package ca.yorku.nemo.slicer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.MethodInvocation;

import ca.yorku.nemo.main.FileUtils;
import ca.yorku.nemo.slicer.CoverageDataLoader.MethodCoverageInfo;
import ca.yorku.nemo.slicer.CoverageDataLoader.CoverageLoadResult;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

public class MayToMustConverter {
    
    private String coverageFile;
    private String processedLogDir;
    private String outputCoverageFile;
    private String conversionLogFile;
    
    List<MethodCoverageInfo> methodCoverageList = new ArrayList<>();
    HashMap<String, ArrayList<MethodCoverageInfo>> fileToMethodsMap = new HashMap<>();
    HashMap<String, List<LogEntry>> fileToLogsMap = new HashMap<>();
    
    // Constructor for MainParser integration
    public MayToMustConverter(String coverageFile, String processedLogDir) {
        this.coverageFile = coverageFile;
        this.processedLogDir = processedLogDir;
        this.outputCoverageFile = coverageFile.replace(".csv", "_sliced.csv");
        this.conversionLogFile = coverageFile.replace(".csv", "_conversion.log");
    }
    
    // Default constructor for standalone use
    public MayToMustConverter() {
        this("/Users/satorukano/repository/research/LogCoCo/output/coverage.csv",
             "/Users/satorukano/repository/research/LogCoCo/output/pre_process_logs/");
    }
    
    public static void main(String[] args) {
        MayToMustConverter converter;
        
        if (args.length >= 2) {
            converter = new MayToMustConverter(args[0], args[1]);
        } else {
            converter = new MayToMustConverter();
        }
        
        converter.performConversion();
    }
    
    // Public method for MainParser to call
    public void performConversion() {
        try {
            loadProcessedLogs();
            loadCoverageData();
            processMethodCoverage();
            outputUpdatedCoverage();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void loadCoverageData() throws Exception {
        CoverageLoadResult result = CoverageDataLoader.loadCoverageData(coverageFile);
        this.methodCoverageList = result.methodCoverageList;
        this.fileToMethodsMap = result.fileToMethodsMap;
    }
    
    // Load processed log files to extract variable values
    private void loadProcessedLogs() throws Exception {
        File logDir = new File(processedLogDir);
        if (!logDir.exists() || !logDir.isDirectory()) {
            System.err.println("Processed log directory not found: " + processedLogDir);
            return;
        }
        
        File[] logFiles = logDir.listFiles((dir, name) -> name.endsWith(".txt"));
        if (logFiles == null) return;
        
        for (File logFile : logFiles) {
            loadLogFile(logFile);
        }
        
        System.out.println("Loaded logs from " + logFiles.length + " files");
    }
    
    private void loadLogFile(File logFile) throws Exception {
        try (BufferedReader br = new BufferedReader(new FileReader(logFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                LogEntry entry = parseLogLine(line);
                if (entry != null) {
                    fileToLogsMap.computeIfAbsent(entry.filePath, k -> new ArrayList<>()).add(entry);
                }
            }
        }
    }
    
    private LogEntry parseLogLine(String line) {
        // Parse format: thread [File.java:lineNumber] /full/path/to/File.java:method timestamp logMessage
        try {
            String[] parts = line.split("\t");
            if (parts.length >= 3) {
                String fileLineInfo = parts[1]; // [File.java:123]
                String fullPath = parts[2].split(":")[0]; // Extract file path
                
                // Extract line number from [File.java:123]
                Pattern pattern = Pattern.compile("\\[(.*?):(\\d+)\\]");
                Matcher matcher = pattern.matcher(fileLineInfo);
                if (matcher.find()) {
                    int lineNumber = Integer.parseInt(matcher.group(2));
                    String logMessage = parts.length > 3 ? parts[parts.length - 1] : "";
                    
                    LogEntry entry = new LogEntry();
                    entry.filePath = fullPath;
                    entry.lineNumber = lineNumber;
                    entry.logMessage = logMessage;
                    // Extract variables by comparing with source code
                    CompilationUnit cu = getResolvedCUFromFilePath(fullPath);
                    entry.variables = LogVariableExtractor.extractVariablesFromLogMessage(fullPath, lineNumber, logMessage, cu);
                    
                    return entry;
                }
            }
        } catch (Exception e) {
            // Skip malformed lines
        }
        return null;
    }
    
    
    static class LogEntry {
        String filePath;
        int lineNumber;
        String logMessage;
        HashMap<String, String> variables;
    }
    
    // Data flow analysis classes
    static class DefUseAnalyzer {
        private Map<String, List<ASTNode>> definitions = new HashMap<>();
        private Map<String, List<ASTNode>> usages = new HashMap<>();
        private CompilationUnit cu;
        
        public DefUseAnalyzer(CompilationUnit cu) {
            this.cu = cu;
        }
        
        public void analyzeMethod(MethodDeclaration method) {
            method.accept(new ASTVisitor() {
                @Override
                public boolean visit(Assignment node) {
                    // Record definitions in assignment statements
                    if (node.getLeftHandSide() instanceof SimpleName) {
                        String varName = ((SimpleName)node.getLeftHandSide()).getIdentifier();
                        definitions.computeIfAbsent(varName, k -> new ArrayList<>()).add(node);
                    }
                    return true;
                }
                
                @Override
                public boolean visit(VariableDeclarationFragment node) {
                    // Record definitions in variable declarations
                    String varName = node.getName().getIdentifier();
                    definitions.computeIfAbsent(varName, k -> new ArrayList<>()).add(node);
                    return true;
                }
                
                @Override
                public boolean visit(SimpleName node) {
                    // Record variable usage
                    if (!isInDeclaration(node)) {
                        usages.computeIfAbsent(node.getIdentifier(), k -> new ArrayList<>()).add(node);
                    }
                    return true;
                }
                
                private boolean isInDeclaration(SimpleName node) {
                    ASTNode parent = node.getParent();
                    return parent instanceof VariableDeclarationFragment ||
                           parent instanceof SingleVariableDeclaration ||
                           (parent instanceof Assignment && ((Assignment)parent).getLeftHandSide() == node);
                }
            });
        }
        
        public List<ASTNode> getDefinitions(String varName) {
            return definitions.getOrDefault(varName, new ArrayList<>());
        }
        
        public List<ASTNode> getUsages(String varName) {
            return usages.getOrDefault(varName, new ArrayList<>());
        }
        
        public int getLineNumber(ASTNode node) {
            return cu.getLineNumber(node.getStartPosition());
        }
    }
    
    static class VariableValueTracker {
        private DefUseAnalyzer defUseAnalyzer;
        private HashMap<String, List<LogEntry>> fileToLogsMap;
        private MethodCoverageInfo methodInfo;
        private CompilationUnit cu;
        private MethodDeclaration currentMethod;
        
        public VariableValueTracker(MayToMustConverter.DefUseAnalyzer analyzer, HashMap<String, List<MayToMustConverter.LogEntry>> logsMap, 
                                  MethodCoverageInfo methodInfo, CompilationUnit cu, MethodDeclaration method) {
            this.defUseAnalyzer = analyzer;
            this.fileToLogsMap = logsMap;
            this.methodInfo = methodInfo;
            this.cu = cu;
            this.currentMethod = method;
        }
        
        public String getVariableValue(String varName, int beforeLine) {
            // Priority: 1. Local assignment, 2. Log output, 3. Method parameter
            
            String localValue = getAssignmentLocal(varName, beforeLine);
            if (localValue != null) return localValue;
            
            String logValue = getAssignmentLog(varName, beforeLine);
            if (logValue != null) return logValue;
            
            return getAssignmentCaller(varName);
        }
        
        private String getAssignmentLocal(String varName, int beforeLine) {
            List<ASTNode> definitions = defUseAnalyzer.getDefinitions(varName);
            
            // Find the closest definition before the given line
            ASTNode closestDef = null;
            int closestLine = -1;
            
            for (ASTNode defNode : definitions) {
                int defLine = defUseAnalyzer.getLineNumber(defNode);
                
                // Only consider executed lines
                if (defLine < beforeLine && isLineExecuted(defLine)) {
                    if (defLine > closestLine) {
                        closestLine = defLine;
                        closestDef = defNode;
                    }
                }
            }
            
            if (closestDef != null) {
                return extractValueFromDefinition(closestDef);
            }
            
            return null;
        }
        
        private String extractValueFromDefinition(ASTNode defNode) {
            if (defNode instanceof Assignment) {
                Assignment assignment = (Assignment) defNode;
                return extractLiteralValue(assignment.getRightHandSide());
            } else if (defNode instanceof VariableDeclarationFragment) {
                VariableDeclarationFragment fragment = (VariableDeclarationFragment) defNode;
                Expression initializer = fragment.getInitializer();
                if (initializer != null) {
                    return extractLiteralValue(initializer);
                }
            }
            return null;
        }
        
        private String extractLiteralValue(Expression expr) {
            if (expr instanceof StringLiteral) {
                return ((StringLiteral) expr).getLiteralValue();
            } else if (expr instanceof NumberLiteral) {
                return ((NumberLiteral) expr).getToken();
            } else if (expr instanceof BooleanLiteral) {
                return String.valueOf(((BooleanLiteral) expr).booleanValue());
            } else if (expr instanceof SimpleName) {
                // Try to resolve constant values
                return resolveConstantValue((SimpleName) expr);
            }
            return null;
        }
        
        private String resolveConstantValue(SimpleName name) {
            // Try to resolve final static constants
            // This is a simplified implementation
            return null;
        }
        
        private String getAssignmentLog(String varName, int beforeLine) {
            // Get variable values from log entries
            List<LogEntry> logs = fileToLogsMap.get(methodInfo.filePath);
            if (logs != null) {
                // Look for logs before the current line that contain the variable
                for (LogEntry log : logs) {
                    // Check if this log is within the method and before the if statement
                    if (log.lineNumber >= methodInfo.startLine && 
                        log.lineNumber <= methodInfo.endLine &&
                        log.lineNumber < beforeLine && 
                        log.variables.containsKey(varName)) {
                        return log.variables.get(varName);
                    }
                }
            }
            return null;
        }
        
        private String getAssignmentCaller(String varName) {
            // Check if it's a method parameter
            List parameters = currentMethod.parameters();
            for (Object param : parameters) {
                if (param instanceof SingleVariableDeclaration) {
                    SingleVariableDeclaration svd = (SingleVariableDeclaration) param;
                    if (svd.getName().getIdentifier().equals(varName)) {
                        // Parameter value tracing would require call graph analysis
                        return null;
                    }
                }
            }
            return null;
        }
        
        private boolean isLineExecuted(int lineNumber) {
            // Check against Must or May lines
            return methodInfo.mustLineNumbers.contains(lineNumber) || 
                   methodInfo.mayLineNumbers.contains(lineNumber);
        }
    }
    
    
    private void processMethodCoverage() throws Exception {
        List<ConversionResult> allConversions = new ArrayList<>();
        
        for (MethodCoverageInfo methodInfo : methodCoverageList) {
            System.out.println("Processing method: " + methodInfo.methodName + " in " + methodInfo.filePath);
            List<ConversionResult> conversions = analyzeMethodForSlicing(methodInfo);
            allConversions.addAll(conversions);
        }
        
        // Write conversion log
        try (PrintWriter logWriter = new PrintWriter(new BufferedWriter(new FileWriter(conversionLogFile)))) {
            for (ConversionResult result : allConversions) {
                logWriter.printf("%s\t%s\t%d\t%s\n", 
                    result.filePath, result.methodName, result.lineNumber, result.reason);
            }
        }
        
        System.out.println("Total conversions: " + allConversions.size());
    }
    
    private List<ConversionResult> analyzeMethodForSlicing(MethodCoverageInfo methodInfo) {
        List<ConversionResult> conversions = new ArrayList<>();
        
        try {
            CompilationUnit cu = getResolvedCUFromFilePath(methodInfo.filePath);
            if (cu == null) {
                System.err.println("Cannot parse file: " + methodInfo.filePath);
                return conversions;
            }
            
            // Find the method declaration
            MethodDeclaration targetMethod = findMethodInCU(cu, methodInfo);
            if (targetMethod == null) {
                System.err.println("Cannot find method: " + methodInfo.methodName + " in " + methodInfo.filePath);
                return conversions;
            }
            
            // Perform data flow analysis first
            DefUseAnalyzer defUseAnalyzer = new DefUseAnalyzer(cu);
            defUseAnalyzer.analyzeMethod(targetMethod);
            
            // Analyze the method with advanced slicing
            AdvancedSlicingVisitor visitor = new AdvancedSlicingVisitor(cu, methodInfo, fileToLogsMap, defUseAnalyzer, targetMethod);
            targetMethod.accept(visitor);
            
            // Update the coverage info based on conversions
            for (Integer convertedLine : visitor.getConvertedLines()) {
                methodInfo.mayLineNumbers.remove(convertedLine);
                methodInfo.mustLineNumbers.add(convertedLine);
                
                ConversionResult result = new ConversionResult();
                result.filePath = methodInfo.filePath;
                result.methodName = methodInfo.methodName;
                result.lineNumber = convertedLine;
                result.reason = visitor.getConversionReason(convertedLine);
                conversions.add(result);
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return conversions;
    }
    
    private MethodDeclaration findMethodInCU(CompilationUnit cu, MethodCoverageInfo methodInfo) {
        final MethodDeclaration[] result = {null};
        
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodDeclaration node) {
                int startLine = cu.getLineNumber(node.getStartPosition());
                int endLine = cu.getLineNumber(node.getStartPosition() + node.getLength());
                
                if (node.getName().toString().equals(methodInfo.methodName) &&
                    startLine == methodInfo.startLine && endLine == methodInfo.endLine) {
                    result[0] = node;
                    return false;
                }
                return true;
            }
        });
        
        return result[0];
    }
    
    private void outputUpdatedCoverage() throws Exception {
        // Read original CSV and update with conversions
        List<String> outputLines = new ArrayList<>();
        
        try (BufferedReader br = new BufferedReader(new FileReader(coverageFile))) {
            String header = br.readLine();
            outputLines.add(header);
            
            String line;
            while ((line = br.readLine()) != null) {
                MethodCoverageInfo info = CoverageDataLoader.parseCoverageCSVLine(line);
                if (info != null) {
                    // Find if this method was updated
                    MethodCoverageInfo updatedInfo = findUpdatedMethod(info);
                    if (updatedInfo != null) {
                        line = updateCSVLine(line, updatedInfo);
                    }
                }
                outputLines.add(line);
            }
        }
        
        // Write updated CSV
        try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(outputCoverageFile)))) {
            for (String outputLine : outputLines) {
                writer.println(outputLine);
            }
        }
        
        System.out.println("Updated coverage written to: " + outputCoverageFile);
    }
    
    private MethodCoverageInfo findUpdatedMethod(MethodCoverageInfo original) {
        for (MethodCoverageInfo updated : methodCoverageList) {
            if (updated.filePath.equals(original.filePath) &&
                updated.methodName.equals(original.methodName) &&
                updated.startLine == original.startLine) {
                return updated;
            }
        }
        return null;
    }
    
    private String updateCSVLine(String originalLine, MethodCoverageInfo updatedInfo) {
        try {
            // Parse CSV line using Apache Commons CSV
            CSVParser parser = CSVFormat.DEFAULT.parse(new java.io.StringReader(originalLine));
            CSVRecord record = parser.iterator().next();
            
            // Convert to array for modification
            String[] fields = new String[record.size()];
            for (int i = 0; i < record.size(); i++) {
                fields[i] = record.get(i);
            }
            
            // Update MustLineNumbers (field 14)
            fields[14] = formatLineNumbers(updatedInfo.mustLineNumbers);
            
            // Update MayLineNumbers (field 15)
            fields[15] = formatLineNumbers(updatedInfo.mayLineNumbers);
            
            // Rebuild CSV line using Apache Commons CSV formatting
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < fields.length; i++) {
                if (i > 0) sb.append(",");
                
                // Quote fields that contain commas or quotes
                if (fields[i].contains(",") || fields[i].contains("\"")) {
                    sb.append("\"").append(fields[i].replace("\"", "\"\"")).append("\"");
                } else {
                    sb.append(fields[i]);
                }
            }
            
            return sb.toString();
        } catch (Exception e) {
            System.err.println("Error updating CSV line: " + originalLine);
            e.printStackTrace();
            return originalLine; // Return original line if parsing fails
        }
    }
    
    private String formatLineNumbers(List<Integer> lineNumbers) {
        if (lineNumbers.isEmpty()) {
            return "None";
        }
        return lineNumbers.stream()
            .sorted()
            .map(String::valueOf)
            .collect(Collectors.joining(";"));
    }
    
    private CompilationUnit getResolvedCUFromFilePath(String filePath) {
        try {
            Map<String, String> options = JavaCore.getOptions();
            options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_17);
            ASTParser astParser = ASTParser.newParser(AST.getJLSLatest());
            astParser.setKind(ASTParser.K_COMPILATION_UNIT);
            String fs = FileUtils.getFileString(filePath);
            astParser.setCompilerOptions(options);
            astParser.setSource(fs.toCharArray());
            astParser.setResolveBindings(true);
            astParser.setBindingsRecovery(true);
            CompilationUnit cu = (CompilationUnit) astParser.createAST(null);
            return cu;
        } catch (Exception e1) {
            e1.printStackTrace();
        }
        return null;
    }
    
    
    static class ConversionResult {
        String filePath;
        String methodName;
        int lineNumber;
        String reason;
    }
}

// Advanced slicing visitor with data flow analysis
class AdvancedSlicingVisitor extends ASTVisitor {
    
    CompilationUnit cu;
    MethodCoverageInfo methodInfo;
    HashMap<String, List<MayToMustConverter.LogEntry>> fileToLogsMap;
    MayToMustConverter.DefUseAnalyzer defUseAnalyzer;
    MayToMustConverter.VariableValueTracker valueTracker;
    MethodDeclaration currentMethod;
    HashSet<Integer> convertedLines;
    HashMap<Integer, String> conversionReasons;
    
    public AdvancedSlicingVisitor(CompilationUnit cu, MethodCoverageInfo methodInfo,
                                HashMap<String, List<MayToMustConverter.LogEntry>> fileToLogsMap,
                                MayToMustConverter.DefUseAnalyzer defUseAnalyzer, MethodDeclaration method) {
        this.cu = cu;
        this.methodInfo = methodInfo;
        this.fileToLogsMap = fileToLogsMap;
        this.defUseAnalyzer = defUseAnalyzer;
        this.currentMethod = method;
        this.valueTracker = new MayToMustConverter.VariableValueTracker(defUseAnalyzer, fileToLogsMap, methodInfo, cu, method);
        this.convertedLines = new HashSet<>();
        this.conversionReasons = new HashMap<>();
    }
    
    public HashSet<Integer> getConvertedLines() {
        return convertedLines;
    }
    
    public String getConversionReason(Integer lineNumber) {
        return conversionReasons.getOrDefault(lineNumber, "Unknown reason");
    }
    
    @Override
    public boolean visit(IfStatement node) {
        int ifLineNumber = cu.getLineNumber(node.getStartPosition());
        
        // Extract variables from condition
        Set<String> conditionVars = extractVariablesFromCondition(node.getExpression());
        
        // Track variable values
        Map<String, String> varValues = new HashMap<>();
        for (String var : conditionVars) {
            String value = valueTracker.getVariableValue(var, ifLineNumber);
            if (value != null) {
                varValues.put(var, value);
            }
        }
        
        // Analyze if statement for slicing
        if (!varValues.isEmpty()) {
            analyzeIfStatementWithKnownValues(node, ifLineNumber, varValues);
        }
        
        return true;
    }
    
    private Set<String> extractVariablesFromCondition(Expression condition) {
        Set<String> variables = new HashSet<>();
        
        condition.accept(new ASTVisitor() {
            @Override
            public boolean visit(SimpleName node) {
                // Skip built-in keywords and "this"
                String identifier = node.getIdentifier();
                if (!isBuiltinOrKeyword(identifier) && !identifier.equals("this")) {
                    variables.add(identifier);
                }
                return true;
            }
            
            @Override
            public boolean visit(FieldAccess node) {
                // Handle field access like obj.field
                variables.add(node.getName().getIdentifier());
                return true;
            }
            
            @Override
            public boolean visit(QualifiedName node) {
                // Handle qualified names like Class.CONSTANT
                variables.add(node.getName().getIdentifier());
                return true;
            }
        });
        
        return variables;
    }
    
    private boolean isBuiltinOrKeyword(String identifier) {
        Set<String> keywords = new HashSet<>(Arrays.asList(
            "true", "false", "null", "void", "class", "interface", "enum",
            "if", "else", "while", "for", "do", "switch", "case", "default",
            "break", "continue", "return", "throw", "try", "catch", "finally"
        ));
        return keywords.contains(identifier);
    }
    
    private void analyzeIfStatementWithKnownValues(IfStatement ifStmt, int ifLineNumber, Map<String, String> knownValues) {
        // Evaluate the condition with known values
        Boolean conditionResult = evaluateCondition(ifStmt.getExpression(), knownValues);
        
        if (conditionResult != null) {
            // Find all statements in the appropriate branch
            Statement targetBranch = conditionResult ? ifStmt.getThenStatement() : ifStmt.getElseStatement();
            
            if (targetBranch != null) {
                // Get all line numbers in the target branch
                List<Integer> branchLines = getAllLinesInStatement(targetBranch);
                
                for (Integer line : branchLines) {
                    if (methodInfo.mayLineNumbers.contains(line)) {
                        String reason = String.format(
                            "If condition at line %d evaluates to %s with values extracted from logs: %s",
                            ifLineNumber, conditionResult, knownValues
                        );
                        convertedLines.add(line);
                        conversionReasons.put(line, reason);
                    }
                }
            }
        }
    }
    
    private List<Integer> getAllLinesInStatement(Statement stmt) {
        List<Integer> lines = new ArrayList<>();
        
        // Get the start and end positions of the statement
        int startPos = stmt.getStartPosition();
        int endPos = startPos + stmt.getLength();
        
        // Convert positions to line numbers
        int startLine = cu.getLineNumber(startPos);
        int endLine = cu.getLineNumber(endPos);
        
        // Add all lines in the range
        for (int line = startLine; line <= endLine; line++) {
            lines.add(line);
        }
        
        return lines;
    }
    
    private List<Integer> findLogsInStatement(Statement stmt) {
        List<Integer> logLines = new ArrayList<>();
        
        stmt.accept(new ASTVisitor() {
            @Override
            public boolean visit(ExpressionStatement node) {
                if (FileUtils.ifLogPrinting(node.toString())) {
                    int lineNumber = cu.getLineNumber(node.getStartPosition());
                    logLines.add(lineNumber);
                }
                return true;
            }
        });
        
        return logLines;
    }
    
    private Boolean evaluateCondition(Expression condition, Map<String, String> knownValues) {
        if (condition instanceof InfixExpression) {
            InfixExpression infixExpr = (InfixExpression) condition;
            return evaluateInfixExpression(infixExpr, knownValues);
        }
        // TODO: Handle other types of conditions (method calls, boolean variables, etc.)
        return null;
    }
    
    private Boolean evaluateInfixExpression(InfixExpression expr, Map<String, String> knownValues) {
        Expression left = expr.getLeftOperand();
        Expression right = expr.getRightOperand();
        InfixExpression.Operator operator = expr.getOperator();
        
        String leftValue = getExpressionValue(left, knownValues);
        String rightValue = getExpressionValue(right, knownValues);
        
        if (leftValue != null && rightValue != null) {
            // Evaluate based on operator
            if (operator == InfixExpression.Operator.EQUALS) {
                return leftValue.equals(rightValue);
            } else if (operator == InfixExpression.Operator.NOT_EQUALS) {
                return !leftValue.equals(rightValue);
            } else if (operator == InfixExpression.Operator.LESS) {
                try {
                    return Integer.parseInt(leftValue) < Integer.parseInt(rightValue);
                } catch (NumberFormatException e) {
                    return null;
                }
            } else if (operator == InfixExpression.Operator.GREATER) {
                try {
                    return Integer.parseInt(leftValue) > Integer.parseInt(rightValue);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            // TODO: Add more operators
        }
        
        return null;
    }
    
    private String getExpressionValue(Expression expr, Map<String, String> knownValues) {
        if (expr instanceof SimpleName) {
            String varName = ((SimpleName) expr).getIdentifier();
            return knownValues.get(varName);
        } else if (expr instanceof StringLiteral) {
            return ((StringLiteral) expr).getLiteralValue();
        } else if (expr instanceof NumberLiteral) {
            return ((NumberLiteral) expr).getToken();
        } else if (expr instanceof BooleanLiteral) {
            return String.valueOf(((BooleanLiteral) expr).booleanValue());
        } else if (expr instanceof MethodInvocation) {
            // Handle simple method calls like String.equals()
            MethodInvocation methodCall = (MethodInvocation) expr;
            if (methodCall.getName().getIdentifier().equals("equals") && 
                methodCall.arguments().size() == 1) {
                Expression arg = (Expression) methodCall.arguments().get(0);
                String argValue = getExpressionValue(arg, knownValues);
                Expression target = methodCall.getExpression();
                if (target != null) {
                    String targetValue = getExpressionValue(target, knownValues);
                    if (targetValue != null && argValue != null) {
                        return String.valueOf(targetValue.equals(argValue));
                    }
                }
            }
        }
        return null;
    }
}

