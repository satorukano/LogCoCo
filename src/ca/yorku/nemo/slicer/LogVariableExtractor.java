package ca.yorku.nemo.slicer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ExpressionStatement;

import ca.yorku.nemo.main.FileUtils;

public class LogVariableExtractor {
    
    public static HashMap<String, String> extractVariablesFromLogMessage(String filePath, int lineNumber, 
                                                                         String actualLogMessage, CompilationUnit cu) {
        HashMap<String, String> variables = new HashMap<>();
        
        try {
            if (cu == null) {
                return variables;
            }
            
            // Find the logging statement at the specified line
            String loggingStatement = findLoggingStatementAtLine(cu, lineNumber);
            if (loggingStatement == null) {
                return variables;
            }
            
            // Extract variables based on the pattern used
            if (loggingStatement.contains(" + ")) {
                // Handle string concatenation pattern
                return extractVariablesFromConcatenation(loggingStatement, actualLogMessage);
            } else if (loggingStatement.contains("String.format")) {
                // Handle String.format pattern
                return extractVariablesFromStringFormat(loggingStatement, actualLogMessage);
            } else if (loggingStatement.contains("{}")) {
                // Handle SLF4J placeholder pattern
                return extractVariablesFromSlf4jPlaceholders(loggingStatement, actualLogMessage);
            } else if (loggingStatement.contains("%")) {
                // Handle printf format pattern
                return extractVariablesFromPrintfFormat(loggingStatement, actualLogMessage);
            }
            
        } catch (Exception e) {
            // Silently fail for malformed code
        }
        
        return variables;
    }
    
    private static String findLoggingStatementAtLine(CompilationUnit cu, int targetLineNumber) {
        final String[] result = {null};
        
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(ExpressionStatement node) {
                int lineNumber = cu.getLineNumber(node.getStartPosition());
                if (lineNumber == targetLineNumber) {
                    String statement = node.toString().trim();
                    // Check if this is a logging statement
                    if (FileUtils.ifLogPrinting(statement)) {
                        result[0] = statement;
                        return false;
                    }
                }
                return true;
            }
        });
        
        return result[0];
    }
    
    private static HashMap<String, String> extractVariablesFromConcatenation(String loggingStatement, String actualMessage) {
        HashMap<String, String> variables = new HashMap<>();
        
        try {
            // Parse the logging statement to extract the concatenation pattern
            // Example: System.out.println("Message: " + variable + " more text " + anotherVar);
            
            // Extract the method call and its argument
            Pattern methodPattern = Pattern.compile("(\\w+\\.)*\\w+\\((.+)\\)");
            Matcher methodMatcher = methodPattern.matcher(loggingStatement);
            
            if (methodMatcher.find()) {
                String argument = methodMatcher.group(2).trim();
                
                // Parse the concatenation expression
                List<String> parts = parseConcatenationExpression(argument);
                
                if (parts.size() > 1) {
                    // Reconstruct the template and extract variables
                    return matchConcatenationParts(parts, actualMessage);
                }
            }
        } catch (Exception e) {
            // Ignore parsing errors
        }
        
        return variables;
    }
    
    private static List<String> parseConcatenationExpression(String expression) {
        List<String> parts = new ArrayList<>();
        StringBuilder currentPart = new StringBuilder();
        boolean inQuotes = false;
        int parenthesesLevel = 0;
        
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            
            if (c == '"' && (i == 0 || expression.charAt(i-1) != '\\')) {
                inQuotes = !inQuotes;
                currentPart.append(c);
            } else if (c == '(' && !inQuotes) {
                parenthesesLevel++;
                currentPart.append(c);
            } else if (c == ')' && !inQuotes) {
                parenthesesLevel--;
                currentPart.append(c);
            } else if (c == '+' && !inQuotes && parenthesesLevel == 0) {
                // This is a concatenation operator
                String part = currentPart.toString().trim();
                if (!part.isEmpty()) {
                    parts.add(part);
                }
                currentPart = new StringBuilder();
            } else {
                currentPart.append(c);
            }
        }
        
        // Add the last part
        String lastPart = currentPart.toString().trim();
        if (!lastPart.isEmpty()) {
            parts.add(lastPart);
        }
        
        return parts;
    }
    
    private static HashMap<String, String> matchConcatenationParts(List<String> parts, String actualMessage) {
        HashMap<String, String> variables = new HashMap<>();
        
        try {
            StringBuilder templateBuilder = new StringBuilder();
            List<String> variableNames = new ArrayList<>();
            
            // Build template and collect variable names
            for (String part : parts) {
                if (isStringLiteral(part)) {
                    // String literal - add to template
                    String literal = part.substring(1, part.length() - 1); // Remove quotes
                    templateBuilder.append(literal);
                } else {
                    // Variable - add placeholder and record name
                    templateBuilder.append("{{VAR}}");
                    variableNames.add(part.trim());
                }
            }
            
            String template = templateBuilder.toString();
            
            // Extract variable values using the template
            String[] templateParts = template.split("\\{\\{VAR\\}\\}");
            
            if (templateParts.length == variableNames.size() + 1) {
                String remaining = actualMessage;
                
                for (int i = 0; i < variableNames.size(); i++) {
                    String prefix = templateParts[i];
                    String suffix = (i + 1 < templateParts.length) ? templateParts[i + 1] : "";
                    
                    // Remove prefix
                    if (!prefix.isEmpty() && remaining.startsWith(prefix)) {
                        remaining = remaining.substring(prefix.length());
                    }
                    
                    // Find variable value
                    String variableValue;
                    if (!suffix.isEmpty()) {
                        int suffixIndex = remaining.indexOf(suffix);
                        if (suffixIndex != -1) {
                            variableValue = remaining.substring(0, suffixIndex);
                            remaining = remaining.substring(suffixIndex);
                        } else {
                            continue; // Skip this variable
                        }
                    } else {
                        // Last variable - take everything remaining
                        variableValue = remaining;
                        remaining = "";
                    }
                    
                    variables.put(variableNames.get(i), variableValue);
                }
            }
        } catch (Exception e) {
            // Ignore parsing errors
        }
        
        return variables;
    }
    
    private static boolean isStringLiteral(String part) {
        return part.startsWith("\"") && part.endsWith("\"") && part.length() >= 2;
    }
    
    private static HashMap<String, String> extractVariablesFromStringFormat(String loggingStatement, String actualMessage) {
        HashMap<String, String> variables = new HashMap<>();
        
        try {
            // Parse String.format pattern
            // Example: String.format("Message: %s", variable)
            Pattern formatPattern = Pattern.compile("String\\.format\\s*\\(\\s*\"([^\"]*)\",\\s*(.+)\\)");
            Matcher matcher = formatPattern.matcher(loggingStatement);
            
            if (matcher.find()) {
                String formatString = matcher.group(1);
                String argumentsString = matcher.group(2);
                
                // Parse format specifiers
                List<String> formatSpecifiers = new ArrayList<>();
                Pattern specifierPattern = Pattern.compile("%[sd]");
                Matcher specifierMatcher = specifierPattern.matcher(formatString);
                while (specifierMatcher.find()) {
                    formatSpecifiers.add(specifierMatcher.group());
                }
                
                // Parse arguments
                List<String> arguments = parseArguments(argumentsString);
                
                if (formatSpecifiers.size() == arguments.size()) {
                    // Build template and extract values
                    String template = formatString;
                    for (String specifier : formatSpecifiers) {
                        template = template.replaceFirst(Pattern.quote(specifier), "{{VAR}}");
                    }
                    
                    return extractValuesFromTemplate(template, actualMessage, arguments);
                }
            }
        } catch (Exception e) {
            // Ignore parsing errors
        }
        
        return variables;
    }
    
    private static HashMap<String, String> extractVariablesFromSlf4jPlaceholders(String loggingStatement, String actualMessage) {
        HashMap<String, String> variables = new HashMap<>();
        
        try {
            // Parse SLF4J pattern
            // Example: logger.info("Message: {}", variable)
            Pattern slf4jPattern = Pattern.compile("\\w+\\(\"([^\"]*)\",\\s*(.+)\\)");
            Matcher matcher = slf4jPattern.matcher(loggingStatement);
            
            if (matcher.find()) {
                String messageTemplate = matcher.group(1);
                String argumentsString = matcher.group(2);
                
                // Count placeholders
                int placeholderCount = (messageTemplate.split("\\{\\}", -1).length - 1);
                
                // Parse arguments
                List<String> arguments = parseArguments(argumentsString);
                
                if (placeholderCount == arguments.size()) {
                    String template = messageTemplate.replace("{}", "{{VAR}}");
                    return extractValuesFromTemplate(template, actualMessage, arguments);
                }
            }
        } catch (Exception e) {
            // Ignore parsing errors
        }
        
        return variables;
    }
    
    private static HashMap<String, String> extractVariablesFromPrintfFormat(String loggingStatement, String actualMessage) {
        HashMap<String, String> variables = new HashMap<>();
        
        try {
            // Parse printf pattern
            // Example: System.out.printf("Message: %s", variable)
            Pattern printfPattern = Pattern.compile("printf\\s*\\(\\s*\"([^\"]*)\",\\s*(.+)\\)");
            Matcher matcher = printfPattern.matcher(loggingStatement);
            
            if (matcher.find()) {
                String formatString = matcher.group(1);
                String argumentsString = matcher.group(2);
                
                // Parse format specifiers
                List<String> formatSpecifiers = new ArrayList<>();
                Pattern specifierPattern = Pattern.compile("%[dsfbn]");
                Matcher specifierMatcher = specifierPattern.matcher(formatString);
                while (specifierMatcher.find()) {
                    formatSpecifiers.add(specifierMatcher.group());
                }
                
                // Parse arguments
                List<String> arguments = parseArguments(argumentsString);
                
                if (formatSpecifiers.size() == arguments.size()) {
                    String template = formatString;
                    for (String specifier : formatSpecifiers) {
                        template = template.replaceFirst(Pattern.quote(specifier), "{{VAR}}");
                    }
                    
                    return extractValuesFromTemplate(template, actualMessage, arguments);
                }
            }
        } catch (Exception e) {
            // Ignore parsing errors
        }
        
        return variables;
    }
    
    private static List<String> parseArguments(String argumentsString) {
        List<String> arguments = new ArrayList<>();
        StringBuilder currentArg = new StringBuilder();
        int parenthesesLevel = 0;
        boolean inQuotes = false;
        
        for (int i = 0; i < argumentsString.length(); i++) {
            char c = argumentsString.charAt(i);
            
            if (c == '"' && (i == 0 || argumentsString.charAt(i-1) != '\\')) {
                inQuotes = !inQuotes;
                currentArg.append(c);
            } else if (c == '(' && !inQuotes) {
                parenthesesLevel++;
                currentArg.append(c);
            } else if (c == ')' && !inQuotes) {
                parenthesesLevel--;
                currentArg.append(c);
            } else if (c == ',' && !inQuotes && parenthesesLevel == 0) {
                String arg = currentArg.toString().trim();
                if (!arg.isEmpty()) {
                    arguments.add(arg);
                }
                currentArg = new StringBuilder();
            } else {
                currentArg.append(c);
            }
        }
        
        String lastArg = currentArg.toString().trim();
        if (!lastArg.isEmpty()) {
            arguments.add(lastArg);
        }
        
        return arguments;
    }
    
    private static HashMap<String, String> extractValuesFromTemplate(String template, String actualMessage, List<String> variableNames) {
        HashMap<String, String> variables = new HashMap<>();
        
        try {
            String[] templateParts = template.split("\\{\\{VAR\\}\\}");
            
            if (templateParts.length == variableNames.size() + 1) {
                String remaining = actualMessage;
                
                for (int i = 0; i < variableNames.size(); i++) {
                    String prefix = templateParts[i];
                    String suffix = (i + 1 < templateParts.length) ? templateParts[i + 1] : "";
                    
                    // Remove prefix
                    if (!prefix.isEmpty() && remaining.startsWith(prefix)) {
                        remaining = remaining.substring(prefix.length());
                    }
                    
                    // Find variable value
                    String variableValue;
                    if (!suffix.isEmpty()) {
                        int suffixIndex = remaining.indexOf(suffix);
                        if (suffixIndex != -1) {
                            variableValue = remaining.substring(0, suffixIndex);
                            remaining = remaining.substring(suffixIndex);
                        } else {
                            continue;
                        }
                    } else {
                        variableValue = remaining;
                        remaining = "";
                    }
                    
                    variables.put(variableNames.get(i), variableValue);
                }
            }
        } catch (Exception e) {
            // Ignore parsing errors
        }
        
        return variables;
    }
}