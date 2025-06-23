package ca.yorku.mustnot;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import ca.yorku.nemo.main.FileUtils;
import jmetal.metaheuristics.moead.pMOEAD;


public class ExtractMustNotMethods {
	
	static String invokeHeuristics = "org.apache.hadoop";
	static String jreLibPath = "/Library/Java/JavaVirtualMachines/jdk1.8.0_111.jdk/Contents/Home/jre/lib/rt.jar";
	static HashMap<String, String> qualifyClassNameFilePathMap = new HashMap<>();

	static HashMap<String, HashSet<String>> methodAndItsInvokeMethods = new HashMap<>();
	static HashMap<String, HashSet<String>> methodAndItsCallers = new HashMap<>();
	
	public static void main(String[] args) {
		
		
//		String invokeMethodFilePath = "invoke_methods_enhanced.txt";
//		String coverageResultPath = "/Users/nemo/workspace/LogSug/new_test_ycsb_for_verify_purpose/coverage_matrix.csv";
//		String logContainMethodFilePath = "log_containing_methods.txt";
//		String qualifyNameFileInfoPath = "qualifyname_filepath.txt";
//		String jacocoResults = "new_test_ycsb_for_verify_purpose/cov_info.xml";
		
		jreLibPath = args[0];
		invokeHeuristics = args[1];
		String invokeMethodFilePath = args[2];
		String coverageResultPath = args[3];
		String logContainMethodFilePath = args[4];
		String qualifyNameFileInfoPath = args[5];
		String jacocoResults = args[6];
		String fileFilterStr = args[7]; // == "hbase-server"
		String outputInitialMustNotMethods = args[8];  // == "initial method str" 
		
		
		try (BufferedReader br = new BufferedReader(new FileReader(qualifyNameFileInfoPath))) {
			String line = null;
			while((line = br.readLine()) != null) {
				String[] results = line.split(",");
				qualifyClassNameFilePathMap.put(results[0], results[1]);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		
		
		
		try (BufferedReader br = new BufferedReader(new FileReader(invokeMethodFilePath))) {
			String line;
		    while ((line = br.readLine()) != null) {
		    	String[] splits = line.split("\\t");
		    	if(splits.length < 4)
		    		continue;
		    	String filePath = splits[0];
		    	int numOfInvokeMethods = Integer.parseInt(splits[1]);
		    	String callMethodBinding = splits[2];
		    	String invokedMethodBindings = splits[3];
		    	if (invokedMethodBindings.split(";").length == 0) {
		    		continue;
		    	}
		    	HashSet<String> invokedMethodBindingSet = new HashSet<>();
		    	StringTokenizer st = new StringTokenizer(invokedMethodBindings, ";");
		    	while(st.hasMoreTokens()) {
		    		String invokedMethodBinding = st.nextToken();
		    		invokedMethodBindingSet.add(invokedMethodBinding);
		    		if (methodAndItsCallers.containsKey(invokedMethodBinding)) {
		    			methodAndItsCallers.get(invokedMethodBinding).add(filePath+"|||||"+callMethodBinding);
		    		} else {
		    			HashSet<String> nSet = new HashSet<>();
		    			nSet.add(filePath+"|||||"+callMethodBinding);
		    			methodAndItsCallers.put(invokedMethodBinding, nSet);
		    		}
		    	}
		    	
		    	methodAndItsInvokeMethods.put(filePath+"|||||"+callMethodBinding, invokedMethodBindingSet);
		    }
		} catch (Exception e) {
			
			e.printStackTrace();
		}
		
		
		// step 1 get all must log methods;
		ArrayList<String> mustMethods = new ArrayList<>();
		try (BufferedReader br = new BufferedReader(new FileReader(coverageResultPath))) {
		    String line;
		    while ((line = br.readLine()) != null) {
		       String filePath = line.split(",")[0];
		       String methodIdentifier = line.split(",")[1];
//		       System.out.println(line);
		       int lineNumber = Integer.parseInt(methodIdentifier.split(":")[1]);
		       String methodBinding = "";
		       CompilationUnit cu = getResolvedCUFromFilePath(filePath);
		       if (cu == null) {
	    			System.err.println("Cannot parse file: " + filePath);
					continue;
	    		}
		       MethodVisitor visitor = new MethodVisitor(cu);
		       cu.accept(visitor);
		       if (visitor.lineNumberAndMethodBindingMap.containsKey(lineNumber)) {
		    	   String binding = visitor.lineNumberAndMethodBindingMap.get(lineNumber);
		    	   mustMethods.add(binding);
		       } else {
//		    	   System.err.println("Cannot find binding! " + line);
		       }
		    }
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		// step 2: get all log methods binding
		HashSet<String> mustNotMethodSet = new HashSet<>(); 
		
		try (BufferedReader br = new BufferedReader(new FileReader(logContainMethodFilePath))) {
			String line;
		    while ((line = br.readLine()) != null) {
		    	String filePath = line.split("\\t")[0];
		    	String binding = line.split("\\t")[1];
		    	
//		    	if (!filePath.contains("/Users/nemo/hbase-1.2.6/hbase-server/src/main/java/org/apache/hadoop/hbase/wal/WALPrettyPrinter.java"))
		    	
		    	
		    	Pattern p = Pattern.compile(fileFilterStr);
		    	Matcher matcher = p.matcher(filePath);
		    	if (!matcher.find()) {
		    		continue;
		    	}
		    	
		    	if (!mustMethods.contains(binding)) {
		    		// must-not LM candidates
		    		// if logs are all in exceptions, then it is not a must-not
		    		// if at least one log is not in exception/for/if/while/switch, etc. the method is a must-not
		    		// test if the initial must-not sets are correct compared to Jacoco results
//		    		System.out.println(filePath + "," +binding);
		    		CompilationUnit cu = getResolvedCUFromFilePath(filePath);
		    		if (cu == null) {
		    			System.err.println("Cannot parse file: " + filePath);
						continue;
		    		}
		    		MethodVisitor mv = new MethodVisitor(cu);
		    		cu.accept(mv);
		    		if (mv.methodBindingAndMethodNodeMap.containsKey(binding)) {
		    			MethodDeclaration md = mv.methodBindingAndMethodNodeMap.get(binding);
		    			int startLine = cu.getLineNumber(md.getStartPosition());
		    			int endLine = cu.getLineNumber(md.getStartPosition() + md.getLength() - 1);
		    			
		    			LogVisitor lv = new LogVisitor();
		    			md.accept(lv);
		    			boolean isMethodMustNot = true;
		    			// we changed the log extraction rule here to
		    			// bypass the debug and trace and console outputs
		    			if (lv.logExpNodeList.size() == 0) 
		    				isMethodMustNot = false;
		    			for (ASTNode logNode : lv.logExpNodeList) {
		    				ArrayList<ASTNode> parentNodeList = getParentNodeList(logNode);
		    				if (isLogInControlNodes(parentNodeList)) {
		    					isMethodMustNot = false;
		    				}
		    			}
		    			if (isMethodMustNot) {
		    				mustNotMethodSet.add(filePath+"|||||"+binding); // + "|||||" + md.getName().toString() + "|||||"
		    						 // + startLine + "|||||" + endLine);
		    			}
		    		}
		    	}
		    }
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		
		
		try (PrintWriter writer = new PrintWriter(new BufferedWriter(
				new FileWriter(outputInitialMustNotMethods, true)))) {
			Iterator<String> iterator = mustNotMethodSet.iterator();
			while (iterator.hasNext()) {
				writer.println(iterator.next());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
//		System.out.println("Must Not method size:");
		

//		String opt = "initial";
		HashSet<String> initialMustNotSet = new HashSet<>();
		initialMustNotSet = compareWithJacocoResults(jacocoResults, mustNotMethodSet, "initial");
		
		System.out.println("---------------------------");
		
		HashSet<String> finalMustNotMethodSet = new HashSet<>();
		extractMustNotMethods(finalMustNotMethodSet, initialMustNotSet);
		compareWithJacocoResults(jacocoResults, finalMustNotMethodSet, "final");
		
//		try (PrintWriter writer = new PrintWriter(new BufferedWriter(
//				new FileWriter(outputInitialMustNotMethods, true)))) {
//			writer.println("---------------------------------------------");
//			Iterator<String> iterator = finalMustNotMethodSet.iterator();
//			while (iterator.hasNext()) {
//				writer.println(iterator.next());
//			}
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
		
	}
	
	
	public static HashSet<String> compareWithJacocoResults(String covXml, HashSet<String> finalMustNotMethodSet, String opt) {
		
		HashSet<String> adaptedInitialMethodSet = new HashSet<>();
		
		HashMap<String, ArrayList<Element>> fileNameElemListMap = new HashMap<>();
		int unCoverJacocoMustNot = 0;
		int coverJacocoMustNot = 0;
		try {
			File fXmlFile = new File(covXml);
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document doc;
			doc = dBuilder.parse(fXmlFile);
			Element rootElem = doc.getDocumentElement();
			NodeList fileNodeList = doc.getElementsByTagName("file");
			for (int i = 0; i < fileNodeList.getLength(); i ++) {
				Element fileElem = (Element)fileNodeList.item(i);
				String fileName = fileElem.getAttribute("name");
				if (fileNameElemListMap.containsKey(fileName)) {
					fileNameElemListMap.get(fileName).add(fileElem);
				} else {
					ArrayList<Element> nList = new ArrayList<>();
					nList.add(fileElem);
					fileNameElemListMap.put(fileName, nList);
				}
			}
			Iterator<String> iterator = finalMustNotMethodSet.iterator();
			while (iterator.hasNext()) {
				String methodKey = iterator.next();
				Element methodElem = findCorrectMethodElement(methodKey, fileNameElemListMap);
				if (methodElem != null) {
					Element linesElem = (Element)methodElem.getElementsByTagName("lines").item(0);
					if (linesElem.getAttribute("coveredline").equals("0")) {
						unCoverJacocoMustNot ++;
						System.out.println("Result correct: " + methodKey);
						adaptedInitialMethodSet.add(methodKey);
					}
					else {
						System.out.println("LogSug considers must-not, but Jacoco shows covered: " + methodKey);
						coverJacocoMustNot ++;
					}
				} else {
					unCoverJacocoMustNot ++;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		if (opt.equals("initial")) {
//			finalMustNotMethodSet = adaptedInitialMethodSet;
			System.out.println("Initial must-not methods #: " + finalMustNotMethodSet.size());
			return adaptedInitialMethodSet;
		}
		else {
			System.out.println("Must-not methods are not covered by Jacoco: " + unCoverJacocoMustNot);
			System.out.println("Must-not methods are covered by Jacoco: " + coverJacocoMustNot);
			return finalMustNotMethodSet;
		}

	}
	
	public static Element findCorrectMethodElement(String methodKey,
			HashMap<String, ArrayList<Element>> fileNameElemListMap) {
		String filePath = methodKey.split("\\|\\|\\|\\|\\|")[0];
		String methodBinding = methodKey.split("\\|\\|\\|\\|\\|")[1];
		Pattern p = Pattern.compile("\\s(\\w+)\\(");
		Matcher m = p.matcher(methodBinding);
		String methodName="";
		if (m.find()) {
			methodName = m.group(1);
		}
//		String methodName = methodKey.split("\\|\\|\\|\\|\\|")[2];
//		int startLine = Integer.parseInt(methodKey.split("\\|\\|\\|\\|\\|")[3]);
//		int EndLine = Integer.parseInt(methodKey.split("\\|\\|\\|\\|\\|")[4]);
		
		
		String splitRegex;
		if (File.separator.equals("\\")) {
			splitRegex = "\\\\";
		} else {
			splitRegex = File.separator;
		}
		String fileName = filePath.split(splitRegex)[filePath.split(splitRegex).length-1];
		ArrayList<Element> fileElemList = fileNameElemListMap.get(fileName);
		
		if (fileElemList == null) {
			return null;
		}
		
		Element correctFileElem = null;
		if (fileElemList.size() == 1) {
			correctFileElem = fileElemList.get(0);
		} else {
			for (Element elem : fileElemList) {
				Element packageNode = (Element)elem.getParentNode();
				String packageName = packageNode.getAttribute("name");
				if (filePath.replaceAll("\\W", "").contains(packageName.replaceAll("\\W", ""))) {
					correctFileElem = elem;
					break;
				}
			}
		}
		if (correctFileElem == null) {
			return null;
		}
		
		
		NodeList methodElemList = correctFileElem.getElementsByTagName("method");
		
		for (int i = 0; i < methodElemList.getLength(); i ++) {
			Element methodElem = (Element) methodElemList.item(i);
			String mName = methodElem.getAttribute("name");
			if (methodName.equals(mName)) {
				return methodElem;
//				Element firstLineElem = (Element)((Element)methodElem.getElementsByTagName("lines").item(0)).getElementsByTagName("line").item(0);
//				int lineNumber = Integer.parseInt(firstLineElem.getAttribute("number"));
//				if (lineNumber >= startLine && lineNumber <= EndLine)
//					return methodElem;
			}
		}
		
		return null;
		
	}
	
	public static void extractMustNotMethods(HashSet<String> mustNotMethodSet, HashSet<String> newMustNotNodeSet) {
		if (newMustNotNodeSet.size() > 0) {
			mustNotMethodSet.addAll(newMustNotNodeSet);
			newMustNotNodeSet.clear();
			Iterator<String> iterator = mustNotMethodSet.iterator();
			while (iterator.hasNext()) {
				String binding = iterator.next();
				if (methodAndItsInvokeMethods.containsKey(binding)) {
					HashSet<String> invoked = methodAndItsInvokeMethods.get(binding);
					Iterator<String> iterator2 = invoked.iterator();
					while (iterator2.hasNext()) {
						String calledMethodBinding = iterator2.next();
						if (methodAndItsCallers.containsKey(calledMethodBinding)) {
							HashSet<String> callers = methodAndItsCallers.get(calledMethodBinding);
							if (mustNotMethodSet.containsAll(callers) && !mustNotMethodSet.contains(calledMethodBinding)) {
								newMustNotNodeSet.add(calledMethodBinding);
							}
						}
					}
				}
			}
			extractMustNotMethods(mustNotMethodSet, newMustNotNodeSet);
		} else {
			return;
		}
	}
	
	public static boolean isLogInControlNodes(ArrayList<ASTNode> nodeList) {
		for (ASTNode node : nodeList) {
			if (node instanceof SwitchStatement || 
					node instanceof ForStatement || 
					node instanceof EnhancedForStatement ||
					node instanceof IfStatement ||
					node instanceof WhileStatement || 
					node instanceof DoStatement || 
					node instanceof CatchClause || 
					node instanceof TryStatement) { 
				return true;
			}
		}
		return false;
	}
 	
	public static ArrayList<ASTNode> getParentNodeList(ASTNode node) { 
		ArrayList<ASTNode> results = new ArrayList<>();
		ASTNode currentNode = node.getParent();
		while (currentNode != null) {
			results.add(currentNode);
			currentNode = currentNode.getParent();
		}
		return results;
	}
	
	public static CompilationUnit getResolvedCUFromFilePath(String filePath) {
		try {
			Map options = JavaCore.getOptions();
			options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_17);
			ASTParser astParser = ASTParser.newParser(AST.getJLSLatest());
			astParser.setKind(ASTParser.K_COMPILATION_UNIT);
			String fs = FileUtils.getFileString(filePath);
			astParser.setCompilerOptions(options);
			astParser.setSource(fs.toCharArray());
			CompilationUnit cu = (CompilationUnit) astParser.createAST(null);
			List importList = cu.imports();
			
			
			astParser = ASTParser.newParser(AST.getJLSLatest());
			astParser.setKind(ASTParser.K_COMPILATION_UNIT);
			astParser.setCompilerOptions(options);
			astParser.setSource(fs.toCharArray());
			HashSet<String> dependentModuleDir = getDependentModuleDir(importList);
			String[] srcPathEntries = dependentModuleDir.toArray(new String[dependentModuleDir.size()]);
			
			
			
			String unitName = FileUtils.extractUnitnameFromAbsFilePath(filePath);
			String[] classPathEntries = {jreLibPath};
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
	
	public static HashSet<String> getDependentModuleDir(List importList) {
		HashSet<String> results = new HashSet<>();
		for (int i = 0; i < importList.size(); i ++) {
			ImportDeclaration importDeclaration = (ImportDeclaration)importList.get(i);
			String importStr = importDeclaration.getName().getFullyQualifiedName();
			if (importStr.startsWith(invokeHeuristics)) {
				if (qualifyClassNameFilePathMap.containsKey(importStr)) {
					results.add(FileUtils.extractSrcEntryFromAbsFilePath(qualifyClassNameFilePathMap.get(importStr)));
				}
				else {
				}

			}
		}
		return results;
	}
	
}

class MethodVisitor extends ASTVisitor {
	
//	ArrayList<String> methodContainLogsList = new ArrayList<>();
//	HashMap<String, HashSet<String>> methodAndItsInvokeMethods = new HashMap<>();
	HashMap<Integer, String> lineNumberAndMethodBindingMap = new HashMap<>();
	HashMap<String, MethodDeclaration> methodBindingAndMethodNodeMap = new HashMap<>();
	CompilationUnit cUnit;
	
	public MethodVisitor(CompilationUnit cu) {
		cUnit = cu;
	}
	
	@Override
	public boolean visit(MethodDeclaration node) {
	
		int lineNumber = cUnit.getLineNumber(node.getStartPosition());
		
		if (node.resolveBinding() == null) {
//			System.out.println("Can't resolve binding of method: " + node.getName());
			return false;
		}
		
		String binding = node.resolveBinding().toString();
		lineNumberAndMethodBindingMap.put(lineNumber, binding);
		
		methodBindingAndMethodNodeMap.put(binding, node);
		
		return false;
	}
}

class LogVisitor extends ASTVisitor {
	
	ArrayList<ExpressionStatement> logExpNodeList = new ArrayList<>();
	
	@Override
	public boolean visit(ExpressionStatement node) {
		if(FileUtils.ifLogPrinting(node.toString())) {
//			if ( node.toString().toLowerCase().contains(".debug") || 
//					node.toString().toLowerCase().contains(".trace") ||
			if (node.toString().toLowerCase().contains(".debug") || 
					node.toString().toLowerCase().contains(".trace") ||
					node.toString().toLowerCase().contains("system.out") ||
					node.toString().toLowerCase().contains("system.err")) {
				return false;
			}
			logExpNodeList.add(node);
		}
		
		return false;
	}
}
