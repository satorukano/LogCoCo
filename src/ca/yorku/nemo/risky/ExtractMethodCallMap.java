package ca.yorku.nemo.risky;

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
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;

import com.google.common.base.Joiner;

import ca.yorku.nemo.main.FileUtils;
import ca.yorku.nemo.main.MainParser;

public class ExtractMethodCallMap {
	
	static String qualifyNameFileInfoPath = "/Users/satorukano/repository/research/LogCoCo/output/outputClass.txt";
	static String projectPath = "/Users/satorukano/repository/research/zookeeper";;
	static String jreLibPath = "/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home/jre/lib/rt.jar";
	static String outputContainLogMethodList = "/Users/satorukano/repository/research/LogCoCo/output/outputContainLogMethodList.txt";
	static String outputInvokingMethods = "/Users/satorukano/repository/research/LogCoCo/output/invoke_method.txt";
	
	static String invokeHeuristics = "org.apache.zookeeper";
	HashMap<String, HashSet<String>> methodCallMap = new HashMap<>();
	
	static HashMap<String, String> qualifyClassNameFilePathMap = new HashMap<>();
	
	static ArrayList<String> allFiles = new ArrayList<>();
	
	public static void main(String[] args) {
		
		projectPath = args[0];
		qualifyNameFileInfoPath = args[1];
//		jreLibPath = args[2];
		outputContainLogMethodList = args[2];
		outputInvokingMethods = args[3];
		invokeHeuristics = args[4];
		
		
		try (BufferedReader br = new BufferedReader(new FileReader(qualifyNameFileInfoPath))) {
			String line = null;
			while((line = br.readLine()) != null) {
				String[] results = line.split(",");
				qualifyClassNameFilePathMap.put(results[0], results[1]);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		try {
			traverse(new File(projectPath));
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		
		for(String filePath : allFiles) {
			
			System.out.println(filePath);
			
			CompilationUnit cu = getResolvedCUFromFilePath(filePath);
			
			if (cu == null) {
				System.err.println("Cannot parse file: " + filePath);
				continue;
			}
			
			MethodVisitor mVisitor = new MethodVisitor(cu);
			
			cu.accept(mVisitor);
			
			HashMap<String, HashSet<String>> methodAndItsInvokeMethods = mVisitor.methodAndItsInvokeMethods;
			
			try(PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(outputInvokingMethods, true)))) {
				for (String callMethod : methodAndItsInvokeMethods.keySet()) {
					HashSet<String> invokedMethods = methodAndItsInvokeMethods.get(callMethod);
					HashSet<IMethodBinding> invokedMethodBindings = mVisitor.methodAndItsInvokeMethodBindings.get(callMethod);
					String invokeMethodAsString = "None";
					if(invokedMethods.size() != 0) {
						invokeMethodAsString = Joiner.on(";").join(invokedMethods);
						
						ArrayList<String> invokeMethodKeys = new ArrayList<>();
						Iterator<IMethodBinding> iterator = invokedMethodBindings.iterator();
						while (iterator.hasNext()) {
							IMethodBinding binding = iterator.next();
							String declarationClassQualifiedName = binding.getDeclaringClass().getQualifiedName();
							String declarationMethodKey = binding.toString();
							
							if (qualifyClassNameFilePathMap.containsKey(declarationClassQualifiedName)) {
								String callClassFilePath = qualifyClassNameFilePathMap.get(declarationClassQualifiedName);
								String key = callClassFilePath + "|||||" + declarationMethodKey;
								invokeMethodKeys.add(key);
//								CompilationUnit cu2 = getResolvedCUFromFilePath(callClassFilePath);
//								MethodVisitor mv2 = new MethodVisitor(cu2);
//								cu2.accept(mv2);
//								if (mv2.methodBindingAndItsMethodNodes.containsKey(declarationMethodKey) ) {
//									MethodDeclaration md = mv2.methodBindingAndItsMethodNodes.get(declarationMethodKey);
//									int startLine = cu2.getLineNumber(md.getStartPosition());
//									int endLine = cu2.getLineNumber(md.getStartPosition() + md.getLength() - 1);
//									String key = callClassFilePath + "|||||" + declarationMethodKey + "|||||" + md.getName().toString() +
//											"|||||" + startLine + "|||||" + endLine;
//									invokeMethodKeys.add(key);
//								}
							}
						}
						invokeMethodAsString= Joiner.on(";").join(invokeMethodKeys);
					}
					
					
					writer.printf("%s\t%d\t%s\t%s\n", filePath,invokedMethods.size(), callMethod, invokeMethodAsString);
					
				}
			} catch (Exception e) {
				e.printStackTrace();
			} 
			
			try(PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(outputContainLogMethodList, true)))) {
				for (String methodContainLog : mVisitor.methodContainLogsList) {
					writer.printf("%s\t%s\n", filePath, methodContainLog);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	public static void traverse(File node) throws Exception {
		if(node.isFile() && node.getName().endsWith(".java")) {
			if (!node.getAbsolutePath().contains("test") && node.getAbsolutePath().contains("src"+File.separator+"main"+File.separator+"java")) 
				allFiles.add(node.getAbsolutePath());
		} else if(node.isDirectory()) {
			for(File f: node.listFiles()) {
				traverse(f);
			}
		}
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
	
	public static HashSet<String> getDependentModuleDir(List importList) {
		HashSet<String> results = new HashSet<>();
		for (int i = 0; i < importList.size(); i ++) {
			ImportDeclaration importDeclaration = (ImportDeclaration)importList.get(i);
			String importStr = importDeclaration.getName().getFullyQualifiedName();
			if (importStr.startsWith(invokeHeuristics)) {
				if (qualifyClassNameFilePathMap.containsKey(importStr)) {
					results.add(FileUtils.extractSrcEntryFromAbsFilePath(qualifyClassNameFilePathMap.get(importStr)));
				}
//				else {
//				}
			}
		}
		return results;
	}
	
}

class MethodVisitor extends ASTVisitor {
	
	ArrayList<String> methodContainLogsList = new ArrayList<>();
	HashMap<String, HashSet<String>> methodAndItsInvokeMethods = new HashMap<>();
	HashMap<String, HashSet<IMethodBinding>> methodAndItsInvokeMethodBindings = new HashMap<>();
	HashMap<String, MethodDeclaration> methodBindingAndItsMethodNodes = new HashMap<>();
	
	CompilationUnit cUnit;
	
	public MethodVisitor(CompilationUnit cu) {
		cUnit = cu;
	}
	
	@Override
	public boolean visit(MethodDeclaration node) {
		
		MIVisitor miVisitor = new MIVisitor(cUnit,node);
		
		node.accept(miVisitor);
		
		if (node.resolveBinding() == null) {
//			System.out.println("Can't resolve binding of method: " + node.getName());
			return false;
		}
		String key = node.resolveBinding().toString();
		
		methodAndItsInvokeMethods.put(key, miVisitor.invokeMethodKey);
		methodAndItsInvokeMethodBindings.put(key, miVisitor.invokeMethodBinding);
		methodBindingAndItsMethodNodes.put(key, node);
		
		if (miVisitor.logList.size() != 0) {
			methodContainLogsList.add(key);
		}
		
		
		return false;
	}
}

class MIVisitor extends ASTVisitor {
	
	CompilationUnit cUnit;
	MethodDeclaration md;
	
	HashSet<String> invokeMethodKey = new HashSet<>();
	
	HashSet<IMethodBinding> invokeMethodBinding = new HashSet<>();
	
	ArrayList<ExpressionStatement> logList = new ArrayList<>();
	
	public MIVisitor(CompilationUnit cu, MethodDeclaration md) {
		cUnit = cu;
		this.md = md;
	}
	
	@Override
	public boolean visit(ExpressionStatement node) {
		if(FileUtils.ifLogPrinting(node.toString())) {
			logList.add(node);
			return false;
		}
		return true;
	}
	
	@Override
	public boolean visit(MethodInvocation node) {
		IMethodBinding binding = node.resolveMethodBinding();
		if (binding != null) {
			if (binding.toString().contains(ExtractMethodCallMap.invokeHeuristics)) {
				invokeMethodKey.add(binding.toString());
				invokeMethodBinding.add(binding);
			}
		}
		return false;
	}
	
	
}
