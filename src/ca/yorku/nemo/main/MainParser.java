package ca.yorku.nemo.main;

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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BreakStatement;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.SynchronizedStatement;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.jgrapht.alg.cycle.CycleDetector;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.moeaframework.Executor;
import org.moeaframework.algorithm.NSGAII;
import org.moeaframework.core.Algorithm;
import org.moeaframework.core.Initialization;
import org.moeaframework.core.NondominatedPopulation;
import org.moeaframework.core.NondominatedSortingPopulation;
import org.moeaframework.core.Problem;
import org.moeaframework.core.Solution;
import org.moeaframework.core.Variation;
import org.moeaframework.core.comparator.ChainedComparator;
import org.moeaframework.core.comparator.CrowdingComparator;
import org.moeaframework.core.comparator.DominanceComparator;
import org.moeaframework.core.comparator.ParetoDominanceComparator;
import org.moeaframework.core.operator.GAVariation;
import org.moeaframework.core.operator.InjectedInitialization;
import org.moeaframework.core.operator.RandomInitialization;
import org.moeaframework.core.operator.TournamentSelection;
import org.moeaframework.core.operator.binary.BitFlip;
import org.moeaframework.core.operator.binary.HUX;
import org.moeaframework.core.operator.real.PM;
import org.moeaframework.core.operator.real.SBX;
import org.moeaframework.core.variable.BinaryVariable;
import org.moeaframework.core.variable.EncodingUtils;
import org.w3c.dom.Element;

import com.google.common.base.Joiner;
import com.google.common.collect.Sets;

public class MainParser {
	
	static String projectRootPath = "/Users/satorukano/repository/research/TraceCollector/repos/main/zookeeper";
	static String qualifyClassNameAndFileInfoPath = "output/outputClass.txt";
	static String processedLogDir = "output/pre_process_logs/";
//	static String riskyFileInfoPath = "risky_file_sample_paths.txt";
	static String entryMethodList = "output/outputContainLogMethodList.txt";
	static String jreLibPath = "/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home/jre/lib/rt.jar";
	static String oracle_coverage_data = "input/ex.xml";
	static String output_coverage_matrix = "output/coverage.csv";
	static String logAddCountDistributionPath = "logAddPointCount.txt"; 
	static String comparisonTwoObjFunction = "comparisonOfTwoObjFunctionResults.txt";
	
	static LinkedHashSet<String> allRiskyFiles = new LinkedHashSet<>(); 
	static LinkedHashSet<String> allFiles = new LinkedHashSet<>(); 
	static HashMap<String, String> qualifyClassNameFilePathMap = new HashMap<>();
	
	
//	static String testLogSequenceFile = "test_worker_fp-charging_only_id.log";

	
	static HashMap<MethodNodeForOutput, HashMap<ASTNode, String>> methodGlobalMarkMap = new HashMap<>();
	static HashMap<MethodNodeForOutput, HashMap<String, HashMap<ASTNode, String>>> methodAndAllLocalMaps = new HashMap<>();
	
//	static HashMap<MethodNodeForOutput, HashMap<ASTNode, String>> directOutputLoggingMethodGlobalMarkMap = new HashMap<>();
	
	/**
	 * type one method means that the method contains logging code and at least one logging code
	 * was printed when matching with generated log files
	 */
	static LinkedHashSet<MethodNodeForOutput> typeOneMethodList = new LinkedHashSet<>();
	 
	
	static int totalMethods = 0;
	static int totalMethodWithLog = 0;
	static int totalMethodWithInternalCall = 0;
	
	static Logger logger = LogManager.getLogger();
	
	static String invokeHeuritics ="org.apache.zookeeper";
	
	public static void main(String[] args) {
		// Record start time and initial memory usage
		if (args.length > 0) {
			jreLibPath = args[0];
		}
		long startTime = System.currentTimeMillis();
		Runtime runtime = Runtime.getRuntime();
		runtime.gc(); // Run garbage collection for more accurate initial measurement
		long initialMemory = runtime.totalMemory() - runtime.freeMemory();
		System.out.println("=== Memory Usage Report ===");
		System.out.printf("Initial memory usage: %.2f MB\n", initialMemory / (1024.0 * 1024.0));

		try (BufferedReader br = new BufferedReader(new FileReader(qualifyClassNameAndFileInfoPath))) {
			String line = null;
			while((line = br.readLine()) != null) {
				String[] results = line.split(",");
				qualifyClassNameFilePathMap.put(results[0], results[1]);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		try {
			LogRegexMatcher.initWithLogStr(new File(processedLogDir));
			System.out.println(LogRegexMatcher.rawLogList.size());
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		
//		try (BufferedReader br = new BufferedReader(new FileReader(riskyFileInfoPath))) {
//			String line = null;
//			while((line = br.readLine()) != null) {
//				allRiskyFiles.add(line);
//			}
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
		
		try (BufferedReader br = new BufferedReader(new FileReader(entryMethodList))) {
			String line = null;
			while((line = br.readLine()) != null) {
				String filePath = line.split("\\t")[0];
				allFiles.add(filePath);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		// Memory check before processing files
		long beforeProcessingMemory = runtime.totalMemory() - runtime.freeMemory();
		System.out.printf("Memory before processing files: %.2f MB\n", beforeProcessingMemory / (1024.0 * 1024.0));
		System.out.printf("Number of files to process: %d\n\n", allFiles.size());
		
		int fileCount = 0;
		for (String filePath : allFiles) {
			fileCount++;
			logger.info("Processing File {} ({}/{})", filePath, fileCount, allFiles.size());
			
			// Memory check every 10 files
			if (fileCount % 10 == 0) {
				long currentMemory = runtime.totalMemory() - runtime.freeMemory();
				System.out.printf("Memory after %d files: %.2f MB\n", fileCount, currentMemory / (1024.0 * 1024.0));
			}
			if (filePath.contains("zookeeper-server")) {
				if(filePath.contains("zookeeper/ZKSplitLog.java")){
//					|| filePath.contains("ipc/RpcServer.java")) {
					continue;
				}
				parse(filePath);
			}
		}
		
//		parse("/Users/nemo/hbase-1.2.6/hbase-client/src/main/java/org/apache/hadoop/hbase/client/AsyncProcess.java");
		
//		try {
//			traverse(new File(projectRootPath));
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//		
//		for (int i = 0; i < allFiles.size(); i ++) {
//			String filePath = allFiles.get(i);
//			System.out.println(filePath);
//			findLogGenerateMethods(filePath);
//		}
//		
//		System.out.println(totalMethods);
//		System.out.println(totalMethodWithLog);
//		System.out.println(totalMethodWithInternalCall);
//		
//		

		
//		for(MethodNodeForOutput methodNodeForOutput : methodGlobalMarkMap.keySet()) {
//			if (methodNodeForOutput.toString().contains("charge")) {
//				LogAdditionFinder finder = new LogAdditionFinder( methodGlobalMarkMap.get(methodNodeForOutput), methodNodeForOutput);
//				finder.provideSuggestion();
//			}
//		}
		
//		CoverageData coverageData = new CoverageData(oracle_coverage_data);
//		outputMethodCoverage();

		// Memory usage before outputEstimatedCoverageOnly
		long beforeOutputMemory = runtime.totalMemory() - runtime.freeMemory();
		System.out.printf("Memory usage before output: %.2f MB\n", beforeOutputMemory / (1024.0 * 1024.0));
		
		outputEstimatedCoverageOnly(output_coverage_matrix);
		
		// Memory usage after outputEstimatedCoverageOnly
		long afterOutputMemory = runtime.totalMemory() - runtime.freeMemory();
		System.out.printf("Memory usage after output: %.2f MB\n", afterOutputMemory / (1024.0 * 1024.0));

		
//		ArrayList<Integer> logAddCountList = new ArrayList<>();
		
		System.out.printf("There are %d type one method(s)\n", typeOneMethodList.size());
		
		/* The following is to solve log add problem
		int coverageGeneratedMethodCount = 0;
		int coverageWithoutMustNodeCount = 0;
		int coverageNeedSuggestionCount = 0;
		
		for (MethodNodeForOutput methodNodeForOutput : methodGlobalMarkMap.keySet()) {
			boolean coveredMethod = methodGlobalMarkMap.get(methodNodeForOutput).values().contains("Must");
			// if allFiles.contains(methodNodeForOutput.filePath)
			// i.e if the method is at log entry file
			if (coveredMethod) {
				coverageGeneratedMethodCount ++;
			} else {
				continue;
			}
			
			LoggingAddEvaluateByLineProblem problemLineFirst = new LoggingAddEvaluateByLineProblem(
					methodGlobalMarkMap.get(methodNodeForOutput), methodNodeForOutput);
			
			if (problemLineFirst.branchSize >= 20) {
				continue;
			}
			
			if (problemLineFirst.possibleAddingPointCount > 0) {
				logAddCountList.add(problemLineFirst.possibleAddingPointCount);
				coverageNeedSuggestionCount ++;
			} else if (problemLineFirst.possibleAddingPointCount == 0) {
				coverageWithoutMustNodeCount ++;
				continue;
			}
		
			
			
			
			LoggingAddProblem problemBranchFirst = new LoggingAddProblem(
					methodGlobalMarkMap.get(methodNodeForOutput), methodNodeForOutput);
			System.out.println("--------------------- SearchBased evaluation ---------------------");
			try {
				System.out.println(methodNodeForOutput);
				
				if (methodNodeForOutput.toString().contains("from:32"))
					System.out.println("debug");
				
				for (ASTNode addLogPoint : problemBranchFirst.possibleLogAddPointList) {
					System.out.printf("Log add point: %s:%d\n",addLogPoint.getClass().getSimpleName(),
							problemBranchFirst.mdNodeInfo.cu.getLineNumber(addLogPoint.getStartPosition()));
				}
				List<Solution> lineFirstSolutions = getOptimalSolutionThroughGA(problemLineFirst);
				List<Solution> branchFirstSolutions = getOptimalSolutionThroughGA(problemBranchFirst);
				
				for (Solution solution : lineFirstSolutions) {
					System.out.println("Line-first optimal solution: " + solution.getVariable(0).toString());
				}
				for (Solution solution : branchFirstSolutions) {
					System.out.println("Branch-first LogSug solution: " + solution.getVariable(0).toString());
				}
				
//				ArrayList<String> lineFirstSolutionStrList = new ArrayList<>();
				int improveMaxMayLine = 0;
				int improveMaxBranchLine = 0;
				int maxAddLogLine = 0;
				for (Solution solution : lineFirstSolutions) {
					int addLogCount = (int) solution.getObjective(1);
					int improveMayLine = (int)(-solution.getObjective(0));
					int improveMayBranch = problemBranchFirst.getMayBranchNumber();
					maxAddLogLine = problemBranchFirst.possibleLogAddPointList.size();
					improveMaxMayLine = improveMayLine;
					improveMaxBranchLine = improveMayBranch;
					System.out.printf("%d,%d,%d;",improveMayLine,improveMayBranch,addLogCount);
				}
				System.out.printf("|||||");
				System.out.printf("%d,%d,%d;",improveMaxMayLine,improveMaxBranchLine,maxAddLogLine);
				System.out.printf("|||||");
				
				for (Solution solution : branchFirstSolutions) {
					int addLogCount = StringUtils.countMatches(solution.getVariable(0).toString(), "1");
					int improveMayBranch =  (int)(-solution.getObjective(0) * addLogCount);
					int improveMayLine = (int)(-solution.getObjective(1) * addLogCount);
					System.out.printf("%d,%d,%d;",improveMayLine,improveMayBranch,addLogCount);
				}
				
				System.out.printf("|||||%d,%d,%d,%d", problemBranchFirst.getTotalLineNumber(), 
						problemBranchFirst.getTotalMayLineNumber(),
						problemBranchFirst.getTotalBranchNumber(),
						problemBranchFirst.getMayBranchNumber());
				
				System.out.println();
				
				
//				try(PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(comparisonTwoObjFunction, true)))) {
//					writer.println(processedLogDir);
//					for (Integer logAddCount : logAddCountList) {
//						writer.printf("%d\n",logAddCount);
//					}
//				} catch (Exception e) {
//					e.printStackTrace();
//				}

			} catch (Exception e) {
				System.err.println(methodNodeForOutput);
				e.printStackTrace();
			}
		}
		
		System.out.println("Methods with coverage generated: " + coverageGeneratedMethodCount);
		System.out.println("Methods without must node: " + coverageWithoutMustNodeCount);
		System.out.println("Methods need suggestion: " + coverageNeedSuggestionCount);
		
		
		try(PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(logAddCountDistributionPath, true)))) {
			writer.println(processedLogDir);
			for (Integer logAddCount : logAddCountList) {
				writer.printf("%d\n",logAddCount);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		*/
		
		// Final memory usage report
		long finalMemory = runtime.totalMemory() - runtime.freeMemory();
		long totalTime = System.currentTimeMillis() - startTime;
		
		System.out.println("\n=== Final Memory Usage Summary ===");
		System.out.printf("Initial memory: %.2f MB\n", initialMemory / (1024.0 * 1024.0));
		System.out.printf("Final memory: %.2f MB\n", finalMemory / (1024.0 * 1024.0));
		System.out.printf("Memory used: %.2f MB\n", (finalMemory - initialMemory) / (1024.0 * 1024.0));
		System.out.printf("Max memory available: %.2f MB\n", runtime.maxMemory() / (1024.0 * 1024.0));
		System.out.printf("Total execution time: %.2f seconds\n", totalTime / 1000.0);
		
		System.exit(0); 
	}
	
	
	public static List<Solution> getOptimalSolutionThroughGA(LoggingAddProblem problem) {
		
		ArrayList<Solution> solList = new ArrayList<>();
		
		int initPopulationSize = (int) Math.min(100, Math.pow(2, problem.possibleAddingPointCount));
		
//		if (problem.possibleAddingPointCount > 4)
//			System.out.println("debug");
		
//		Initialization initialization = new InjectedInitialization(problem, initPopulationSize, initSolutions);
        
		Initialization initialization = new CustomizedInitilization(problem, initPopulationSize, problem.possibleAddingPointCount);
		
		TournamentSelection selection = new TournamentSelection(2, new CustomizedLogSugComparator());
        Variation variation = new GAVariation(
                new HUX(1), new BitFlip(0.25));
		
        CustomizedNSGAII algorithm = new CustomizedNSGAII(
                problem,
                new NondominatedSortingPopulation(new CustomizedLogSugComparator()),
                null, // no archive
                selection,
                variation,
                initialization);
        
        CustomizedTermination termination = new CustomizedTermination(new CustomizedLogSugComparator());
        termination.initialize(algorithm);
        
        int evaluateTime = 0;
        while (!algorithm.isTerminated() && !termination.shouldTerminate(algorithm)) {
        	algorithm.step();
        	
        	NondominatedPopulation curResult = algorithm.getResult();
//            System.out.println(curResult.size());
        	evaluateTime++;
        }
        
        System.out.println("Evaluate_time: " + evaluateTime);

        NondominatedPopulation result = algorithm.getResult();
        
//		NondominatedPopulation result = new Executor()
//		.withProblemClass(LoggingAddProblem.class,
//				methodGlobalMarkMap.get(methodNodeForOutput), methodNodeForOutput)
//		.withAlgorithm("NSGAII")
//		.withMaxEvaluations(10000)
//		.run();
		
		for (int i = 0; i < result.size(); i ++) {
			Solution solution = result.get(i);
			solList.add(solution);
//			double[] objectives = solution.getObjectives();
//			System.out.println("SOLUTION " + (i+1) + ":");
//			System.out.println("	Binary String " + solution.getVariable(0));
//			System.out.println("	Improve branch coverage: " + -objectives[0]); // +"," +solution.getObjective(1));
//			System.out.println("	Improve line coverage: " + -objectives[1]);
		}
		
		return solList;
	}
	
	public static List<Solution> getTwoInitSolution(LoggingAddProblem problem) {
		ArrayList<Solution> results = new ArrayList<>();
		
		Solution solution = new Solution(1, 2);
		
		BinaryVariable v1 = new BinaryVariable(problem.possibleAddingPointCount);
		
		for(int i = 0; i < v1.getNumberOfBits(); i ++) {
			v1.set(i, true);
		}
		
		solution.setVariable(0, v1);
		results.add(solution);
		
		solution = new Solution(1, 2);
		
		BinaryVariable v2 = new BinaryVariable(problem.possibleAddingPointCount);
		for(int i = 0; i < v2.getNumberOfBits(); i ++) {
			v2.set(i, false);
		}
		solution.setVariable(0, v2);
		results.add(solution);
		
		return results;
	}
	
	public static void outputMethodCoverage() {
		
		int totalCovMethodByLogSug =0;
		int covBothMethodCanCount = 0;
//		int totalCovByOralce = coverageData.getTotalMethodNumber();
		for(MethodNodeForOutput procMd : methodGlobalMarkMap.keySet()) {
			System.out.println("--------------------------------------");
			System.out.println(procMd);
			TreeMap<Integer, String> lineCoverageMap = new TreeMap<>();
			HashMap<ASTNode, String> markMap = methodGlobalMarkMap.get(procMd);
			
			int totalBranch = 0;
			int totalMayBranch = 0;
			
			TreeMap<Integer, String> branchRepresentativeLineCovMap = new TreeMap<>();
			
			for (ASTNode node : markMap.keySet()) {
				if ((node instanceof Block || node instanceof SwitchCase)
						&& !((node.getParent() instanceof TryStatement) ||
								(node.getParent() instanceof SynchronizedStatement) ||
								(node.getParent() instanceof MethodDeclaration))){
					if (node instanceof SwitchCase) {
						int lineNumebr = procMd.cu.getLineNumber(node.getStartPosition());
						branchRepresentativeLineCovMap.put(lineNumebr, markMap.get(node));
					}
					if (node instanceof Block) {
						List stmtList = ((Block) node).statements();
						if (!stmtList.isEmpty()) {
							Statement firstStmtInBlock = (Statement) stmtList.get(0);
							int lineNumebr = procMd.cu.getLineNumber(firstStmtInBlock.getStartPosition());
							branchRepresentativeLineCovMap.put(lineNumebr, markMap.get(firstStmtInBlock));
						}
					}
				}
			}
			
			lineCoverageMap = calculateCoverageMap(markMap, procMd);
			boolean coveredMethod = lineCoverageMap.values().contains("Must");
			
			if (coveredMethod) {
				totalCovMethodByLogSug ++;
				System.out.println("----------Line coverage-----------");
				for(Integer lineNumber : lineCoverageMap.keySet()) {
					System.out.printf("%d,%s\n", lineNumber, lineCoverageMap.get(lineNumber));
				}
				System.out.println("----------Branch coverage-----------");
				for(Integer lineNumber : branchRepresentativeLineCovMap.keySet()) {
					System.out.printf("%d,%s\n", lineNumber, branchRepresentativeLineCovMap.get(lineNumber));
				}
			}
		
			
			if (coveredMethod) {
				int mdStartLine = procMd.cu.getLineNumber(procMd.md.getStartPosition());
				int mdEndLine = procMd.cu.getLineNumber(procMd.md.getStartPosition()+procMd.md.getLength() -1);
//				Element covMethodElem = coverageData.getMethodLineCoverageMap(procMd.filePath, procMd.md.getName().toString(), mdStartLine, mdEndLine);
//				if (covMethodElem==null) {
					System.out.println("Can't find method elem in covxml!! " + procMd);
//				}
					covBothMethodCanCount ++;
//					coverageData.compareCoverage(covMethodElem, lineCoverageMap, branchRepresentativeLineCovMap,
//							procMd, output_coverage_matrix);


			}
		}
		System.out.printf(" LogSug coverage covers methods: %d; Common coverage covers methods: %d\n",
				 totalCovMethodByLogSug, covBothMethodCanCount);
	}

	
//	public static void findLogGenerateMethods(String filePath) {
//		try {
//			Map options = JavaCore.getOptions();
//			options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_1_8);
//			ASTParser astParser = ASTParser.newParser(AST.JLS8);
//			astParser.setKind(ASTParser.K_COMPILATION_UNIT);
//			String fs = FileUtils.getFileString(filePath);
//			astParser.setCompilerOptions(options);
//			astParser.setSource(fs.toCharArray());
//			CompilationUnit cu = (CompilationUnit) astParser.createAST(null);
//			List importList = cu.imports();
//			HashSet<String> dependentModuleDir = getDependentModuleDir(importList);
//			String unitName = filePath.replace(projectRootPath, "").replace(File.separator, "/");
//			String[] classPathEntries = {"C:\\Program Files\\Java\\jre1.8.0_131\\lib\\rt.jar"};
//			String[] srcPathEntries = dependentModuleDir.toArray(new String[dependentModuleDir.size()]);
//			
//			// parse again
//			astParser = ASTParser.newParser(AST.JLS8);
//			astParser.setKind(ASTParser.K_COMPILATION_UNIT);
//			astParser.setCompilerOptions(options);
//			astParser.setSource(fs.toCharArray());
////			String[] srcPathEntries = {"D:\\bce-plat\\finance\\fp-charging-v2\\src\\main\\java",
////									   "D:\\bce-plat\\finance\\fp-fundpool-biz\\src\\main\\java", 
////									   "D:\\bce-plat\\finance\\fp-charging-base\\src\\main\\java"};
//			String[] encodeArray = new String[srcPathEntries.length];
//			Arrays.fill(encodeArray, "UTF-8");
//			
//			astParser.setUnitName(unitName);
//			astParser.setEnvironment(classPathEntries, srcPathEntries,encodeArray, true);
//			astParser.setResolveBindings(true);
//			astParser.setBindingsRecovery(true);
//			
//			cu = (CompilationUnit) astParser.createAST(null);
//			FindLogVisitor findLogVisitor = new FindLogVisitor();
//			cu.accept(findLogVisitor);
//			
//			totalMethods += findLogVisitor.methodSet.size();
//			totalMethodWithLog += findLogVisitor.methodContainLog.size();
//			totalMethodWithInternalCall += findLogVisitor.methodContainInternalCall.size();
//			
////			for (String key : findLogVisitor.methodContainInternalCall.keySet()) {
////				System.out.printf("%s\t%s\n",key, findLogVisitor.methodContainInternalCall.get(key));
////			}
////			System.out.println("-------------------");
////			for (String key : findLogVisitor.methodContainLog.keySet()) {
////				System.out.printf("%s\t%s\n",key, findLogVisitor.methodContainLog.get(key));
////			}
//			
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//	}
// MainParser.java の outputEstimatedCoverageOnly メソッドを以下のように修正

	/**
	 * LogCoCoによって推定されたカバレッジ情報のうち、「Must」とマークされた部分のみを
	 * CSVファイルに出力します。Must行番号の詳細情報も含みます。
	 * @param outputCsvPath 出力するCSVファイルのパス
	 */
	public static void outputEstimatedCoverageOnly(String outputCsvPath) {
		// Memory monitoring for this method
		Runtime runtime = Runtime.getRuntime();
		long methodStartMemory = runtime.totalMemory() - runtime.freeMemory();
		
		System.out.println("Outputting 'Must' and 'May' estimated coverage (with line details) to: " + outputCsvPath);
		System.out.printf("Memory at method start: %.2f MB\n", methodStartMemory / (1024.0 * 1024.0));
		try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(outputCsvPath, false)))) {
			// CSVヘッダーの書き込み
			writer.println("Method,FilePath,StartLine,EndLine,MustCoveredLines,MayCoveredLines,TotalConsideredLines,MustLineCoverageRate,MayLineCoverageRate,MustCoveredBranches,MayCoveredBranches,TotalConsideredBranches,MustBranchCoverageRate,MayBranchCoverageRate,MustLineNumbers,MayLineNumbers,MustBranchRepresentativeLineNumbers,MayBranchRepresentativeLineNumbers");

			for (MethodNodeForOutput procMd : methodGlobalMarkMap.keySet()) {
				System.out.println("--------------------------------------");
				System.out.println("Processing method for CSV output (Must and May, detailed): " + procMd);
				HashMap<ASTNode, String> markMap = methodGlobalMarkMap.get(procMd);

				// === ラインカバレッジ (LogCoCoの推定に基づく) ===
				// coverageByLog に相当する TreeMap<Integer, String> lineStatusByLogCoCo
				TreeMap<Integer, String> lineStatusByLogCoCo = calculateCoverageMap(markMap, procMd);

				int mustTrueLines = 0;
				int mayTrueLines = 0;
				StringBuilder mustLineNumbersBuilder = new StringBuilder();
				StringBuilder mayLineNumbersBuilder = new StringBuilder();
				for (Map.Entry<Integer, String> entry : lineStatusByLogCoCo.entrySet()) {
					if ("Must".equals(entry.getValue())) {
						mustTrueLines++;
						if (mustLineNumbersBuilder.length() > 0) {
							mustLineNumbersBuilder.append(";");
						}
						mustLineNumbersBuilder.append(entry.getKey());
					} else if ("May".equals(entry.getValue())) {
						mayTrueLines++;
						if (mayLineNumbersBuilder.length() > 0) {
							mayLineNumbersBuilder.append(";");
						}
						mayLineNumbersBuilder.append(entry.getKey());
					}
				}
				int totalConsideredLines = lineStatusByLogCoCo.size(); // LogCoCoがステータスを割り当てた行の総数
				double mustLineCoverageRate = (totalConsideredLines > 0) ? (double) mustTrueLines / totalConsideredLines : 0.0;
				double mayLineCoverageRate = (totalConsideredLines > 0) ? (double) mayTrueLines / totalConsideredLines : 0.0;
				String mustLineNumbers = mustLineNumbersBuilder.length() > 0 ? mustLineNumbersBuilder.toString() : "None";
				String mayLineNumbers = mayLineNumbersBuilder.length() > 0 ? mayLineNumbersBuilder.toString() : "None";

				// === ブランチカバレッジ (LogCoCoの推定に基づく) ===
				// branchCoverageByLog に相当する TreeMap<Integer, String> branchStatusByLogCoCo
				TreeMap<Integer, String> branchStatusByLogCoCo = new TreeMap<>();
				int totalConsideredBranches = 0;

				for (ASTNode node : markMap.keySet()) {
					if ((node instanceof org.eclipse.jdt.core.dom.Block || node instanceof org.eclipse.jdt.core.dom.SwitchCase) &&
							!((node.getParent() instanceof org.eclipse.jdt.core.dom.TryStatement) ||
									(node.getParent() instanceof org.eclipse.jdt.core.dom.SynchronizedStatement) ||
									(node.getParent() instanceof org.eclipse.jdt.core.dom.MethodDeclaration))) {

						totalConsideredBranches++; // LogCoCoが考慮するブランチとしてカウント
						int representativeLine = -1;
						String status = markMap.get(node); // デフォルトはブロック/ケース自体のマーク

						if (node instanceof org.eclipse.jdt.core.dom.SwitchCase) {
							representativeLine = procMd.cu.getLineNumber(node.getStartPosition());
						} else if (node instanceof org.eclipse.jdt.core.dom.Block) {
							java.util.List stmtList = ((org.eclipse.jdt.core.dom.Block) node).statements();
							if (!stmtList.isEmpty()) {
								// ブロック内の最初のステートメントの行とステータスを代表とする
								org.eclipse.jdt.core.dom.Statement firstStmtInBlock = (org.eclipse.jdt.core.dom.Statement) stmtList.get(0);
								representativeLine = procMd.cu.getLineNumber(firstStmtInBlock.getStartPosition());
								status = markMap.get(firstStmtInBlock); // 最初のステートメントのマークを使用
							} else {
								// 空のブロックの場合
								representativeLine = procMd.cu.getLineNumber(node.getStartPosition());
							}
						}

						if(representativeLine != -1 && status != null) { // 有効な行番号とステータスがある場合のみ
							branchStatusByLogCoCo.put(representativeLine, status);
						} else if (representativeLine != -1) { // ステータスがnullだが行は取れる場合 (マークマップにないノードなど)
							// このケースは通常、markMap.keySet() をループしているので発生しにくいが念のため
							// branchStatusByLogCoCo.put(representativeLine, "Unknown"); // またはスキップ
						}
					}
				}

				int mustTrueBranches = 0;
				int mayTrueBranches = 0;
				StringBuilder mustBranchLineNumbersBuilder = new StringBuilder();
				StringBuilder mayBranchLineNumbersBuilder = new StringBuilder();
				for (Map.Entry<Integer, String> entry : branchStatusByLogCoCo.entrySet()) {
					if ("Must".equals(entry.getValue())) {
						mustTrueBranches++;
						if (mustBranchLineNumbersBuilder.length() > 0) {
							mustBranchLineNumbersBuilder.append(";");
						}
						mustBranchLineNumbersBuilder.append(entry.getKey());
					} else if ("May".equals(entry.getValue())) {
						mayTrueBranches++;
						if (mayBranchLineNumbersBuilder.length() > 0) {
							mayBranchLineNumbersBuilder.append(";");
						}
						mayBranchLineNumbersBuilder.append(entry.getKey());
					}
				}
				// totalConsideredBranches は上でカウント済み
				double mustBranchCoverageRate = (totalConsideredBranches > 0) ? (double) mustTrueBranches / totalConsideredBranches : 0.0;
				double mayBranchCoverageRate = (totalConsideredBranches > 0) ? (double) mayTrueBranches / totalConsideredBranches : 0.0;
				String mustBranchRepresentativeLineNumbers = mustBranchLineNumbersBuilder.length() > 0 ? mustBranchLineNumbersBuilder.toString() : "None";
				String mayBranchRepresentativeLineNumbers = mayBranchLineNumbersBuilder.length() > 0 ? mayBranchLineNumbersBuilder.toString() : "None";

				// メソッド情報の取得
				String methodName = procMd.md.getName().toString();
				String filePath = procMd.filePath;
				int startLine = procMd.cu.getLineNumber(procMd.md.getStartPosition());
				int endLine = procMd.cu.getLineNumber(procMd.md.getStartPosition() + procMd.md.getLength() - 1);

				// CSVへの書き込み
				writer.printf("\"%s\",\"%s\",%d,%d,%d,%d,%d,%.4f,%.4f,%d,%d,%d,%.4f,%.4f,\"%s\",\"%s\",\"%s\",\"%s\"\n",
						methodName.replace("\"", "\"\""),
						filePath.replace("\"", "\"\""),
						startLine,
						endLine,
						mustTrueLines,
						mayTrueLines,
						totalConsideredLines,
						mustLineCoverageRate,
						mayLineCoverageRate,
						mustTrueBranches,
						mayTrueBranches,
						totalConsideredBranches,
						mustBranchCoverageRate,
						mayBranchCoverageRate,
						mustLineNumbers.replace("\"", "\"\""),
						mayLineNumbers.replace("\"", "\"\""),
						mustBranchRepresentativeLineNumbers.replace("\"", "\"\""),
						mayBranchRepresentativeLineNumbers.replace("\"", "\"\""));

				// 標準出力 (デバッグ用)
				System.out.printf("  Method: %s (%s:%d-%d)\n", methodName, filePath, startLine, endLine);
				System.out.printf("  Line Coverage (Must/May/Total): %d / %d / %d (%.2f%% / %.2f%%)\n", mustTrueLines, mayTrueLines, totalConsideredLines, mustLineCoverageRate * 100, mayLineCoverageRate * 100);
				System.out.println("  Must Line Numbers: " + mustLineNumbers);
				System.out.println("  May Line Numbers: " + mayLineNumbers);
				System.out.printf("  Branch Coverage (Must/May/Total): %d / %d / %d (%.2f%% / %.2f%%)\n", mustTrueBranches, mayTrueBranches, totalConsideredBranches, mustBranchCoverageRate * 100, mayBranchCoverageRate * 100);
				System.out.println("  Must Branch Representative Line Numbers: " + mustBranchRepresentativeLineNumbers);
				System.out.println("  May Branch Representative Line Numbers: " + mayBranchRepresentativeLineNumbers);
			}
			System.out.println("Finished writing 'Must' and 'May' estimated coverage (with line details) to CSV.");
			
			// Memory usage at method end
			long methodEndMemory = runtime.totalMemory() - runtime.freeMemory();
			System.out.printf("Memory at method end: %.2f MB\n", methodEndMemory / (1024.0 * 1024.0));
			System.out.printf("Memory used by outputEstimatedCoverageOnly: %.2f MB\n", (methodEndMemory - methodStartMemory) / (1024.0 * 1024.0));
		} catch (Exception e) {
			System.err.println("Error writing 'Must' and 'May' estimated coverage (with line details) to CSV: " + outputCsvPath);
			e.printStackTrace();
		}
	}
	
	public static void parse(String filePath) {
		
		HashMap<String, CompilationUnit> filePathCompilationUnitMap = new HashMap<>();
		System.out.println(filePath);
		try {
			ArrayList<InterProcNode> interProcNodesInFile = new ArrayList<>();
			CompilationUnit cu = getResolvedCUFromFilePath(filePath);
			MethodSummaryVisitor mSumVisitor = new MethodSummaryVisitor();
			cu.accept(mSumVisitor);
			for (MethodDeclaration mdNode : mSumVisitor.methodNodeList) {
				InterProcNode nInterProcNode = new InterProcNode(cu, filePath, mdNode);
				interProcNodesInFile.add(nInterProcNode);
			}
			filePathCompilationUnitMap.put(filePath, cu);
			for (InterProcNode node : interProcNodesInFile) {
				DefaultDirectedGraph<InterProcNode, DefaultEdge> callGraph =
						new DefaultDirectedGraph<>(DefaultEdge.class);
				callGraph.addVertex(node);
//				if(((MethodDeclaration)node.astNode).getName().toString().equals("pullStream")) {
//					System.out.println("recursive");
//				}
				node.invokeMD = null; // null indicates that this is the root method
				processMethod(callGraph, node, cu, filePathCompilationUnitMap,filePath);
				
				MethodDeclaration md = (MethodDeclaration) node.astNode;
				FindLogVisitor findLogVisitor = new FindLogVisitor(invokeHeuritics);
				md.accept(findLogVisitor);
				ArrayList<String> directLogInMethodList = new ArrayList<>();
				for (ASTNode logNode : findLogVisitor.logPrintAndMIStmt) {
					int number = cu.getLineNumber(logNode.getStartPosition());
					directLogInMethodList.add(node.shortFilePath +":" +number);
//					System.out.println(node.shortFilePath +":" +number);
				}
				
				if (directLogInMethodList.isEmpty()) {
					continue;
				}
				CycleDetector<InterProcNode, DefaultEdge> cycleDetector = new CycleDetector<>(callGraph);
				if (cycleDetector.detectCycles()) {
					Set<InterProcNode> cycleNodes = cycleDetector.findCycles();
					System.out.println("CallGraphRecursive! --> " + node);
					continue;
				}	
				
				if (md.getName().toString().equals("loadAllDatanodes"))
					System.out.println("debug");
				
				HashMap<InterProcNode, ArrayList<ArrayList<DefaultEdge>>> branchPos = FileUtils.extractBranchPossibility(callGraph);
				ArrayList<HashMap<InterProcNode, ArrayList<DefaultEdge>>> list = new ArrayList<>();

				if (branchPos.size() >= 20) {
					System.out.println("Branch size more than 20 " + filePath + ";"+ node );
					continue;
				}
				
				FileUtils.combine(0, new HashMap<InterProcNode,ArrayList<DefaultEdge>>(), branchPos, list);
//				System.out.println(md.getName()+":"+list.size());
				if (list.size() >= 100000) {
					System.out.println("path size more than 100000" + filePath + ";"+ node );
					System.out.println(list.size());
					continue;
				}
//				System.out.println("pivot");
				HashMap<String, ArrayList<ArrayList<InterProcNode>>> regexPathMap = new HashMap<>();
//				System.out.println(list.size());
				HashSet<String> logRegexSet = new HashSet<>();
				
				for (HashMap<InterProcNode, ArrayList<DefaultEdge>> branchEdgeMap : list) {
//					System.out.println(count);
					ArrayList<InterProcNode> astPath = new ArrayList<>();
					ArrayList<InterProcNode> keyList = new ArrayList<>();
					keyList.addAll(branchEdgeMap.keySet());
//					if (keyList.size() == 5 ) {
//						if (branchEdgeMap.get(keyList.get(0)).get(0).toString().contains("302:310")
//								&& branchEdgeMap.get(keyList.get(1)).get(0).toString().contains("305:310")
//								 && branchEdgeMap.get(keyList.get(2)).get(0).toString().contains("305:310")
//								 && branchEdgeMap.get(keyList.get(3)).get(0).toString().contains("305:308"))
//							System.out.println("debug");
//					}
					
					String logRegex = FileUtils.dfsTraverseCallGraphToGenerateLogPattern(callGraph, node, false, false, branchEdgeMap, astPath,false);
//					if(logRegex.equals(anObject))
					if (logRegex.equals("")) {
//						System.out.println("empty log regex");
						continue;
					}
//					System.out.println("LogRegex: "+ logRegex);
//					System.out.println("DirectLogList + " + directLogInMethodList);
					boolean hasDirectLog = false;
					for (String possibleDirectLogIdInMethod : directLogInMethodList) {
						if(logRegex.contains(possibleDirectLogIdInMethod))
							hasDirectLog = true;
					}
					if (!hasDirectLog)
						continue;
					if (regexPathMap.containsKey(logRegex)) {
						regexPathMap.get(logRegex).add(astPath);
					} else {
						ArrayList<ArrayList<InterProcNode>> newPathList = new ArrayList<>();
						newPathList.add(astPath);
						regexPathMap.put(logRegex, newPathList);
					}
					logRegexSet.add(logRegex);
				}
				
//				for (String regex : logRegexSet) {
//					System.out.println("********************");
//					System.out.println(regex);
//				}
				
				// the following code is used to match the real log and calculate coverage 
//				System.out.println("logRegexSet:" + logRegexSet);
				if (!logRegexSet.isEmpty()) {
					System.out.println("--------------This method contains Logging code---------------");
					System.out.println("Method Name: " + md.getName());
					
					if (md.getName().toString().equals("dropMemstoreContents")) {
						continue;
					}
					
					for(String logRegex:logRegexSet) {
						System.out.println(logRegex);
					}
					calcualteCoverage(logRegexSet, regexPathMap, node, cu, callGraph,directLogInMethodList);
				}
//				System.out.println("methdFinish");
				/*
				LogRegexMatcher logRegexMatcher = new LogRegexMatcher(logRegexSet);
				logRegexMatcher.feedInLogFile(testLogSequenceFile);
				ArrayList<String> matchedLogRegexList = logRegexMatcher.getMatchedLogRegex();
				HashMap<ASTNode, String> astNodeMarkMap = new HashMap<>();
				
				for (String regex : matchedLogRegexList) {
					System.out.println("********************");
					System.out.println(regex);
					findCommonPath(regex, regexPathMap, node, astNodeMarkMap);
				}
				
				TreeMap<Integer, String> lineCoverageMap = new TreeMap<>();
				for (ASTNode astNode : astNodeMarkMap.keySet()) {
					
					String coverStatus = astNodeMarkMap.get(astNode);
					
					if (astNode instanceof IfStatement || astNode instanceof SwitchStatement || astNode instanceof ForStatement
							|| astNode instanceof EnhancedForStatement || astNode instanceof WhileStatement || astNode instanceof TryStatement) {
						lineCoverageMap.put(cu.getLineNumber(astNode.getStartPosition()), coverStatus);
					} else {
						int startLine = cu.getLineNumber(astNode.getStartPosition());
						int endLine = cu.getLineNumber(astNode.getStartPosition() + astNode.getLength() - 1);
						for (int lineNumber = startLine; lineNumber <= endLine; lineNumber ++) {
							lineCoverageMap.put(lineNumber, coverStatus);
						}
					}
//					System.out.println(astNode.getClass().getName());
//					System.out.println(cu.getLineNumber(astNode.getStartPosition()));
//					System.out.println(astNodeMarkMap.get(astNode));
				}
				for(Integer lineNumber : lineCoverageMap.keySet()) {
					System.out.printf("%d,%s\n", lineNumber, lineCoverageMap.get(lineNumber));
				}
				*/
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private static HashSet<InterProcNode> findInvolvedMethodDeclarationProcNode(String matchedRegex, HashMap<String, ArrayList<ArrayList<InterProcNode>>> regexPathMap,
			HashMap<String, ArrayList<InterProcNode>> matchedRegexAndCorrespondingMD) {
		
		ArrayList<HashSet<InterProcNode>> executedNodeAll = new ArrayList<>();
		HashSet<InterProcNode> mayNodeNonRelatedToLog = new HashSet<>();
		// Check if regexPathMap contains the matchedRegex
		if (!regexPathMap.containsKey(matchedRegex) || regexPathMap.get(matchedRegex) == null) {
			System.err.println("Warning: No paths found for regex: " + matchedRegex);
			return new HashSet<>();
		}
		
		for(ArrayList<InterProcNode> astPath : regexPathMap.get(matchedRegex)) {
			HashSet<InterProcNode> exeNodeSet = new HashSet<>();
			for (InterProcNode tmpProcNode : astPath) {
				if (tmpProcNode.mayInTraverse == true) {
					mayNodeNonRelatedToLog.add(tmpProcNode);
				} else {
					exeNodeSet.add(tmpProcNode);
				}
			}
			executedNodeAll.add(exeNodeSet);
		}
		
		// Check if executedNodeAll is empty to avoid IndexOutOfBoundsException
		if (executedNodeAll.isEmpty()) {
			System.err.println("Warning: No execution paths found for regex: " + matchedRegex);
			return new HashSet<>();
		}
		
		HashSet<InterProcNode> mustNodeSet = new HashSet<>(executedNodeAll.get(0));
		HashSet<InterProcNode> mayNodeSet = new HashSet<>(executedNodeAll.get(0));
		
		for (int i = 1; i < executedNodeAll.size(); i ++) {
			mayNodeSet.addAll(executedNodeAll.get(i));
			mustNodeSet.retainAll(executedNodeAll.get(i));
		}
		
		HashSet<InterProcNode> possibleNode = new HashSet<>(mayNodeSet);
		Iterator<InterProcNode> iterator = possibleNode.iterator();
		while (iterator.hasNext()) {
			InterProcNode n = iterator.next();
			if (mustNodeSet.contains(n)) {
				mayNodeSet.remove(n);
			}
		}
		
		mayNodeSet.addAll(mayNodeNonRelatedToLog);
		mustNodeSet.removeAll(mayNodeNonRelatedToLog);
		
		HashSet<InterProcNode> mustExeMDNodeSet = new HashSet<>();
		
		for (InterProcNode node : mustNodeSet) {
			if (node.astNode instanceof MethodDeclaration) {
				mustExeMDNodeSet.add(node);
			}
		}
		
		return mustExeMDNodeSet;
	}
	
	private static HashMap<ASTNode, String> findCommonPath(String matchedRegex,
			HashMap<String, ArrayList<ArrayList<InterProcNode>>> regexPathMap,
			InterProcNode procNodeForCoverage, HashMap<ASTNode, String> astNodeGlobalMarkMap,
			DefaultDirectedGraph<InterProcNode, DefaultEdge> callGraph) {
		String fileCalculateCoverage = procNodeForCoverage.filePath;
		int startLine = procNodeForCoverage.getStartLineOfNode();
		int endLine = procNodeForCoverage.getEndLineOfNode();
		
		
		ArrayList<HashSet<InterProcNode>> executedNodeInMd = new ArrayList<>();
		
		HashSet<InterProcNode> mayNodeNonRelatedToLog = new HashSet<>();
		
		HashSet<InterProcNode> allPossibleExeNode = new HashSet<>();
		
		HashSet<InterProcNode> allVertexInCallGraphForTheMD = new HashSet<>();
		
		// Check if regexPathMap contains the matchedRegex
		if (!regexPathMap.containsKey(matchedRegex) || regexPathMap.get(matchedRegex) == null) {
			System.err.println("Warning: No paths found in findCommonPath for regex: " + matchedRegex);
			return new HashMap<>();
		}
		
		for (ArrayList<InterProcNode> astPath : regexPathMap.get(matchedRegex)) {
			HashSet<InterProcNode> exeNodeSet = new HashSet<>();
			for (InterProcNode tmpProcNode : astPath) {
				if (tmpProcNode.mayInTraverse == true) {
					if (tmpProcNode.getStartLineOfNode() >= startLine && tmpProcNode.getEndLineOfNode() <= endLine && tmpProcNode.filePath.equals(fileCalculateCoverage)) {
						mayNodeNonRelatedToLog.add(tmpProcNode);
					}
				} else if (tmpProcNode.filePath.equals(fileCalculateCoverage)) {
					if (tmpProcNode.getStartLineOfNode() >= startLine && tmpProcNode.getEndLineOfNode() <= endLine) {
						exeNodeSet.add(tmpProcNode);
					}
				}
			}
			allPossibleExeNode.addAll(exeNodeSet);
			executedNodeInMd.add(exeNodeSet);
		}
		
		for (InterProcNode vertex : callGraph.vertexSet()) {
			if(vertex.filePath.equals(fileCalculateCoverage) && 
					vertex.getStartLineOfNode() >= startLine && vertex.getEndLineOfNode() <= endLine) {
				allVertexInCallGraphForTheMD.add(vertex);
			}
		}
		
		allPossibleExeNode.addAll(mayNodeNonRelatedToLog);
		HashSet<InterProcNode> mustNotNodeSet = new HashSet<>(allVertexInCallGraphForTheMD);
		mustNotNodeSet.removeAll(allPossibleExeNode);
		
		// Check if executedNodeInMd is empty to avoid IndexOutOfBoundsException
		if (executedNodeInMd.isEmpty()) {
			System.err.println("Warning: No execution paths found in method for regex: " + matchedRegex);
			// Return an empty map when no execution paths are found
			return new HashMap<>();
		}
		
		HashSet<InterProcNode> mustNodeSet = new HashSet<>(executedNodeInMd.get(0));
		HashSet<InterProcNode> mayNodeSet = new HashSet<>(executedNodeInMd.get(0));
		
		for (int i = 1; i < executedNodeInMd.size(); i ++) {
			mayNodeSet.addAll(executedNodeInMd.get(i));
			mustNodeSet.retainAll(executedNodeInMd.get(i));
		}
		
		HashSet<InterProcNode> possibleNode = new HashSet<>(mayNodeSet);
		Iterator<InterProcNode> iterator = possibleNode.iterator();
		while (iterator.hasNext()) {
			InterProcNode n = iterator.next();
			if (mustNodeSet.contains(n)) {
				mayNodeSet.remove(n);
			}
		}
		
		mayNodeSet.addAll(mayNodeNonRelatedToLog);
		mustNodeSet.removeAll(mayNodeNonRelatedToLog);
		
		HashSet<InterProcNode>mustLogNodeSet = new HashSet<>();
		for (InterProcNode mustNode : mustNodeSet) {
			if (mustNode.isLog)
				mustLogNodeSet.add(mustNode);
		}
		
		HashSet<InterProcNode> tmpSet = new HashSet<>(mustNotNodeSet);
		for (InterProcNode mustNotCandidate : tmpSet) {
			if (!mustNotCandidate.isLog) {
				
				boolean mustNotNodeBeforeLog = false;
				boolean noLogBeforeTheNode = true;
				
				for (InterProcNode node: mustLogNodeSet) {
					if (node.getStartLineOfNode() > mustNotCandidate.getStartLineOfNode()) {
						mustNotNodeBeforeLog = true;
						continue;
					}
					if (node.getStartLineOfNode() < mustNotCandidate.getStartLineOfNode()) {
						noLogBeforeTheNode = false;
					}
					
				}
				if (!mustNotNodeBeforeLog) {
					mustNotNodeSet.remove(mustNotCandidate);
					mayNodeSet.add(mustNotCandidate);
				}
				if (noLogBeforeTheNode) {
					mustNotNodeSet.remove(mustNotCandidate);
					mayNodeSet.add(mustNotCandidate);
				}
			}	
//			String possibleregex = FileUtils.dfsTraverseCallGraphIsLogExist(callGraph, mustNotCandidate, false, false);
//				System.out.println(possibleregex);
				// if the possible regex ever contains any of the regex in current file
				// if not , then set the node to may
				
//				mustNotNodeSet.remove(mustNotCandidate);
//				mayNodeSet.add(mustNotCandidate);
		}
		
		
		LinkedHashMap<ASTNode, String> astNodeLocalMarkMap = new LinkedHashMap<>();
		
		LinkedHashSet<ASTNode> allStmtLevelNodes = new LinkedHashSet<>();
		
		initASTMarkSet(procNodeForCoverage.astNode, allStmtLevelNodes);
		Iterator<ASTNode> iterator2 = allStmtLevelNodes.iterator();
		int count = 0;
		ASTNode firstBlockNode=null;
		while (iterator2.hasNext()) {
			if(count == 0) {
				firstBlockNode = iterator2.next();
				astNodeLocalMarkMap.put(firstBlockNode, "Must");
			}
			else {
				astNodeLocalMarkMap.put(iterator2.next(), "May");
			}
			count ++;
		}
		if(firstBlockNode !=null)
			markMustNodeCoverage(astNodeLocalMarkMap, firstBlockNode, procNodeForCoverage.astNode);

//		for (InterProcNode mayNode : mayNodeSet) {
//			markMayNodeCoverage(astNodeGlobalMarkMap, mayNode.astNode, procNodeForCoverage.astNode);
//			markMayNodeCoverage(astNodeLocalMarkMap, mayNode.astNode, procNodeForCoverage.astNode);
//		}
		
		for (InterProcNode mustNotNode : mustNotNodeSet) {
			markMustNotNodeCoverage(astNodeLocalMarkMap, mustNotNode.astNode, procNodeForCoverage.astNode);
		}
		for (InterProcNode mustNode : mustNodeSet) {
			if (mustNode.astNode instanceof MethodInvocation) {
				continue;
			}
			if (mustNode.astNode instanceof TryStatement) {
				markMustNodeCoverage(astNodeLocalMarkMap, (ASTNode)((TryStatement) mustNode.astNode).getBody().statements().get(0), procNodeForCoverage.astNode);
			}
			if (mustNode.astNode instanceof SynchronizedStatement) {
				markMustNodeCoverage(astNodeLocalMarkMap, (ASTNode)((SynchronizedStatement) mustNode.astNode).getBody().statements().get(0), procNodeForCoverage.astNode);
			}
			markMustNodeCoverage(astNodeLocalMarkMap, mustNode.astNode, procNodeForCoverage.astNode);
		}

		
		return astNodeLocalMarkMap;
//		System.out.println(mustNodeSet);
//		System.out.println(mayNodeSet);
	}
	
	private static void initASTMarkSet(ASTNode node, LinkedHashSet<ASTNode> allStmtLevelNodes) {
		
		if (node == null)
			return;
		if (node instanceof MethodDeclaration) {
			initASTMarkSet(((MethodDeclaration)node).getBody(),allStmtLevelNodes);
		} else if (node instanceof Block) {
			allStmtLevelNodes.add(node);
			Block blk = (Block) node;
			for ( int i = 0; i < blk.statements().size(); i ++) {
				initASTMarkSet((ASTNode)blk.statements().get(i), allStmtLevelNodes);
			}
		} else if (node instanceof IfStatement) {
			allStmtLevelNodes.add(node);
			IfStatement ifStatement = (IfStatement) node;
			initASTMarkSet(ifStatement.getThenStatement(), allStmtLevelNodes);
			initASTMarkSet(ifStatement.getElseStatement(), allStmtLevelNodes);
		} else if (node instanceof SwitchStatement) {
			allStmtLevelNodes.add(node);
			SwitchStatement switchStatement = (SwitchStatement) node;
			for ( int i = 0; i < switchStatement.statements().size(); i ++) {
				initASTMarkSet((ASTNode)switchStatement.statements().get(i), allStmtLevelNodes);
			}
		} else if (node instanceof ForStatement) {
			allStmtLevelNodes.add(node);
			initASTMarkSet(((ForStatement) node).getBody(),allStmtLevelNodes);
			
		} else if (node instanceof WhileStatement) {
			allStmtLevelNodes.add(node);
			initASTMarkSet(((WhileStatement) node).getBody(),allStmtLevelNodes);

		} else if (node instanceof EnhancedForStatement) {
			allStmtLevelNodes.add(node);
			initASTMarkSet(((EnhancedForStatement) node).getBody(),allStmtLevelNodes);
		} else if (node instanceof TryStatement) {
			allStmtLevelNodes.add(node);
			initASTMarkSet(((TryStatement) node).getBody(),allStmtLevelNodes);
			initASTMarkSet(((TryStatement) node).getFinally(),allStmtLevelNodes);
		} else if (node instanceof SynchronizedStatement) {
			allStmtLevelNodes.add(node);
			initASTMarkSet(((SynchronizedStatement) node).getBody(), allStmtLevelNodes);
		} else {
			allStmtLevelNodes.add(node);
		}
		
	}
	
	private static void markAllSubNodes(ASTNode node, HashMap<ASTNode, String> astNodeMarkMap, String label) {
		if (node == null)
			return;
		assert astNodeMarkMap.containsKey(node);
		if (node instanceof Block) {
			Block blk = (Block) node;
			astNodeMarkMap.put(blk,label);
			for ( int i = 0; i < blk.statements().size(); i ++) {
				markAllSubNodes((ASTNode)blk.statements().get(i), astNodeMarkMap, label);
			}
		} else if (node instanceof IfStatement) {
			astNodeMarkMap.put(node,label);
			IfStatement ifStatement = (IfStatement) node;
			markAllSubNodes(ifStatement.getThenStatement(), astNodeMarkMap,label);
			markAllSubNodes(ifStatement.getElseStatement(), astNodeMarkMap, label);
		} else if (node instanceof SwitchStatement) {
			astNodeMarkMap.put(node,label);
			SwitchStatement switchStatement = (SwitchStatement) node;
			for ( int i = 0; i < switchStatement.statements().size(); i ++) {
				markAllSubNodes((ASTNode)switchStatement.statements().get(i), astNodeMarkMap, label);
			}
		} else if (node instanceof ForStatement) {
			astNodeMarkMap.put(node,label);
			markAllSubNodes(((ForStatement) node).getBody(),astNodeMarkMap, label);
		} else if (node instanceof EnhancedForStatement) {
			astNodeMarkMap.put(node,label);
			markAllSubNodes(((EnhancedForStatement) node).getBody(),astNodeMarkMap, label);
		} else if (node instanceof WhileStatement) {
			astNodeMarkMap.put(node,label);
			markAllSubNodes(((WhileStatement) node).getBody(),astNodeMarkMap, label);

		} else if (node instanceof TryStatement) {
			astNodeMarkMap.put(node,label);
			markAllSubNodes(((TryStatement) node).getBody(),astNodeMarkMap, label);
			markAllSubNodes(((TryStatement) node).getFinally(),astNodeMarkMap, label);
		} else if (node instanceof SynchronizedStatement){
			astNodeMarkMap.put(node,label);
			markAllSubNodes(((SynchronizedStatement) node).getBody(),astNodeMarkMap, label);
		} else {
			astNodeMarkMap.put(node,label);
		}
	}
	
	private static void markMustNotNodeCoverage(HashMap<ASTNode, String> astNodeMarkMap, ASTNode markNode, ASTNode rootEntryMethodNode) {
		
		if (!astNodeMarkMap.containsKey(markNode))
			return;
		if (markNode == null)
			return;
		if (markNode instanceof MethodDeclaration) { 
			return;
		} else if (markNode instanceof Block) {
			if (astNodeMarkMap.get(markNode).equals("Must"))
				return;
			astNodeMarkMap.put(markNode, "MustNot");
			// mark all children node with must not;
			markAllSubNodes((Block)markNode, astNodeMarkMap, "MustNot");
			ASTNode parentNode = markNode.getParent();
			if (parentNode instanceof TryStatement || parentNode instanceof SynchronizedStatement) {
				astNodeMarkMap.put(parentNode, "MustNot");
				markMustNotNodeCoverage(astNodeMarkMap, parentNode, rootEntryMethodNode);
			} 
		} else {
			astNodeMarkMap.put(markNode, "MustNot");
			ASTNode parentNode = markNode.getParent();
			
			if (parentNode instanceof Block) {
				Block parentBlock =  (Block) parentNode;
				List stmtList = parentBlock.statements();
				int markNodeIndex = getIndexOfRelatedStmtFromMarkNode(stmtList, markNode);
				if (markNodeIndex == -1) {
					System.err.println("Cannot find node in statements!");
				}
				for (int i = markNodeIndex; i < stmtList.size(); i ++) {
					Statement stmt = (Statement) stmtList.get(i);
					astNodeMarkMap.put(stmt, "MustNot");
				}
				boolean wholeBlockMustNot = true;
				for (int i = markNodeIndex - 1; i >= 0; i --) {
					Statement stmt = (Statement) stmtList.get(i);
					if (isStmtContainReturnStmt(stmt)) {
						wholeBlockMustNot = false;
						break;
					} else {
						markAllSubNodes(stmt, astNodeMarkMap, "MustNot");
					}
				}
				if (wholeBlockMustNot) {
					astNodeMarkMap.put(parentNode, "MustNot");
				}
//				markMustNotNodeCoverage(astNodeMarkMap,parentNode,rootEntryMethodNode);
			} else {
				
			}
		}
	}
	
	public static void markMustNodeCoverage(HashMap<ASTNode, String> astNodeMarkMap, ASTNode markNode, ASTNode rootEntryMethodNode) {
		
		if (markNode instanceof Block) {
			astNodeMarkMap.put(markNode, "Must");
			if (markNode.getParent().equals(rootEntryMethodNode)) {
				List stmtList = ((Block) markNode).statements();
				if (!stmtList.isEmpty()) {
					 Statement firstStmt = (Statement) stmtList.get(0);
					 if (astNodeMarkMap.get(firstStmt).equals("May")) {
						 for (int i = 0; i < stmtList.size(); i ++) {
							 Statement stmt = (Statement) stmtList.get(i);
							 astNodeMarkMap.put(stmt, "Must");
							 if (isStmtContainReturnStmt(stmt)) {
								 boolean stopStmt = false;
								 for(ASTNode node : extractReturnStmtInsideStmt(stmt)) {
									if (astNodeMarkMap.get(node) != "MustNot") {
										stopStmt = true;
										break;
									}
								}
								 if (stopStmt)
									 break;
							 }
						 }
					 }
				}
			}
			markMustNodeCoverage(astNodeMarkMap, markNode.getParent(), rootEntryMethodNode);
		} else if (markNode.equals(rootEntryMethodNode)) {
			return;
		} else {
			astNodeMarkMap.put(markNode, "Must");
			ASTNode parentNode = markNode.getParent();
			if (parentNode instanceof Block) {
				Block parentBlock =  (Block) parentNode;
				List stmtList = parentBlock.statements();
				int markNodeIndex = getIndexOfRelatedStmtFromMarkNode(stmtList, markNode);
				if (markNodeIndex == -1) {
					System.err.println("Cannot find node in statements!");
				}
				for (int i = 0; i <= markNodeIndex; i ++) {
					Statement stmt = (Statement) stmtList.get(i);
					astNodeMarkMap.put(stmt, "Must");
				}
				if (!isStmtContainReturnStmt(markNode)) {
					for (int i = markNodeIndex +1; i < stmtList.size(); i ++) {
						Statement stmt = (Statement) stmtList.get(i);
						astNodeMarkMap.put(stmt, "Must");
//						if (stmt instanceof TryStatement) {
//							TryStatement tryStatement = (TryStatement) stmt;
//							tryStatement.getBody().statements()
//						}
						if (isStmtContainReturnStmt(stmt)) {
							boolean stopStmt = false;
							for(ASTNode node : extractReturnStmtInsideStmt(stmt)) {
								if (astNodeMarkMap.get(node) != "MustNot") {
									stopStmt = true;
									break;
								}
							}
							if (stopStmt)
								break;
						}
					}
				}
			} else if (parentNode instanceof SwitchStatement) {
				SwitchStatement switchStatement = (SwitchStatement) parentNode;
				List stmtList = switchStatement.statements();
				ASTNode nearestSwitchCase = switchStatement; // init with switchstmt temporarily
				for (int i = 0 ; i< stmtList.size(); i ++) {
					if (stmtList.get(i) instanceof SwitchCase) {
						SwitchCase switchCase = (SwitchCase) stmtList.get(i);
						if (switchCase.getStartPosition() < markNode.getStartPosition() && switchCase.getStartPosition() > nearestSwitchCase.getStartPosition()) {
							nearestSwitchCase = switchCase;
						}
					}
				}
				astNodeMarkMap.put(nearestSwitchCase, "Must");
				
			} 
			markMustNodeCoverage(astNodeMarkMap, parentNode.getParent(), rootEntryMethodNode);
		}
		
		
		
//		if (markNode instanceof Block) {
//			astNodeMarkMap.put(markNode, "Must");
//			markMustNodeCoverage(astNodeMarkMap, markNode.getParent(), rootEntryMethodNode);
//			return;
//		}
//		
//		if (markNode.equals(rootEntryMethodNode)) {
//			return;
//		}
//		
//		ASTNode blockyNode = getContainBlockyNode(markNode);
//		if (!(blockyNode instanceof Block)) {
//			astNodeMarkMap.put(markNode, "Must");
//			if (blockyNode instanceof SwitchStatement) {
//				SwitchStatement switchStatement = (SwitchStatement) blockyNode;
//				List stmtList = switchStatement.statements();
//				ASTNode nearestSwitchCase = switchStatement; // init with switchstmt temporarily
//				for (int i = 0 ; i< stmtList.size(); i ++) {
//					if (stmtList.get(i) instanceof SwitchCase) {
//						SwitchCase switchCase = (SwitchCase) stmtList.get(i);
//						if (switchCase.getStartPosition() < markNode.getStartPosition() && switchCase.getStartPosition() > nearestSwitchCase.getStartPosition()) {
//							nearestSwitchCase = switchCase;
//						}
//					}
//				}
//				astNodeMarkMap.put(nearestSwitchCase, "Must");
//			}
//			if (markNode instanceof MethodInvocation) {
//				return;
//			}
//			markMustNodeCoverage(astNodeMarkMap, markNode.getParent(), rootEntryMethodNode);
//		} else {
//			Block parentBlock = (Block) blockyNode;
//			List stmtList = parentBlock.statements();
//			int markNodeIndex = getIndexOfRelatedStmtFromMarkNode(stmtList, markNode);
//			if (markNodeIndex == -1) {
//				System.err.println("Cannot find node in statements!");
//			}
//			
//			for (int i = 0; i <= markNodeIndex; i ++) {
//				Statement stmt = (Statement) stmtList.get(i);
////				if (stmt instanceof TryStatement)
////					System.out.println("??");
//				astNodeMarkMap.put(stmt, "Must");
//			}
//			
//			if (!isStmtContainReturnStmt(markNode)) {
//				for (int i = markNodeIndex +1; i < stmtList.size(); i ++) {
//					Statement stmt = (Statement) stmtList.get(i);
////					if (stmt instanceof TryStatement)
////						System.out.println("??");
//					astNodeMarkMap.put(stmt, "Must");
//					if (isStmtContainReturnStmt(stmt)) {
//						break;
//					}
//				}
//			}
//			
//			if (parentBlock.getParent().equals(rootEntryMethodNode)) {
//				return;
//			}
//			markMustNodeCoverage(astNodeMarkMap, parentBlock.getParent(), rootEntryMethodNode);
//		}
	}
	
	private static void markMayNodeCoverage(HashMap<ASTNode, String> astNodeMarkMap, ASTNode mayNode, ASTNode rootEntryMethodNode) {
//		if (mayNode instanceof Block) {
//			if (!astNodeMarkMap.containsKey(mayNode)) {
//				astNodeMarkMap.put(mayNode, "May");
//			}
//			markMayNodeCoverage(astNodeMarkMap, mayNode.getParent(), rootEntryMethodNode);
//			return;
//		}
		if (mayNode.equals(rootEntryMethodNode)) {
			return;
		}
		
//		if (astNodeMarkMap.containsKey(mayNode)) {
//			if (astNodeMarkMap.get(mayNode).equals("Must")) {
//				return;
//			}
//		}
		
		if (!astNodeMarkMap.containsKey(mayNode)) {
			astNodeMarkMap.put(mayNode, "May");
			markMayNodeCoverage(astNodeMarkMap, mayNode.getParent(), rootEntryMethodNode);
		} 
//		else if (astNodeMarkMap.containsKey(mayNode)) {
//			if (astNodeMarkMap.get(mayNode).equals("MustNot")) {
//				astNodeMarkMap.put(mayNode,"May");
//			}
//		}
		
		
		
		
		
		
//		ASTNode blockyNode = getContainBlockyNode(mayNode);
//		if (!(blockyNode instanceof Block)) {
//			astNodeMarkMap.put(mayNode, "May");
//			if (blockyNode instanceof SwitchStatement) {
//				SwitchStatement switchStatement = (SwitchStatement) blockyNode;
//				List stmtList = switchStatement.statements();
//				ASTNode nearestSwitchCase = switchStatement; // init with switchstmt temporarily
//				for (int i = 0 ; i< stmtList.size(); i ++) {
//					if (stmtList.get(i) instanceof SwitchCase) {
//						SwitchCase switchCase = (SwitchCase) stmtList.get(i);
//						if (switchCase.getStartPosition() < mayNode.getStartPosition() && switchCase.getStartPosition() > nearestSwitchCase.getStartPosition()) {
//							nearestSwitchCase = switchCase;
//						}
//					}
//				}
//				astNodeMarkMap.put(nearestSwitchCase, "May");
//			}
//			if (mayNode instanceof MethodInvocation) {
//				return;
//			}
//			markMayNodeCoverage(astNodeMarkMap, mayNode.getParent(), rootEntryMethodNode);
//		} else {
//			Block parentBlock = (Block) blockyNode;
//			List stmtList = parentBlock.statements();
//			int markNodeIndex = getIndexOfRelatedStmtFromMarkNode(stmtList, mayNode);
//			if (markNodeIndex == -1) {
//				System.err.println("Cannot find node in statements!");
//			}
//			for (int i = 0; i <= markNodeIndex; i ++) {
//				Statement stmt = (Statement) stmtList.get(i);
//				
//				astNodeMarkMap.put(stmt, "May");
//			}
//			
//			for (int i = markNodeIndex +1; i < stmtList.size(); i ++) {
//				Statement stmt = (Statement) stmtList.get(i);
//				astNodeMarkMap.put(stmt, "May");
//				if (isStmtContainReturnStmt(stmt)) {
//					break;
//				}
//			}
//			if (parentBlock.getParent().equals(rootEntryMethodNode)) {
//				return;
//			}
//			markMayNodeCoverage(astNodeMarkMap, parentBlock.getParent(), rootEntryMethodNode);
//		}
	}
	
	private static boolean isStmtContainReturnStmt(ASTNode node) {
		ReturnVisitor visitor = new ReturnVisitor();
		node.accept(visitor);
		return visitor.containReturn;
	}
	
	private static ArrayList<ASTNode> extractReturnStmtInsideStmt(ASTNode node) {
		ReturnVisitor visitor = new ReturnVisitor();
		node.accept(visitor);
		return visitor.returnList;
	}
	
	private static int getIndexOfRelatedStmtFromMarkNode(List stmtList, ASTNode markNode) {
		for (int i = 0; i < stmtList.size(); i ++) {
			Statement currentStmt = (Statement) stmtList.get(i);
			int startPos = currentStmt.getStartPosition();
			int endPos = currentStmt.getStartPosition() + currentStmt.getLength() -1;
			if (markNode.getStartPosition() >= startPos &&
					markNode.getStartPosition() + markNode.getLength() - 1 <= endPos) {
				return i;
			}
		}
		
		return -1;
	}
	
	private static void calcualteCoverage(HashSet<String> logRegexSet,
			HashMap<String, ArrayList<ArrayList<InterProcNode>>> regexPathMap, InterProcNode mdNode,
			CompilationUnit cu, DefaultDirectedGraph<InterProcNode, DefaultEdge> callGraph,
			ArrayList<String> directLogInMethod) throws Exception {
		LogRegexMatcher logRegexMatcher = new LogRegexMatcher(logRegexSet);
		logRegexMatcher.filterInLogFile();
		HashMap<String,ArrayList<String>> matchedLogRegexAndItsMatchedLogStrMap = logRegexMatcher.getMatchedLogRegex();
		HashMap<String, ArrayList<InterProcNode>> matchedRegexAndCorrespondingMD = new HashMap<>();
		
	if (matchedLogRegexAndItsMatchedLogStrMap.isEmpty()) {
			// the method contains log regex, but none of the log regex can be
			// matched with generated logs. In this scenario, we can mark the logging
			// node as "MustNot" since the logging nodes are not executed. In addition,
			// the related nodes might be marked as "MustNot" as well by calling 
			//  markMustNotNodeCoverage.
			HashMap<ASTNode, String> nodeWithoutLogMatchingMarkMap = new HashMap<>();
			LinkedHashSet<ASTNode> allStmtSet = new LinkedHashSet<>();
			initASTMarkSet(mdNode.astNode, allStmtSet);
			FindLogVisitor lv = new FindLogVisitor(invokeHeuritics);
			mdNode.astNode.accept(lv);
			ArrayList<ASTNode> logList = lv.logPrintAndMIStmt;
			Iterator<ASTNode> iterator2 = allStmtSet.iterator();
			while(iterator2.hasNext()) {
				ASTNode nextNode = iterator2.next();
				nodeWithoutLogMatchingMarkMap.put(nextNode, "May");
			}
			for (ASTNode logNode : logList) {
				markMustNotNodeCoverage(nodeWithoutLogMatchingMarkMap, logNode, mdNode.astNode);
			}
			MethodNodeForOutput newEntryNode = new MethodNodeForOutput(cu,(MethodDeclaration)mdNode.astNode,mdNode.filePath);
			if (methodGlobalMarkMap.containsKey(newEntryNode)) {
				System.out.println("Log non-printing methods in globalmap already! --> " + newEntryNode);
			} else {
				methodGlobalMarkMap.put(newEntryNode, nodeWithoutLogMatchingMarkMap);
			}
			return;
		}
		
		// coverage for the entry method
		System.out.println("Matched Regex: " + matchedLogRegexAndItsMatchedLogStrMap.keySet());
		
		
		
		MethodNodeForOutput newEntryNode = new MethodNodeForOutput(cu,(MethodDeclaration)mdNode.astNode,mdNode.filePath);
		logger.info("Calculate coverage for root method {}", newEntryNode.toString());

		// type one method means methods with logging code and they 
		// are also matched with generated logs
		typeOneMethodList.add(newEntryNode);
		HashMap<ASTNode, String> nodeMarkMap =
				calcualteCoverageForOneMethod(matchedLogRegexAndItsMatchedLogStrMap,regexPathMap,cu,mdNode,callGraph,newEntryNode);
		// if this method already been visited before, merge current markmap
		// with previous markmap. If the node in globalmap markmap already marked
		// with "Must", nothing needs to be done. Else we can put local node which marked
		// as "May" or "Must" to the global map.
		if (methodGlobalMarkMap.containsKey(newEntryNode)) {
			HashMap<ASTNode, String> globalMarkMap = methodGlobalMarkMap.get(newEntryNode);
			for(ASTNode astNode : nodeMarkMap.keySet()) {
				ASTNode nodeInGlobal = astNode;
				if (!globalMarkMap.containsKey(astNode)) {
					for (ASTNode tmpnodeInGlobal:globalMarkMap.keySet()) {
						if (tmpnodeInGlobal.getStartPosition() == astNode.getStartPosition() 
								&& tmpnodeInGlobal.getLength() == astNode.getLength()) {
							nodeInGlobal = tmpnodeInGlobal;
							break;
						}
					}	
				}
				if (!globalMarkMap.containsKey(nodeInGlobal)) {
					if (nodeInGlobal instanceof MethodInvocation) {
						System.out.println("Node not found in Global Map dueto MI node");
						continue;
					}
				}
				if (globalMarkMap.get(nodeInGlobal).equals("Must")) {
					continue;
				} else if (nodeMarkMap.get(astNode).equals("May")){
					globalMarkMap.put(nodeInGlobal, nodeMarkMap.get(astNode));
				} else if (nodeMarkMap.get(astNode).equals("Must")) {
					globalMarkMap.put(nodeInGlobal, nodeMarkMap.get(astNode));
				}
			}
		} else {
			methodGlobalMarkMap.put(newEntryNode, nodeMarkMap);
		}
		
		// coverage for the methods called by entry methods
		
		HashMap<String, HashSet<InterProcNode>> regexAndItsMustExeMethodNodeMap = new HashMap<>();
		HashSet<InterProcNode> allMergedMustMdNode = new HashSet<>();
		for (String regex : matchedLogRegexAndItsMatchedLogStrMap.keySet()) {
			HashSet<InterProcNode> mustExeMethodNodeSet = findInvolvedMethodDeclarationProcNode(regex, regexPathMap,matchedRegexAndCorrespondingMD);
			regexAndItsMustExeMethodNodeMap.put(regex, mustExeMethodNodeSet);
			allMergedMustMdNode.addAll(mustExeMethodNodeSet);
		}
		String[] matchedLogRegexs = matchedLogRegexAndItsMatchedLogStrMap.keySet().toArray(new String[0]);
		for (int i = 0; i < matchedLogRegexs.length; i ++) {
			for (int j = i+1; j < matchedLogRegexs.length; j ++) {
				String regex1 = matchedLogRegexs[i];
				String regex2 = matchedLogRegexs[j];
				ArrayList<String> matchedLogSequence1 = matchedLogRegexAndItsMatchedLogStrMap.get(regex1);
				ArrayList<String> matchedLogSequence2 = matchedLogRegexAndItsMatchedLogStrMap.get(regex2);
				boolean allSeqInOneSubStringInTwo = true;
				for (String logSeq : matchedLogSequence1) {
					if (!isStringSubInAllStringInList(matchedLogSequence2, logSeq)) {
						allSeqInOneSubStringInTwo = false;
						break;
					}
				}
				if (allSeqInOneSubStringInTwo) {
					HashSet<InterProcNode> mdInterNodeSet1 = regexAndItsMustExeMethodNodeMap.get(regex1);
					HashSet<InterProcNode> mdInterNodeSet2 = regexAndItsMustExeMethodNodeMap.get(regex2);
					for(InterProcNode mdProcNode : mdInterNodeSet1) {
						if (!mdInterNodeSet2.contains(mdProcNode)) {
							allMergedMustMdNode.remove(mdProcNode);
						}
					}
					continue;
				}
				boolean allSeqInTwoIsSubStringInOne = true;
				for (String logSeq : matchedLogSequence2) {
					if (!isStringSubInAllStringInList(matchedLogSequence1, logSeq)) {
						allSeqInTwoIsSubStringInOne = false;
						break;
					}
				}
				if (allSeqInTwoIsSubStringInOne) {
					HashSet<InterProcNode> mdInterNodeSet1 = regexAndItsMustExeMethodNodeMap.get(regex1);
					HashSet<InterProcNode> mdInterNodeSet2 = regexAndItsMustExeMethodNodeMap.get(regex2);
					for(InterProcNode mdProcNode : mdInterNodeSet2) {
						if (!mdInterNodeSet1.contains(mdProcNode)) {
							allMergedMustMdNode.remove(mdProcNode);
						}
					}
					continue;
				}
			}
		}
		
		for (InterProcNode node : allMergedMustMdNode) {
			assert(node.astNode instanceof MethodDeclaration);
//			System.out.println(node.filePath);
//			System.out.println(node);
//			if (node.toString().contains("TYPE��MethodDeclaration,BceFundPoolService.java,150��173,false"))
//				System.out.println("debug");
//			if (node.filePath.contains("BceFundPoolService.java"))
//				System.out.println("debug");
//			if (node.filePath.contains("JsonConverter.java"))
//				System.out.println("holdon");
			MethodNodeForOutput newOutputNode = new MethodNodeForOutput(node.cu,(MethodDeclaration)node.astNode,node.filePath);
			
			System.out.printf("Method being called: %s , Matched by LogRegEx: %s \n", 
					newOutputNode.toString(),
					matchedLogRegexAndItsMatchedLogStrMap.keySet());

//			if (newOutputNode.toString().contains("syncPublishBceSupport:384"))
//				System.out.println("to know which methods are btw");
//			if (newOutputNode.toString().contains("getFinancePrice:147"))
//				System.out.println("debug");
//			if (newOutputNode.toString().contains("BillTaskEntry.java,setMessage:28"))
//				System.out.println("BTW method");
			HashMap<ASTNode, String> markMap = calcualteCoverageForOneMethod(matchedLogRegexAndItsMatchedLogStrMap, regexPathMap, node.cu, node, callGraph,newOutputNode);
			
			if (methodGlobalMarkMap.containsKey(newOutputNode)) {
//				if (newOutputNode.toString().contains("setMessage:28"))
//					System.out.println("Merge multiple local map to one cause problem");
				HashMap<ASTNode, String> globalMarkMap = methodGlobalMarkMap.get(newOutputNode);
				for(ASTNode astNode : markMap.keySet()) {
					ASTNode nodeInGlobal = astNode;
					if (!globalMarkMap.containsKey(astNode)) {
//						System.out.println("????");
						for (ASTNode tmpnodeInGlobal:globalMarkMap.keySet()) {
							if (tmpnodeInGlobal.getStartPosition() == astNode.getStartPosition() 
									&& tmpnodeInGlobal.getLength() == astNode.getLength()) {
								nodeInGlobal = tmpnodeInGlobal;
								break;
							}
						}	
					}
					if (globalMarkMap.get(nodeInGlobal).equals("Must")) {
						continue;
					} else if (markMap.get(astNode).equals("May")){
						globalMarkMap.put(nodeInGlobal, markMap.get(astNode));
					} else if (markMap.get(astNode).equals("Must")) {
//						System.out.println("BTW method change");
						globalMarkMap.put(nodeInGlobal, markMap.get(astNode));
					}
				}
			} else {
				methodGlobalMarkMap.put(newOutputNode, markMap);
			}
		}
		
	}
	
	private static HashMap<ASTNode, String> calcualteCoverageForOneMethod(HashMap<String,ArrayList<String>> matchedLogRegexAndItsMatchedLogStrMap,
			HashMap<String, ArrayList<ArrayList<InterProcNode>>> regexPathMap, CompilationUnit cu, InterProcNode mdNode, 
			DefaultDirectedGraph<InterProcNode, DefaultEdge> callGraph, MethodNodeForOutput methodNodeForOutput) {
		
		if (methodNodeForOutput.toString().contains("getFinancePrice")) {
			System.out.println("BTW method?");
		}
		
		HashMap<ASTNode, String> astNodeMergePathsMarkMap = new HashMap<>();
		
		HashMap<String, HashMap<ASTNode, String>> regexASTNodeMarkMapMap = new HashMap<>();
		
		for (String regex : matchedLogRegexAndItsMatchedLogStrMap.keySet()) {
			HashMap<ASTNode, String> astNodeOneRegexMap = findCommonPath(regex, regexPathMap, mdNode, astNodeMergePathsMarkMap, callGraph);
			regexASTNodeMarkMapMap.put(regex, astNodeOneRegexMap);
		}
		
		if (methodAndAllLocalMaps.containsKey(methodNodeForOutput)) {
			methodAndAllLocalMaps.get(methodNodeForOutput).putAll(regexASTNodeMarkMapMap);
		} else {
			methodAndAllLocalMaps.put(methodNodeForOutput, regexASTNodeMarkMapMap);
		}
		
		// How to merge the multiple nodes
		Iterator<String> tmpRegexIterator = regexASTNodeMarkMapMap.keySet().iterator();
		ArrayList<String> regexList = new ArrayList<>();
		while (tmpRegexIterator.hasNext()) {
			regexList.add(tmpRegexIterator.next());
		}
		
		// Check if regexList is empty to avoid IndexOutOfBoundsException
		if (regexList.isEmpty()) {
			System.err.println("Warning: No regex patterns found for method: " + methodNodeForOutput.toString());
			return astNodeMergePathsMarkMap;  // Return empty map
		}
		
		astNodeMergePathsMarkMap = new HashMap<>(regexASTNodeMarkMapMap.get(regexList.get(0)));
		
		for (ASTNode node : astNodeMergePathsMarkMap.keySet()) {
			String originLabel = astNodeMergePathsMarkMap.get(node);
			boolean hasMust = false;
			boolean hasMay = false;
			
			for (int i = 1; i < regexList.size(); i ++) {
				
				HashMap<ASTNode, String> tmpMark = regexASTNodeMarkMapMap.get(regexList.get(i));
				//TODO
				if (!tmpMark.containsKey(node))
					continue;
				if (tmpMark.get(node).equals("Must")) {
					hasMust = true;
				} else if (tmpMark.get(node).equals("May")) {
					hasMay = true;
				}
//				mergeTwoMarkMap(regexASTNodeMarkMapMap.get(regexList.get(i)), astNodeGloablMarkMap);
			}
			if (originLabel.equals("Must")) {
				continue;
			} else if (originLabel.equals("May")) {
				if (hasMust) {
					astNodeMergePathsMarkMap.put(node, "Must");
				}
			} else if(originLabel.equals("MustNot")) {
				if (hasMust) {
					astNodeMergePathsMarkMap.put(node, "Must");
				} else if(hasMay){
					astNodeMergePathsMarkMap.put(node, "May");
				}
			}
		}
		
		String[] matchedLogRegexs = matchedLogRegexAndItsMatchedLogStrMap.keySet().toArray(new String[0]);
		for (int i = 0; i < matchedLogRegexs.length; i ++) {
			for (int j = i+1; j < matchedLogRegexs.length; j ++) {
				String regex1 = matchedLogRegexs[i];
				String regex2 = matchedLogRegexs[j];
				ArrayList<String> matchedLogSequence1 = matchedLogRegexAndItsMatchedLogStrMap.get(regex1);
				ArrayList<String> matchedLogSequence2 = matchedLogRegexAndItsMatchedLogStrMap.get(regex2);
				boolean allSeqInOneSubStringInTwo = true;
				for (String logSeq : matchedLogSequence1) {
					if (!isStringSubInAllStringInList(matchedLogSequence2, logSeq)) {
						allSeqInOneSubStringInTwo = false;
						break;
					}
				}
				if (allSeqInOneSubStringInTwo) { 
					markNodeForPossibleSubRegex(regex1,regex2,regexASTNodeMarkMapMap.get(regex1), regexASTNodeMarkMapMap.get(regex2), astNodeMergePathsMarkMap);
					continue;
				}
				boolean allSeqInTwoIsSubStringInOne = true;
				for (String logSeq : matchedLogSequence2) {
					if (!isStringSubInAllStringInList(matchedLogSequence1, logSeq)) {
						allSeqInTwoIsSubStringInOne = false;
						break;
					}
				}
				if (allSeqInTwoIsSubStringInOne) {
					markNodeForPossibleSubRegex(regex2,regex1,regexASTNodeMarkMapMap.get(regex2), regexASTNodeMarkMapMap.get(regex1), astNodeMergePathsMarkMap);
					continue;
				}
			}
		}		
		return astNodeMergePathsMarkMap;
	}
	
	private static void mergeTwoMarkMap(HashMap<ASTNode, String> newMap, HashMap<ASTNode, String> resultMap) {
		
		for (ASTNode node : resultMap.keySet()) {
			if (newMap.get(node).equals("Must")) {
				resultMap.put(node, "Must");
			} else if (newMap.get(node).equals("May")) {
				if (resultMap.get(node).equals("MustNot")) {
					System.out.println("During merge, resultMap is MustNot and newMap is May");
					resultMap.put(node, "May");
				}
			} 
		}
	}
	
	private static boolean isStringSubInAllStringInList(ArrayList<String> list, String str) {
		boolean result = true;
		for(String s :list ) {
			if(s.length() < str.length())
				return false;
		}
		for (String s : list) {
			if (!s.contains(str)) {
				return false;
			}
		}
		return result;
	}
	
	private static void markNodeForPossibleSubRegex(String r1, String r2, HashMap<ASTNode, String> localMap1, HashMap<ASTNode, String> localMap2, HashMap<ASTNode, String> globalMap) {
		for (ASTNode node : localMap1.keySet()) {
			if(localMap1.get(node).equals("Must")) {
				if (!localMap2.get(node).equals("Must")) {
//					System.out.println("Node being changed from Must/not to May");
//					System.out.println("#########################");
//					System.out.println(node);
//					System.out.println("#########################");
					globalMap.put(node, "May");
				}
			}
		}
	}
	
	
	private static ASTNode getContainBlockyNode(ASTNode node) {
		ASTNode result = node;
		
		if (node instanceof MethodDeclaration)
			return null;
		
		try {
		while (true) {
		
			if (result instanceof Block || result instanceof IfStatement ||result instanceof SwitchStatement || result instanceof ForStatement
					|| result instanceof EnhancedForStatement || result instanceof WhileStatement || result instanceof WhileStatement) {
				return result;
			}
			result = result.getParent();
		}} catch (Exception e){
			e.printStackTrace();
		}
		return result;
	}

	
	private static void processMethod (DefaultDirectedGraph<InterProcNode, DefaultEdge> callGraph, InterProcNode interProcMethodVertex,
			CompilationUnit currentCU, HashMap<String, CompilationUnit> visitedCUMap, String filePath) {
		TypeDeclaration tdNode = null;
		MethodDeclaration methodASTNode = (MethodDeclaration) interProcMethodVertex.astNode;
		if (methodASTNode.getParent() instanceof TypeDeclaration) {
			tdNode = (TypeDeclaration) methodASTNode.getParent();
		}
		if (methodASTNode == null || methodASTNode.getBody() == null) // empty body in interface
			return;
		List stmtList = methodASTNode.getBody().statements();
		for(int i = 0; i < stmtList.size(); i ++) {
			Statement stmt = (Statement)stmtList.get(i);
			processStatement(stmt, callGraph, interProcMethodVertex, tdNode, currentCU, visitedCUMap, filePath);
		}
	}
	
	private static void processStatement(Statement stmt, DefaultDirectedGraph<InterProcNode, DefaultEdge> callGraph,
			InterProcNode interProcVertex, TypeDeclaration tdNode, CompilationUnit cu, HashMap<String, CompilationUnit> visitedCUMap,
			String filePath) {
		if (stmt == null)
			return;
		if (stmt.getNodeType() == ASTNode.IF_STATEMENT) {
			IfStatement ifStatement = (IfStatement) stmt;
//			if(ifStatement.toString().contains("chargeStatus == ChargeStatus.PART"))
//				System.out.println("debug");
			InterProcNode newNode = new InterProcNode(cu,filePath,ifStatement);
			callGraph.addVertex(newNode);
			callGraph.addEdge(interProcVertex, newNode);
			processStatement(ifStatement.getThenStatement(), callGraph, newNode, tdNode, cu, visitedCUMap, filePath);
			processStatement(ifStatement.getElseStatement(), callGraph, newNode, tdNode, cu, visitedCUMap, filePath);
		} else if(stmt instanceof TryStatement) {
			TryStatement tryStmt = (TryStatement) stmt;
			InterProcNode newNode = new InterProcNode(cu,filePath,tryStmt);
			callGraph.addVertex(newNode);
			callGraph.addEdge(interProcVertex, newNode);
			processStatement(tryStmt.getBody(), callGraph, newNode, tdNode, cu, visitedCUMap,filePath);
			//TODO add catch?
			processStatement(tryStmt.getFinally(), callGraph, newNode, tdNode, cu, visitedCUMap,filePath);
		} else if(stmt instanceof Block) {
			Block blockStmt = (Block) stmt;
			InterProcNode newNode = new InterProcNode(cu,filePath,blockStmt);
			callGraph.addVertex(newNode);
			callGraph.addEdge(interProcVertex, newNode);
			for( int i = 0; i < blockStmt.statements().size(); i ++) {
				Statement stmtInBlock = (Statement) blockStmt.statements().get(i);
				processStatement(stmtInBlock, callGraph, newNode, tdNode, cu, visitedCUMap,filePath);
			}
		} else if(stmt instanceof SwitchStatement) {
			SwitchStatement switchStatement = (SwitchStatement) stmt;
			InterProcNode newNode = new InterProcNode(cu,filePath,switchStatement);
			callGraph.addVertex(newNode);
			callGraph.addEdge(interProcVertex, newNode);
			for ( int i = 0; i <  switchStatement.statements().size(); i ++) {
				Statement stmtInSwitch = (Statement) switchStatement.statements().get(i);
				processStatement(stmtInSwitch, callGraph, newNode, tdNode, cu, visitedCUMap,filePath);
			}
		} else if(stmt instanceof EnhancedForStatement) {
			EnhancedForStatement enhancedForStatement = (EnhancedForStatement) stmt;
			InterProcNode newNode = new InterProcNode(cu,filePath,enhancedForStatement);
			callGraph.addVertex(newNode);
			callGraph.addEdge(interProcVertex, newNode);
			processStatement(enhancedForStatement.getBody(), callGraph, newNode, tdNode, cu, visitedCUMap, filePath);
		} else if(stmt instanceof WhileStatement) {
			WhileStatement whileStatement = (WhileStatement) stmt;
			InterProcNode newNode = new InterProcNode(cu,filePath,whileStatement);
			callGraph.addVertex(newNode);
			callGraph.addEdge(interProcVertex, newNode);
			processStatement(whileStatement.getBody(), callGraph, newNode, tdNode, cu, visitedCUMap, filePath);
		} else if(stmt instanceof ForStatement) {
			ForStatement forStmt = (ForStatement) stmt;
			InterProcNode newNode = new InterProcNode(cu,filePath,forStmt);
			callGraph.addVertex(newNode);
			callGraph.addEdge(interProcVertex, newNode);
			processStatement(forStmt.getBody(), callGraph, newNode, tdNode, cu, visitedCUMap,filePath);
		} else if(stmt instanceof BreakStatement) {
			BreakStatement breakStatement = (BreakStatement) stmt;
			InterProcNode newNode = new InterProcNode(cu,filePath,breakStatement);
			callGraph.addVertex(newNode);
			callGraph.addEdge(interProcVertex, newNode);
		} else if(stmt instanceof SynchronizedStatement) {
			SynchronizedStatement synchronizedStatement = (SynchronizedStatement) stmt;
			InterProcNode newNode = new InterProcNode(cu,filePath,synchronizedStatement);
			callGraph.addVertex(newNode);
			callGraph.addEdge(interProcVertex, newNode);
			processStatement(synchronizedStatement.getBody(),
					callGraph, interProcVertex, tdNode, cu, visitedCUMap, filePath);
		} else {
			if (stmt instanceof ExpressionStatement) {
				ExpressionStatement exStmt = (ExpressionStatement) stmt;
				if (FileUtils.ifLogPrinting(exStmt.toString())) {
					
					if (!exStmt.toString().toLowerCase().contains("debug(") && 
							!exStmt.toString().toLowerCase().contains("trace(")) {
						
					InterProcNode newNode = new InterProcNode(cu,filePath,exStmt);
					newNode.isLog = true;
					callGraph.addVertex(newNode); 
					callGraph.addEdge(interProcVertex, newNode); 
					return;
					}
				}
			}
			
			if(stmt instanceof ReturnStatement) {
				ReturnStatement returnStatement = (ReturnStatement) stmt;
				InterProcNode newReturnNode = new InterProcNode(cu, filePath, returnStatement);
				callGraph.addVertex(newReturnNode);
				callGraph.addEdge(interProcVertex, newReturnNode);
				interProcVertex = newReturnNode;
			}	
				
			MIVisitor miVisitor = new MIVisitor();
			stmt.accept(miVisitor);
			for(int i = miVisitor.bindingList.size()-1; i >= 0; i --) {
				// the method call is external (cannot be resolved to a type)
				if (miVisitor.bindingList.get(i) == null) {
					continue;
				} else {
					IMethodBinding binding = miVisitor.bindingList.get(i);
					MethodInvocation mi = miVisitor.miList.get(i);
					
					String declarationClassQualifiedName = binding.getDeclaringClass().getQualifiedName();
					String declarationMethodKey = binding.toString();
					
					// if the method call is from the same class
					if (tdNode == null || tdNode.resolveBinding() == null)
						continue;
					if (declarationClassQualifiedName.equals(tdNode.resolveBinding().getQualifiedName())) {
						for (MethodDeclaration md : tdNode.getMethods()) {
							IMethodBinding tempMdBinding = md.resolveBinding();
							if (md.resolveBinding() == null)
								continue;
							if (tempMdBinding.toString().equals(declarationMethodKey)) {
								
								InterProcNode newMINode = new InterProcNode(cu,filePath,mi);
								callGraph.addVertex(newMINode);
								DefaultEdge edge = callGraph.addEdge(interProcVertex, newMINode); // cycle happens here
								
								CycleDetector<InterProcNode, DefaultEdge> cycleDetector = new CycleDetector<>(callGraph);
								if (cycleDetector.detectCycles()) {
									Set<InterProcNode> nodes = cycleDetector.findCycles();
									callGraph.removeEdge(edge);
									break;
								}								
								InterProcNode newMDNode = new InterProcNode(cu,filePath,md);
								newMDNode.invokeMD = mi;
								
//								if(callGraph.containsVertex(newMDNode)) {
//									System.out.println(callGraph.incomingEdgesOf(newMDNode));
//								}
								
								callGraph.addVertex(newMDNode);
//								if (callGraph.outgoingEdgesOf(newMDNode).size()!=0)
//									System.out.println("crap!");
								DefaultEdge edgeFromMIToMd = callGraph.addEdge(newMINode, newMDNode);
								
								
								processMethod(callGraph, newMDNode, cu, visitedCUMap, filePath);
								break;
							}
						}
					} else if(declarationClassQualifiedName.startsWith(invokeHeuritics)) {
						// if the method is from the other class in the same project
						// get the MethodDeclaration of this method
						if (qualifyClassNameFilePathMap.containsKey(declarationClassQualifiedName)) {
							String callClassFilePath = qualifyClassNameFilePathMap.get(declarationClassQualifiedName);
							String callMethod = binding.toString();
							CompilationUnit callMethodCU;
							if (visitedCUMap.containsKey(callClassFilePath)) {
								callMethodCU = visitedCUMap.get(callClassFilePath);
							} else {
								callMethodCU = getResolvedCUFromFilePath(callClassFilePath);
								visitedCUMap.put(callClassFilePath, callMethodCU);
							}
//							if(binding.getName().contains("serialize"))
//								System.out.println("debug");
//							System.out.println(callClassFilePath);
							MethodDeclaration md = getMethodDeclarationNodeFromCU(callMethodCU, callMethod, binding.getName());
							if (md == null || md.getName() == null) {
//								System.err.println(md);
								continue;
							}
							// first add mi in the graph, then add 
							InterProcNode newMINode = new InterProcNode(cu,filePath,mi);
							callGraph.addVertex(newMINode);
							DefaultEdge edge = callGraph.addEdge(interProcVertex, newMINode);
							
							
							InterProcNode newMDNode = new InterProcNode(callMethodCU,callClassFilePath, md);
							newMDNode.invokeMD = mi;
//							if (md == null || md.getName() == null) {
//								System.err.println(md);
//								continue;
//							}
							
//							if (newMDNode.astNode == null)
//								System.out.println("shit");
//							if (callGraph.containsVertex(newMDNode)) {
//								System.out.println("shooo");
//							}
							
							callGraph.addVertex(newMDNode);
							DefaultEdge edgeFromMIToMd =  callGraph.addEdge(newMINode, newMDNode);
							
							
							
//							processMethod(callGraph, newMDNode, callMethodCU, visitedCUMap, callClassFilePath);

							CycleDetector<InterProcNode, DefaultEdge> cycleDetector = new CycleDetector<>(callGraph);
							try {
								if (cycleDetector.detectCycles()) {
									callGraph.removeEdge(edgeFromMIToMd);
									continue;
								} else {
									processMethod(callGraph, newMDNode, callMethodCU, visitedCUMap, callClassFilePath);
								}
							} catch (Exception e) {
								e.printStackTrace();
							}
							
						}
						
					}
				}
			}
			
			
			
		}
	
	}
	
	public static CompilationUnit getResolvedCUFromFilePath(String filePath) {
		logger.debug("Getting the dependent files from import componenets");
		try {
			Map options = JavaCore.getOptions();
			options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_1_8);
			ASTParser astParser = ASTParser.newParser(AST.JLS8);
			astParser.setKind(ASTParser.K_COMPILATION_UNIT);
			String fs = FileUtils.getFileString(filePath);
			astParser.setCompilerOptions(options);
			astParser.setSource(fs.toCharArray());
			CompilationUnit cu = (CompilationUnit) astParser.createAST(null);
			List importList = cu.imports();
			
			
			astParser = ASTParser.newParser(AST.JLS8);
			astParser.setKind(ASTParser.K_COMPILATION_UNIT);
			astParser.setCompilerOptions(options);
			astParser.setSource(fs.toCharArray());
			HashSet<String> dependentModuleDir = getDependentModuleDir(importList);
			String[] srcPathEntries = dependentModuleDir.toArray(new String[dependentModuleDir.size()]);
			
			for (String srcPathEntry : srcPathEntries) {
				logger.debug("srcPathEntry passed to JDT {}", srcPathEntry);
			}
			
			String unitName = FileUtils.extractUnitnameFromAbsFilePath(filePath);
			logger.debug("unitName passed to JDT {}", unitName);

			String[] classPathEntries = {jreLibPath};
//			String[] srcPathEntries = {"D:\\bce-plat\\finance\\fp-charging-v2\\src\\main\\java",
//									   "D:\\bce-plat\\finance\\fp-fundpool-biz\\src\\main\\java", 
//									   "D:\\bce-plat\\finance\\fp-charging-base\\src\\main\\java",
//									   "D:\\bce-plat\\common\\fbi-common-client\\src\\main\\java"};
			String[] encodeArray = new String[srcPathEntries.length];
			Arrays.fill(encodeArray, "UTF-8");
			astParser.setUnitName(unitName);
			astParser.setEnvironment(classPathEntries, srcPathEntries,encodeArray, true);
			astParser.setResolveBindings(true);
			astParser.setBindingsRecovery(true);
			astParser.setSource(fs.toCharArray());
			cu = (CompilationUnit) astParser.createAST(null);
			return cu;
			
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		
		return null;
	}
	
	private static MethodDeclaration getMethodDeclarationNodeFromCU(CompilationUnit cu, String methodBindingStr, String methodName) {
		MDVisitor visitor = new MDVisitor();
		cu.accept(visitor);
		return visitor.getMDFromBinding(methodBindingStr, methodName);
	}
	
	public static HashSet<String> getDependentModuleDir(List importList) {
//		List importList = cu.imports();
		HashSet<String> results = new HashSet<>();
		for (int i = 0; i < importList.size(); i ++) {
			ImportDeclaration importDeclaration = (ImportDeclaration)importList.get(i);
//			System.out.println(importDeclaration.getName().getFullyQualifiedName());
			String importStr = importDeclaration.getName().getFullyQualifiedName();
			if (importStr.startsWith(invokeHeuritics)) {
				
				if (qualifyClassNameFilePathMap.containsKey(importStr)) {
					results.add(FileUtils.extractSrcEntryFromAbsFilePath(qualifyClassNameFilePathMap.get(importStr)));
				}
				else {
//					System.out.println("import cannot find " + importStr);
				}
//				boolean findCorrespondingClassFile = false;
//				String relativePath = Joiner.on("\\").join(importStr.split("\\."));
//				relativePath = "\\src\\main\\java\\" + relativePath;
//				File rootPathDir = new File(projectRootPath);
//				for(File f: rootPathDir.listFiles()) {
//					Path p = Paths.get(f.getAbsolutePath(), relativePath+".java");
//					String path = p.toString();
//					File dependentFile = new File(path);
//					if (dependentFile.exists()) {
//						findCorrespondingClassFile = true;
//						results.add(f.getAbsolutePath()+"\\src\\main\\java");					}
//				}
//				if (!findCorrespondingClassFile) {
//				}
			}
		}
//		System.out.println(results);
		return results;
	}
	
	private static TreeMap<Integer, String> calculateCoverageMap(HashMap<ASTNode, String> markMap, MethodNodeForOutput mdNodeInfo) {
		TreeMap<Integer, String> lineCoverageMap = new TreeMap<>();
		CompilationUnit cu = mdNodeInfo.cu;
		for (ASTNode astNode : markMap.keySet()) {
			String coverStatus = markMap.get(astNode);
			if (astNode instanceof IfStatement || astNode instanceof SwitchStatement || astNode instanceof ForStatement
					|| astNode instanceof EnhancedForStatement || astNode instanceof WhileStatement 
					|| astNode instanceof TryStatement || astNode instanceof SynchronizedStatement) {
				int startPositionOfBlock = astNode.getStartPosition() + astNode.toString().indexOf("{");
				int blockLine = cu.getLineNumber(startPositionOfBlock);
				int statLine = cu.getLineNumber(astNode.getStartPosition());
				for (int i = statLine; i <= blockLine; i ++) {
					lineCoverageMap.put(i, coverStatus);
				}

				
			} else if (astNode instanceof Block) {
				if (astNode.getParent() instanceof TryStatement) {
					TryStatement parentTry = (TryStatement) astNode.getParent();
					if (parentTry.getFinally() != null && parentTry.getFinally().equals(astNode)) {
						int startLine = cu.getLineNumber(astNode.getStartPosition());
						lineCoverageMap.put(startLine, coverStatus);
					}
				}
			} else {
				if (astNode instanceof CatchClause)
					continue;
				int startLine = cu.getLineNumber(astNode.getStartPosition());
				int endLine = cu.getLineNumber(astNode.getStartPosition() + astNode.getLength() - 1);
				for (int lineNumber = startLine; lineNumber <= endLine; lineNumber ++) {
					lineCoverageMap.put(lineNumber, coverStatus);
				}
			}
		}
		return lineCoverageMap;
	}
	
	public static void traverse(File node) throws Exception {
		if(node.isFile() && node.getName().endsWith(".java")) {
			if (!node.getAbsolutePath().contains("test") && node.getAbsolutePath().contains("src"+File.separator+"main"+File.separator+"java")) 
				allFiles.add(node.getAbsolutePath());
//			parse(node.getAbsolutePath());
		} else if(node.isDirectory()) {
			for(File f: node.listFiles()) {
				traverse(f);
			}
		}
	}
}


class MIVisitor extends ASTVisitor {
	
	LinkedList<IMethodBinding> bindingList = new LinkedList<>();
	
	LinkedList<MethodInvocation> miList = new LinkedList<>();
	
	@Override
	public boolean visit(MethodInvocation node) {
		IMethodBinding binding = node.resolveMethodBinding();
		
		bindingList.add(binding);
		miList.add(node);
		
		if (binding != null) {
			if (binding.getDeclaringClass() != null) {
		}
//			System.out.printf("%s\n%s\n", binding.getDeclaringClass().getQualifiedName(), binding.getMethodDeclaration());
//		} else {
			
		} 
		// consider multiple call ?
		return true;
	}
	
//	class MIBindingInfo {
//		String classQualifiedName;
//		String methodWithSig;
//	}
}

class MDVisitor extends ASTVisitor {
	
	
	HashMap<String, MethodDeclaration> bindingStrMethodMap = new HashMap<>();
	
	HashMap<String, MethodDeclaration> methodNameASTNodeMap = new HashMap<>();
	
	HashMap<String, ArrayList<String>> methodNameBindinglistMap = new HashMap<>();
	
	@Override
	public boolean visit(MethodDeclaration node) {
		methodNameASTNodeMap.put(node.getName().toString(), node);
		if (node.resolveBinding() == null) {
//			System.out.println(node.getName());
			return false;
		}
		if (methodNameBindinglistMap.containsKey(node.getName().toString())) {
			methodNameBindinglistMap.get(node.getName().toString()).add(node.resolveBinding().toString());
		} else {
			
			methodNameBindinglistMap.put(node.getName().toString(), new ArrayList<>(Arrays.asList(node.resolveBinding().toString())));
		}
		
		
		
		bindingStrMethodMap.put(node.resolveBinding().toString(), node);
		
		
		
		return false;
	}
	
	public MethodDeclaration getMDFromBinding(String bindingStr, String methodName) {
		if (bindingStrMethodMap.containsKey(bindingStr)) {
			return bindingStrMethodMap.get(bindingStr);
		} else {
			int parameterCount = bindingStr.length() - bindingStr.replace(",", "").length() + 1;
			
			ArrayList<String> bindingList = methodNameBindinglistMap.get(methodName);
			for (String binding : bindingList) {
				if ((binding.length()-binding.replace(",", "").length() + 1) == parameterCount)
					return bindingStrMethodMap.get(binding);
			}
			
//			if (methodNameASTNodeMap.containsKey(methodName))
//				return methodNameASTNodeMap.get(methodName);
			return null;
		}
	}
}

class ReturnVisitor extends ASTVisitor {

	boolean containReturn = false;

	ArrayList<ASTNode> returnList = new ArrayList<>();
	@Override
	public boolean visit(ReturnStatement node) {
		returnList.add(node);
		containReturn = true;
		return false;
	}

	public ArrayList<ASTNode> getReturnNodeList() {
		return this.returnList;
	}
}



