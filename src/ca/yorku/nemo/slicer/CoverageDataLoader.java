package ca.yorku.nemo.slicer;

import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

public class CoverageDataLoader {
    
    public static class CoverageLoadResult {
        public List<MethodCoverageInfo> methodCoverageList = new ArrayList<>();
        public HashMap<String, ArrayList<MethodCoverageInfo>> fileToMethodsMap = new HashMap<>();
    }
    
    public static class MethodCoverageInfo {
        public String methodName;
        public String filePath;
        public int startLine;
        public int endLine;
        public List<Integer> mustLineNumbers = new ArrayList<>();
        public List<Integer> mayLineNumbers = new ArrayList<>();
    }
    
    public static CoverageLoadResult loadCoverageData(String coverageFile) throws Exception {
        CoverageLoadResult result = new CoverageLoadResult();
        
        try (FileReader fileReader = new FileReader(coverageFile);
             CSVParser csvParser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(fileReader)) {
            
            for (CSVRecord record : csvParser) {
                MethodCoverageInfo info = parseCoverageCSVRecord(record);
                if (info != null && !info.mayLineNumbers.isEmpty()) {
                    result.methodCoverageList.add(info);
                    result.fileToMethodsMap.computeIfAbsent(info.filePath, k -> new ArrayList<>()).add(info);
                }
            }
        }
        
        System.out.println("Loaded " + result.methodCoverageList.size() + " methods with May labels");
        return result;
    }
    
    public static MethodCoverageInfo parseCoverageCSVRecord(CSVRecord record) {
        try {
            MethodCoverageInfo info = new MethodCoverageInfo();
            info.methodName = record.get(0);
            info.filePath = record.get(1);
            info.startLine = Integer.parseInt(record.get(2));
            info.endLine = Integer.parseInt(record.get(3));
            
            // Parse MustLineNumbers (field 14)
            info.mustLineNumbers = parseLineNumbers(record.get(14));
            
            // Parse MayLineNumbers (field 15)
            info.mayLineNumbers = parseLineNumbers(record.get(15));
            
            return info;
        } catch (Exception e) {
            System.err.println("Error parsing CSV record: " + record);
            e.printStackTrace();
            return null;
        }
    }
    
    public static MethodCoverageInfo parseCoverageCSVLine(String line) {
        try {
            // Handle CSV with quotes using Apache Commons CSV
            CSVParser parser = CSVFormat.DEFAULT.parse(new java.io.StringReader(line));
            CSVRecord record = parser.iterator().next();
            
            return parseCoverageCSVRecord(record);
        } catch (Exception e) {
            System.err.println("Error parsing line: " + line);
            e.printStackTrace();
            return null;
        }
    }
    
    private static List<Integer> parseLineNumbers(String lineNumberStr) {
        List<Integer> lineNumbers = new ArrayList<>();
        lineNumberStr = lineNumberStr.replaceAll("\"", "");
        
        if (!"None".equals(lineNumberStr) && !lineNumberStr.isEmpty()) {
            String[] numbers = lineNumberStr.split(";");
            for (String num : numbers) {
                try {
                    lineNumbers.add(Integer.parseInt(num.trim()));
                } catch (NumberFormatException e) {
                    // Skip invalid numbers
                }
            }
        }
        return lineNumbers;
    }
}