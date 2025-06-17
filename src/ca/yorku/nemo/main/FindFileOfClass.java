package ca.yorku.nemo.main;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

public class FindFileOfClass {
	
	static String projectPath = "/Users/satorukano/repository/research/hbase-1.2.6";
	static String outputFilePath = "/Users/satorukano/repository/research/LogCoCo/output/replication/hbase/outputClass.txt";
	static String jreLibPath = "/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home/jre/lib/rt.jar";
	
	static HashMap<String, String> qualifyNameFileAbsPathMap = new HashMap<>();
	static LinkedHashSet<String> allFiles = new LinkedHashSet<>(); 
	public static void main(String[] args) {
		
//		projectPath = args[0];
//		outputFilePath = args[1];
//		jreLibPath = args[2];
		
		try {
			traverse(new File(projectPath));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		try {
			for (String filePath : allFiles) {
				Map options = JavaCore.getOptions();
				options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_1_8);
				ASTParser astParser = ASTParser.newParser(AST.JLS8);
				astParser.setKind(ASTParser.K_COMPILATION_UNIT);
				String fs = FileUtils.getFileString(filePath);
				astParser.setCompilerOptions(options);
				astParser.setSource(fs.toCharArray());
				String unitName = FileUtils.extractUnitnameFromAbsFilePath(filePath);
				String[] classPathEntries = {jreLibPath};
				String[] srcPathEntries = {FileUtils.extractSrcEntryFromAbsFilePath(filePath)};
				String[] encodeArray = new String[srcPathEntries.length];
				Arrays.fill(encodeArray, "UTF-8");
				astParser.setUnitName(unitName);
				astParser.setEnvironment(classPathEntries, srcPathEntries,encodeArray, true);
				astParser.setResolveBindings(true);
				astParser.setBindingsRecovery(true);
				
				CompilationUnit cu = (CompilationUnit) astParser.createAST(null);
				
				FindClassVisitor visitor = new FindClassVisitor();
				cu.accept(visitor);
				
				for(String qualifyName : visitor.qualifyNameList) {
					if (qualifyName.equals("")) {
						System.out.println("debug");
					}
					if(qualifyNameFileAbsPathMap.containsKey(qualifyName)) {
						System.err.println(qualifyName);
						System.err.println(filePath);
						System.err.println(qualifyNameFileAbsPathMap.get(qualifyName));
						System.err.println("DUP!!! ");
					} else {
						qualifyNameFileAbsPathMap.put(qualifyName, filePath);
					}
				}
				
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(outputFilePath, true)))) {
			for (String key : qualifyNameFileAbsPathMap.keySet()) {
				writer.printf("%s,%s\n", key, qualifyNameFileAbsPathMap.get(key));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	private static void traverse(File node) throws Exception {
		if(node.isFile() && node.getName().endsWith(".java")) {
			if (!node.getAbsolutePath().contains("test") && node.getAbsolutePath().contains("src"+File.separator+"main"+File.separator+"java")) 
				allFiles.add(node.getAbsolutePath());
		} else if(node.isDirectory()) {
			for(File f: node.listFiles()) {
				traverse(f);
			}
		}
	}
	
}
