package ca.yorku.nemo.main;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Joiner;

public class LogRegexMatcher {
	
	HashSet<String> logRegexSet = new HashSet<>();
	HashSet<String> logIdSet = new HashSet<>();
	ArrayList<ArrayList<String>> filteredLogSequenceListOfThreads = new ArrayList<>();
	
	static ArrayList<String> rawLogList = new ArrayList<>();
	String logSequence = "";
	
	public LogRegexMatcher(HashSet<String> logRegexSet) {
		this.logRegexSet = logRegexSet;
		getAllLogOccurance();
	}
		
	static void initWithLogStr(File node) throws Exception {
		if(node.isFile()) {
			String logContent = FileUtils.getFileString(node.getAbsolutePath());
			if (!logContent.equals(""))
				rawLogList.add(logContent);
		} else if(node.isDirectory()) {
			for(File f: node.listFiles()) {
				initWithLogStr(f);
			}
		}
	}
	
	private void getAllLogOccurance() {
		Pattern logIdPattern = Pattern.compile("\\w+\\.java:\\d+");
		for (String logRegex : logRegexSet) {
			Matcher matcher = logIdPattern.matcher(logRegex);
			while(matcher.find()) {
				logIdSet.add("[" + matcher.group() + "]");
			}
		}
	}
	
	public void filterInLogFile() throws Exception {
//		String logFileContent = FileUtils.getFileString(filePath);
		for (String logFileContent : rawLogList) {
			ArrayList<String> filteredLogSequence = new ArrayList<>();
			String[] strList = logFileContent.split("\\r?\\n");
	//		ArrayList<String> filterLogList = new ArrayList<>();
			for (int i= 0; i < strList.length; i ++) {
				String logLine = strList[i];
				// Split by tab to separate thread name from log ID
				String[] parts = logLine.split("\t");
				if (parts.length >= 2) {
					String logID = parts[1]; // Extract [FileName.java:lineNumber]
					if (logIdSet.contains(logID)) {
						filteredLogSequence.add(logID);
					}
				}
			}
			filteredLogSequenceListOfThreads.add(filteredLogSequence);
		}
	}
	
	public HashMap<String, ArrayList<String>> getMatchedLogRegex() {
		HashMap<String, ArrayList<String>> result = new HashMap<>();
		
		for (ArrayList<String> filteredLogSequence : filteredLogSequenceListOfThreads) {
			String logSequenceWithoutLineChange = Joiner.on("").join(filteredLogSequence);
			for (String logRegex : logRegexSet) {
				ArrayList<String> matchedStrList = new ArrayList<>();
				Pattern p = Pattern.compile(logRegex);
				Matcher m = p.matcher(logSequenceWithoutLineChange);
				while (m.find()) {
					matchedStrList.add(m.group());
				}
				if (!matchedStrList.isEmpty() ) {
					result.put(logRegex, matchedStrList);
				}
			}
		}
		return result;
	}
}
