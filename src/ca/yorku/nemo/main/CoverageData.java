package ca.yorku.nemo.main;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class CoverageData {
	
	Document doc;
	Element rootElem;
	
	HashMap<String, ArrayList<Element>> fileNameElemListMap = new HashMap<>();
	
	public CoverageData(String covXml) {
		init(covXml);
	}
	
	private void init(String covXml) {
		try {
			File fXmlFile = new File(covXml);
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			dbFactory.setValidating(false);
			dbFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			doc = dBuilder.parse(fXmlFile);
			rootElem = doc.getDocumentElement();
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
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public int getTotalMethodNumber() {
		return doc.getElementsByTagName("method").getLength();
	}
	
	/**
	 * Find the corresponding method cov element in xml file,
	 * since the method name could be the same, so we use startLine
	 * and endline to unique identify the element
	 * 
	 * @param filePath
	 * @param methodName
	 * @param startLine
	 * @param EndLine
	 * @return
	 */
	public Element getMethodLineCoverageMap(String filePath, String methodName, int startLine, int EndLine) {
		String splitRegex;
		if (File.separator.equals("\\")) {
			splitRegex = "\\\\";
		} else {
			splitRegex = File.separator;
		}
		String fileName = filePath.split(splitRegex)[filePath.split(splitRegex).length-1];
		ArrayList<Element> fileElemList = fileNameElemListMap.get(fileName);
		
		if (fileElemList == null)
			return null;
		
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
			System.err.println(filePath);
			return null;
		}
		NodeList methodElemList = correctFileElem.getElementsByTagName("method");
		
		for (int i = 0; i < methodElemList.getLength(); i ++) {
			Element methodElem = (Element) methodElemList.item(i);
			String mName = methodElem.getAttribute("name");
			if (methodName.equals(mName)) {
				Element firstLineElem = (Element)((Element)methodElem.getElementsByTagName("lines").item(0)).getElementsByTagName("line").item(0);
				int lineNumber = Integer.parseInt(firstLineElem.getAttribute("number"));
				if (lineNumber >= startLine && lineNumber <= EndLine)
					return methodElem;
			}
		}
		
		return null;
	}
	
	public void compareCoverage(Element covMethodElem, TreeMap<Integer, String> coverageByLog,
			TreeMap<Integer, String> branchCoverageByLog,
			MethodNodeForOutput procMd, String outputPath) {
		
		NodeList lineCovList = ((Element)covMethodElem.getElementsByTagName("lines").item(0)).getElementsByTagName("line");
		// line cov
		int coverLineCount = 0;
		int unCoverLineCount = 0;
		int mustTrue = 0;
		int mustFalse = 0;
		int mayTrue = 0;
		int mayFalse = 0;
		int mustnotTrue = 0;
		int mustnotFalse = 0;
		// branch cov
		int mustTrueBranch = 0;
		int mustFalseBranch = 0;
		int mayTrueBranch = 0;
		int mayFalseBranch = 0;
		int mustnotTrueBranch = 0;
		int mustnotFalseBranch = 0;
		
		for (int i =0; i < lineCovList.getLength(); i ++) {
			Element lineElem = (Element) lineCovList.item(i);
			int lineNumber = Integer.parseInt(lineElem.getAttribute("number"));
			String isCover = lineElem.getAttribute("cover");
			if (isCover.equals("true")) {
				coverLineCount ++;
			} else {
				unCoverLineCount ++;
			}
			if (coverageByLog.containsKey(lineNumber)) {
				String statusByLog = coverageByLog.get(lineNumber);
				if (statusByLog.equals("Must")) {
					if (isCover.equals("true")) {
						mustTrue ++;
					} else {
						System.err.println("Not covered node calculated as MUST!!!!");
						mustFalse ++;
					}
				} else if (statusByLog.equals("May")) {
					if (isCover.equals("true")) {
						mayTrue ++;
					} else {
						mayFalse ++;
					}
				} else if (statusByLog.equals("MustNot")) {
					if (isCover.equals("true")) {
						System.err.println("Covered node calculated as MUSTNOT!!!");
						mustnotTrue ++;
					} else {
						mustnotFalse ++;
					}
				}
			} else {
				if (isCover.equals("true"))
					coverLineCount --;
				else
					unCoverLineCount --;
			}
			
			if (branchCoverageByLog.containsKey(lineNumber)) {
				String statusByLog = branchCoverageByLog.get(lineNumber);
				if (statusByLog.equals("Must")) {
					if (isCover.equals("true")) {
						mustTrueBranch ++;
					} else {
						System.err.println("Not covered branch calculated as MUST!!!!");
						mustFalseBranch ++;
					}
				} else if (statusByLog.equals("May")) {
					if (isCover.equals("true")) {
						mayTrueBranch ++;
					} else {
						mayFalseBranch ++;
					}
				} else if (statusByLog.equals("MustNot")) {
					if (isCover.equals("true")) {
						System.err.println("Covered branch calculated as MUSTNOT!!!");
						mustnotTrueBranch ++;
					} else {
						mustnotFalseBranch ++;
					}
				}
			}
		}
		
		
		System.out.printf("Oracle Coverage: %2f\t Our coverage range: %2f - %2f\n", coverLineCount/(float)(coverLineCount+unCoverLineCount),
				 (mustTrue+mustFalse)/(float)(coverLineCount+unCoverLineCount),  (mustTrue+mustFalse+mayTrue+mayFalse)/(float)(coverLineCount+unCoverLineCount));
		System.out.println("\ttrue\tfalse");
		System.out.printf("Must\t%d\t%d\n", mustTrue,mustFalse);
		System.out.printf("May\t%d\t%d\n",mayTrue,mayFalse);
		System.out.printf("MustNot\t%d\t%d\n",mustnotTrue,mustnotFalse);

		try(PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(outputPath, true)))) {
			writer.printf("%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d\n", procMd,
					mustTrue,mustFalse,mayTrue,mayFalse,mustnotTrue,mustnotFalse,
					mustTrueBranch,mustFalseBranch,mayTrueBranch,mayFalseBranch,mustnotTrueBranch,mustnotFalseBranch);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
