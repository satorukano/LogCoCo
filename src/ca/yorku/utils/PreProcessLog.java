package ca.yorku.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;

import com.google.common.base.Joiner;

import ca.yorku.nemo.main.FileUtils;
import ca.yorku.nemo.risky.ExtractMethodCallMap;
import ca.yorku.nemo.main.PackageNameResolver;

public class PreProcessLog {
	
//	static String logFilePath = "hbase-nemo-master-Nemos-MacBook-Pro-2.local.log";
//	static String logFilePath = "";
	static String qualifyClassNameAndFileInfoPath = "/Users/satorukano/repository/research/LogCoCo/output/outputClass.txt";
	static PackageNameResolver packageNameResolver;


	
	static HashMap<String, String> qualifyClassNameFilePathMap = new HashMap<>();
	
	public static void main(String[] args) {
		
		String option = args[0];
		String path = args[1];
		String outputDir = args[2];
		qualifyClassNameAndFileInfoPath = args[3];

		System.out.println("preprocess log for option: " + option);
		
		try (BufferedReader br = new BufferedReader(new FileReader(qualifyClassNameAndFileInfoPath))) {
			String line = null;
			packageNameResolver = new PackageNameResolver(qualifyClassNameAndFileInfoPath);
			while((line = br.readLine()) != null) {
				String[] results = line.split(",");
				String[] tokens = results[0].split("\\.");
				String key = null;
				if (option.equals("hbase")) {
					key = tokens[tokens.length-2] + "." + tokens[tokens.length-1];
				} else if (option.equals("zookeeper")) {
					key = tokens[tokens.length-1];
				} else if (option.equals("druid")) {
					key = tokens[tokens.length-1];
				}else if (option.equals("activemq")) {
					key = tokens[tokens.length-1];
				}
				qualifyClassNameFilePathMap.put(key, results[1]);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		// for integration tests
		// File folder = new File("integration_test_data/logs");
		
		// for ycsb tests
		
		// File folder = new File("new_test_ycsb_for_verify_purpose/master_logs");
		File folder =  new File(path);
		File[] listOfFiles = null;
		if (folder.isDirectory()) {
			listOfFiles = folder.listFiles();
		} else {
			listOfFiles = new File[] {folder};
		}
		
		
	    for (int i = 0; i < listOfFiles.length; i++) {
	      if (listOfFiles[i].isFile()) {
//	    	new File("integration_test_data/test/" + i).mkdirs();
	    	String logFilePath = listOfFiles[i].getAbsolutePath();
	    	System.out.println(logFilePath);
	    	String outlogFileName = listOfFiles[i].getName().split("\\.")[listOfFiles[i].getName().split("\\.").length-2];
	    	try (BufferedReader br = new BufferedReader(new FileReader(logFilePath))) {
				String line = null;
				Stack<String> completeLog = new Stack<>();
				while((line = br.readLine()) != null) {
					if (line.startsWith("2025")) {
						if (!completeLog.isEmpty()) {
							String logStmt = Joiner.on("\n").join(completeLog);
							completeLog.clear();
							String process_line = extractThreadAndFileLineNumber(logStmt, option);
							if (process_line != "") {
//								try(FileWriter fw = new FileWriter("integration_test_data/test/"+ outlogFileName+".txt", true);
								File fTest = new File(outputDir);
								if (!fTest.exists()) {
									fTest.mkdir();
								}
								try(FileWriter fw = new FileWriter(Paths.get(outputDir,"process_log.txt").toString(), true);
									    BufferedWriter bw = new BufferedWriter(fw);
									    PrintWriter out = new PrintWriter(bw))
									{
									    out.println(extractThreadAndFileLineNumber(logStmt, option));
									    
									} catch (IOException e) {
									    //exception handling left as an exercise for the reader
									}
							}
						}
						completeLog.push(line);
					} else {
						completeLog.push(line);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
	      } 
	    }
//		String testLogLine = "2018-01-18 14:43:08,138 INFO  [master/nemo-Scale-07/127.0.1.1:16000] hbase.ChoreService: Chore service for: nemo-scale-07,16000,1516304583920 had [] on shutdown";
//		System.out.println(extractThreadAndFileLineNumberWithoutLineNumber(testLogLine));

		
	}
	
	
	
	public static String extractThreadAndFileLineNumber(String logLine, String opt) {
		String threadName = "";
		String filePath = ""; 
		String clsIdentity = "";
		String lineNumber = "";
		String fileName = "";
		if (opt == "ycsb") {
			Pattern p = Pattern.compile("(\\[.+?\\]) (\\w+\\.\\w+)\\((\\d+)\\):");
			Matcher m = p.matcher(logLine);
			if (m.find()) {
				threadName = m.group(1);
				clsIdentity = m.group(2);
				lineNumber = m.group(3);
			}
			
	//		if (qualifyClassNameFilePathMap.containsKey(clsIdentity)) {
	//			filePath = qualifyClassNameFilePathMap.get(clsIdentity);
	//			String[] tmp = filePath.split(File.separator);
	//			fileName = tmp[tmp.length-1];
	//			try {
	//				lineNumber = getLineNumberFromLogStmt(filePath, logLine);
	//			} catch (Exception e) {
	//				e.printStackTrace();
	//			}
	//		}
	//		
	//		if (lineNumber == "") {
	//			return logLine;
	//		}
	//		else
			if (qualifyClassNameFilePathMap.containsKey(clsIdentity)) {
				filePath = qualifyClassNameFilePathMap.get(clsIdentity);
				String[] tmp = filePath.split(File.separator);
				fileName = tmp[tmp.length-1];
				return threadName + "\t" + "[" + fileName + ":" + lineNumber + "]"; 
			} else {
				return "";
			}
		} else if (opt.equals("hbase")) {
//			if (logLine.contains("DEBUG") || logLine.contains("TRACE")) 
//				return "";
			Pattern p = Pattern.compile("(\\[.+?\\]) (\\w+\\.\\w+)(\\$\\w+)?\\((\\d+)\\):");
			Matcher m = p.matcher(logLine);
			if (m.find()) {
				threadName = m.group(1);
				clsIdentity = m.group(2);
				lineNumber = m.group(4);
			}
			System.out.println("clsIdentity: " + clsIdentity);
			System.out.println("lineNumber: " + lineNumber);
			if (qualifyClassNameFilePathMap.containsKey(clsIdentity)) {
				filePath = qualifyClassNameFilePathMap.get(clsIdentity);
				String[] tmp = filePath.split(File.separator);
				fileName = tmp[tmp.length-1];
				return threadName + "\t" + "[" + fileName + ":" + lineNumber + "]"; 
			} else {
				return "";
			}
		} else if (opt.equals("zookeeper")) {
			Pattern p = Pattern.compile("-\\s+\\w+\\s+\\[([^:]+):([^@]+)@(\\d+)\\]");
			Matcher m = p.matcher(logLine);
			if (m.find()) {
				threadName = m.group(1);
				clsIdentity = m.group(2);
				lineNumber = m.group(3);
				// Extract outer class if inner class (contains $)
				if (clsIdentity.contains("$")) {
					clsIdentity = clsIdentity.substring(0, clsIdentity.indexOf("$"));
				}
			}
			if (qualifyClassNameFilePathMap.containsKey(clsIdentity)) {
				filePath = qualifyClassNameFilePathMap.get(clsIdentity);
				String[] tmp = filePath.split(File.separator);
				fileName = tmp[tmp.length-1];
				return threadName + "\t" + "[" + fileName + ":" + lineNumber + "]";
			} else {
				return "";
			}
		} else if (opt.equals("druid")) {
			Pattern p = Pattern.compile("^.*?\\[(.*)]\\s+[^\\[\\]]*\\.([^@]+)@(\\d+)");
			Matcher m = p.matcher(logLine);
			if (m.find()) {
				threadName = m.group(1);
				clsIdentity = m.group(2);
				lineNumber = m.group(3);
				System.out.println("clsIdentity: " + clsIdentity);
				System.out.println("lineNumber: " + lineNumber);
			}
			if (qualifyClassNameFilePathMap.containsKey(clsIdentity)) {
				filePath = qualifyClassNameFilePathMap.get(clsIdentity);
				String[] tmp = filePath.split(File.separator);
				fileName = tmp[tmp.length-1];
				return threadName + "\t" + "[" + fileName + ":" + lineNumber + "]";
			} else {
				return "";
			}
		} else if (opt.equals("activemq")) {
			Pattern p = Pattern.compile("\\S+\\s+\\S+,\\d+\\s+\\[(.*)\\]\\s+(?:- )?\\w+\\s+(\\S+)\\s+@(\\d+)");
			Matcher m = p.matcher(logLine);
			Pattern p2 = Pattern.compile("^[^|]+\\|[^|]+\\|\\s*([^|]+?)\\s*\\|[^|]*?(\\S+)\\s+@(\\d+)");
			Matcher m2 = p2.matcher(logLine);
			if (m.find()) {
				threadName = m.group(1);
				clsIdentity = m.group(2);
				lineNumber = m.group(3);
				System.out.println("clsIdentity: " + clsIdentity);
				System.out.println("lineNumber: " + lineNumber);
			}
			else if (m2.find()) {
				threadName = m2.group(1);
				clsIdentity = m2.group(2);
				lineNumber = m2.group(3);
				System.out.println("clsIdentity: " + clsIdentity);
				System.out.println("lineNumber: " + lineNumber);
			}
			if (qualifyClassNameFilePathMap.containsKey(clsIdentity)) {
				filePath = qualifyClassNameFilePathMap.get(clsIdentity);
				String[] tmp = filePath.split(File.separator);
				fileName = tmp[tmp.length-1];
				return threadName + "\t" + "[" + fileName + ":" + lineNumber + "]";
			} else {
				return "";
			}
		}
		return "";
	}
	
	public static String getLineNumberFromLogStmt(String filePath, String logLine) throws Exception {
		
		String lineNumber = "";
		
		Map options = JavaCore.getOptions();
		options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_17);
		ASTParser astParser = ASTParser.newParser(AST.getJLSLatest());
		astParser.setKind(ASTParser.K_COMPILATION_UNIT);
		String fs = FileUtils.getFileString(filePath);
		astParser.setCompilerOptions(options);
		astParser.setSource(fs.toCharArray());
//		String unitName = FileUtils.extractUnitnameFromAbsFilePath(filePath);
//		String[] classPathEntries = {jreLibPath};
//		String[] srcPathEntries = {FileUtils.extractSrcEntryFromAbsFilePath(filePath)};
//		String[] encodeArray = new String[srcPathEntries.length];
//		Arrays.fill(encodeArray, "UTF-8");
//		astParser.setUnitName(unitName);
//		astParser.setEnvironment(classPathEntries, srcPathEntries,encodeArray, true);
//		astParser.setResolveBindings(true);
//		astParser.setBindingsRecovery(true);
		
		CompilationUnit cu = (CompilationUnit) astParser.createAST(null);
		MIVisitor visitor = new MIVisitor(cu);
		cu.accept(visitor);
		for (MethodInvocation logStmtMI: visitor.logList) {
			HashMap<String, ArrayList<String>> logArguments = getLogArgumentContentsFromLogMInode(logStmtMI);
			if (logArguments.containsKey("StringLiteral")) {
				boolean allStringInLog = true;
				for (String staticText : logArguments.get("StringLiteral")) {
					String text = staticText.substring(1, staticText.length()-1);
					if (!logLine.contains(text)) {
						allStringInLog = false;
						break;
					}
				}
				if (allStringInLog) {
					lineNumber = Integer.toString(cu.getLineNumber(logStmtMI.getStartPosition()));
					break;
				}
			}
		}
		return lineNumber;
	}
	
	public static HashMap<String, ArrayList<String>> getLogArgumentContentsFromLogMInode(MethodInvocation mi) {
		HashMap<String, ArrayList<String>> logArgumentContents = new HashMap<>();
		List arguments = mi.arguments();
		if (arguments.size() > 1) {
			for (int i = 0; i < arguments.size(); i ++)
			{
				Object obj = arguments.get(i);
				if (obj instanceof InfixExpression)
				{
					InfixExpression ex = (InfixExpression) obj;
					if(ex.getOperator().toString().equals("+"))
						recursiveInfixExpression(ex,logArgumentContents);
					else
						objectTypeChoose(obj,logArgumentContents);
				} else 
				{
					objectTypeChoose(obj,logArgumentContents);
				}
			}
		} else if(arguments.size() == 1)
		{
			if (arguments.get(0) instanceof InfixExpression)
			{
				InfixExpression ex = (InfixExpression) arguments.get(0);
				if(ex.getOperator().toString().equals("+"))
					recursiveInfixExpression(ex,logArgumentContents);
				else
					objectTypeChoose(ex,logArgumentContents);
			}
			else {
				objectTypeChoose(arguments.get(0),logArgumentContents);
			}

		}
		return logArgumentContents;
	}
	
	private static void recursiveInfixExpression(InfixExpression ex,HashMap<String, ArrayList<String>> argumentContents)
	{
		Expression left = ex.getLeftOperand();
		Expression right = ex.getRightOperand();
		List extendedOperands = ex.extendedOperands();
		if (left.getNodeType() == ASTNode.INFIX_EXPRESSION)
		{
			InfixExpression next = (InfixExpression) left;
			if (next.getOperator().toString().equals("+"))
			{
				recursiveInfixExpression(next,argumentContents);
			}
			else{
				expressionTypeChoose(next,argumentContents);
				// expression.add(next.toString());
			}
		} 
		else 
		{
			expressionTypeChoose(left,argumentContents);
		}
		if (right.getNodeType() == ASTNode.INFIX_EXPRESSION)
		{
			InfixExpression next = (InfixExpression) right;
			if (next.getOperator().toString().equals("+"))
				recursiveInfixExpression(next,argumentContents);
			else
				expressionTypeChoose(next,argumentContents);
		}
		else 
		{
			expressionTypeChoose(right,argumentContents);
		}
		
		for (int i = 0; i < extendedOperands.size(); i ++)
		{
			objectTypeChoose(extendedOperands.get(i),argumentContents);
		}
	}
	
	private static void expressionTypeChoose(Expression ex,HashMap<String, ArrayList<String>> argumentContents)
	{
		String key = ex.getClass().getSimpleName();
		String value = ex.toString();
		if (argumentContents.containsKey(key))
		{
			argumentContents.get(key).add(value);
		} else 
		{
			ArrayList<String> valueList = new ArrayList<>();
			valueList.add(value);
			argumentContents.put(key, valueList);
		}
	}
	
	private static void objectTypeChoose(Object obj,HashMap<String, ArrayList<String>> argumentContents)
	{
		String key = obj.getClass().getSimpleName();
		String value = obj.toString();
		if (argumentContents.containsKey(key))
		{
			argumentContents.get(key).add(value);
		} else 
		{
			ArrayList<String> valueList = new ArrayList<>();
			valueList.add(value);
			argumentContents.put(key, valueList);
		}
	}
	
	
}

class MIVisitor extends ASTVisitor {
	
	CompilationUnit cUnit;
	
	ArrayList<MethodInvocation> logList = new ArrayList<>();
	
	public MIVisitor(CompilationUnit cu) {
		cUnit = cu;
	}
	
	@Override
	public boolean visit(ExpressionStatement node) {
		if(FileUtils.ifLogPrinting(node.toString())) {
			TmpVisitor visitor = new TmpVisitor();
			node.accept(visitor);
			logList.add(visitor.mi);
			return false;
		}
		return true;
	}
	
	class TmpVisitor extends ASTVisitor {
		MethodInvocation mi;
		public boolean visit(MethodInvocation node) {
				mi = node;
				return false;
			
		}
	}

}
