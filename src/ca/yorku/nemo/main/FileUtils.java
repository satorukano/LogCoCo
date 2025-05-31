package ca.yorku.nemo.main;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BreakStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.alg.cycle.CycleDetector;
import org.jgrapht.graph.DefaultEdge;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.io.Files;

public class FileUtils {
	
	private final static String regexGLOBAL = ".*?(log|trace|(system\\.out)|(system\\.err)).*?(.*?);";
	
	public static String getFileString(String filePath) throws Exception
	{
		return Files.toString(new File(filePath), Charsets.UTF_8);
	}
	
	public static MethodDeclaration getDeclarationMethodNode(ASTNode node) {
		ASTNode currentNode = node;
		while(currentNode != null) {
			if (currentNode.getNodeType() == ASTNode.METHOD_DECLARATION) {
				return (MethodDeclaration) currentNode;
			} else {
				currentNode = currentNode.getParent();
			}
		}
		return null;
	}
	
	public static String getMethodSignatureFromMethodDeclarationNode(MethodDeclaration node) {
		String name = node.getName().toString();
		ArrayList<String> paras = new ArrayList<>();
		List para = node.parameters();	
		if (!para.isEmpty())
		{
			for (int i = 0; i < para.size(); i ++)
				{
					SingleVariableDeclaration sfv = (SingleVariableDeclaration)para.get(i);
					paras.add(sfv.getType().toString());
				}
		}
		String sig = name + "(" + Joiner.on(",").join(paras) + ")";
		return sig;
	}
	
	
	public static String extractSrcEntryFromAbsFilePath(String filePath) {
		String temp = "src"+ File.separator + "main" +  File.separator + "java";
		int i = filePath.indexOf(temp);
		return filePath.substring(0, i+temp.length());
	}
	
	public static String extractUnitnameFromAbsFilePath(String filePath) {
		String temp = "src"+ File.separator + "main" +  File.separator + "java";
		int i = filePath.indexOf(temp);
		String splitRegex = "";
		if (File.separator.equals("\\")) {
			splitRegex = "\\\\";
		} else {
			splitRegex = File.separator;
		}
		String[] otherHalf = filePath.substring(0, i-1).split(splitRegex);
		String moduleName = otherHalf[otherHalf.length-1];
		return "/" + moduleName + "/" + filePath.substring(i).replace(File.separator, "/");
	}


	private static boolean isLogRelated(String content)
	{
		if (content.split("\r|\n|\r\n").length >= 3) // set as 3
		{
			return false;
		}
		else
		{
			Pattern p = Pattern.compile(regexGLOBAL, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
			Matcher m = p.matcher(content);
			if (m.find())
			{
				if (content.toLowerCase().contains("system.out") || content.toLowerCase().contains("system.err"))
				{
					return true;
				}
				content = content.replaceAll("\"(.*?)\"", "");
				Pattern pKeyword = Pattern.compile("(login)|(dialog)|(logout)|(catalog)|logic(al)?", Pattern.CASE_INSENSITIVE);
				Pattern pTrueKeyword = Pattern.compile("loginput|logoutput", Pattern.CASE_INSENSITIVE);
				m = pKeyword.matcher(content);
				Matcher m2 = pTrueKeyword.matcher(content);
				if (m.find() && !m2.find())
					return false;
				
				return true;
			}
		}
		return false;
	}
	
	public static boolean ifLogPrinting(String cur)
	{
		// if log header contain set, then return false
		String temp = cur.replaceAll("\".*?\"", "");
		temp = temp.replaceAll("\\(.*?\\)", "");
		if (temp.toLowerCase().contains("setlevel"))
			return false;
		
		if (temp.toLowerCase().contains("editlog"))
			return false;
		
		//
		Pattern p = Pattern.compile("\".*\"");
		Matcher m = p.matcher(cur);
		
		if(cur.toLowerCase().contains("assertequals") || cur.toLowerCase().contains("assertfalse") || cur.toLowerCase().contains("asserttrue"))
		{
			return false;
		}
		
		/* if find quotes */
		if (m.find())
		{
			cur = cur.replaceAll("\".*?\"", "");
			if (!isLogRelated(cur))
				return false;
			p = Pattern.compile("(system\\.out)|(system.err)|(log(ger)?(\\(\\))?\\.(\\w*?)\\()|logauditevent\\(",Pattern.CASE_INSENSITIVE);
			m = p.matcher(cur);
			if (m.find())
			{
				return true;
			}
			return false;
		}
		else 
		{
			if (!isLogRelated(cur))
				return false;
			p = Pattern.compile("[^\"]*?\\=");
			Matcher mEqualSign = p.matcher(cur);
			if (mEqualSign.find())
				return false;
			p = Pattern.compile("(system\\.out)|(system.err)|(log(ger)?(\\(\\))?\\.(\\w*?)\\()",Pattern.CASE_INSENSITIVE);
			m = p.matcher(cur);
			if (m.find())
			{
				return true;
			}
			return false;
		}
	}
	
	static String dfsTraverseCallGraphToGenerateLogPattern(DefaultDirectedGraph<InterProcNode, DefaultEdge> callGraph,
			InterProcNode entryVertex, boolean condition, boolean loop,
			HashMap<InterProcNode, ArrayList<DefaultEdge>> branchChooseEdgeMap,
			ArrayList<InterProcNode> astPath, boolean may) {
		
//		if (entryVertex.toString().contains("147"))
//			System.out.println("debug");
		
		String result = "";
		
		if (may == true) {
			entryVertex.mayInTraverse = true;
		}
		
		astPath.add(entryVertex);
		
//		if (entryVertex.toString().contains("TYPE��MethodDeclaration,BceFundPoolService.java,150��173,false"))
//			System.out.println("debug");
		
		if (callGraph.outDegreeOf(entryVertex) > 0) {
			ArrayList<DefaultEdge> edgeList = new ArrayList<>();
			if (branchChooseEdgeMap.containsKey(entryVertex)) {
				edgeList = branchChooseEdgeMap.get(entryVertex);
				if (edgeList == null)
					return result;
			} else {
				edgeList.addAll(callGraph.outgoingEdgesOf(entryVertex));
			}
			
			for(DefaultEdge edge : edgeList) {
//				System.out.println(edge);
				// if the vertex has already been visited
				if (result.contains("RETURN") && may==false) {
					break;
				} else if (result.contains("RETMAY")) {
					may = true;
				}
				String subSeq = "";
				InterProcNode vertex = callGraph.getEdgeTarget(edge);
				
				if (vertex.astNode instanceof ReturnStatement) {
					if (may == false) {
						subSeq = "RETURN";
						result += subSeq;
						String childrenNodeGenerateSequence = dfsTraverseCallGraphToGenerateLogPattern(callGraph, vertex, true, loop, branchChooseEdgeMap, astPath, may);
						if (childrenNodeGenerateSequence.contains(".java")) {
							result += childrenNodeGenerateSequence;
						}
						break;
					} else {
						subSeq = "RETMAY";
						subSeq += dfsTraverseCallGraphToGenerateLogPattern(callGraph, vertex, condition, loop, branchChooseEdgeMap, astPath, may);
					}
				} else if (vertex.astNode instanceof BreakStatement) {
					subSeq = "BREAK";
					result += subSeq;
					if (may == true)
						vertex.mayInTraverse = true;
					astPath.add(vertex);
					break;
				} else if (vertex.astNode instanceof IfStatement||
						vertex.astNode instanceof SwitchStatement) {
					// did not add ifs in astPath yet
					if (may == true)
						vertex.mayInTraverse = true;
					astPath.add(vertex);
					if (branchChooseEdgeMap.containsKey(vertex)) {
						ArrayList<DefaultEdge> toExeEdgeList = branchChooseEdgeMap.get(vertex);
						if (branchChooseEdgeMap.get(vertex) == null) {
							continue;
						} else {
							// toExeEdgeList size == 1 means that this round will not traverse all possibilities
							if (toExeEdgeList.size() == 1)  {
								DefaultEdge e = toExeEdgeList.get(0);
								
								
								InterProcNode nextVisitBranchNode = callGraph.getEdgeTarget(e); 
								String childrenNodeGenerateSequence = dfsTraverseCallGraphToGenerateLogPattern(callGraph, nextVisitBranchNode, true, loop, branchChooseEdgeMap, astPath, false);
								
								if (childrenNodeGenerateSequence.contains("java")) {
									subSeq = "(" + childrenNodeGenerateSequence + ")";
								} else if(childrenNodeGenerateSequence.contains("RETURN")) {
									subSeq =  childrenNodeGenerateSequence;
								}
							} else {
								ArrayList<String> childrenNodeSequenceList = new ArrayList<>();
								for (DefaultEdge e : toExeEdgeList) {
									
									if (e == null) {
										continue;
									}
									
									InterProcNode nextVisitBranchNode = callGraph.getEdgeTarget(e);
									String childrenNodeGenerateSequence = dfsTraverseCallGraphToGenerateLogPattern(callGraph, nextVisitBranchNode, true, loop, branchChooseEdgeMap, astPath, false);
									childrenNodeGenerateSequence.replaceAll("RETURN", "");
									childrenNodeGenerateSequence.replaceAll("BREAK", "");
									
									if (childrenNodeGenerateSequence.contains("java")) {
										childrenNodeSequenceList.add(childrenNodeGenerateSequence);
									}
								}
								if (childrenNodeSequenceList.size() > 1) {
									subSeq = "(" + Joiner.on("|").join(childrenNodeSequenceList) + ")";
								}
							}
						}
					} else {
						for( DefaultEdge e : callGraph.outgoingEdgesOf(vertex)) {
							InterProcNode nextVisitBranchNode = callGraph.getEdgeTarget(e);
							subSeq += dfsTraverseCallGraphToGenerateLogPattern(callGraph, nextVisitBranchNode, true, loop, branchChooseEdgeMap, astPath, true);
						}
						
					}
				} else if (vertex.astNode instanceof EnhancedForStatement||
						vertex.astNode instanceof ForStatement ||
						vertex.astNode instanceof WhileStatement) {
					
					if (may == true) {
						assert !branchChooseEdgeMap.containsKey(vertex);
						vertex.mayInTraverse = true;
					}
					astPath.add(vertex);
					
					if (branchChooseEdgeMap.containsKey(vertex)) {
						if (branchChooseEdgeMap.get(vertex) == null) {
							continue;
						} else {
							assert branchChooseEdgeMap.get(vertex).size() == 1;
							
							DefaultEdge e = branchChooseEdgeMap.get(vertex).get(0);
							InterProcNode nextVisitBranchNode = callGraph.getEdgeTarget(e);
							String childrenNodeGenerateSequence = dfsTraverseCallGraphToGenerateLogPattern(callGraph, nextVisitBranchNode, condition, true, branchChooseEdgeMap, astPath, may);
							if(childrenNodeGenerateSequence.contains("java")) {
								subSeq = "(" + childrenNodeGenerateSequence + ")+";
							}
						}
					} else {
						for( DefaultEdge e : callGraph.outgoingEdgesOf(vertex)) {
							InterProcNode nextVisitBranchNode = callGraph.getEdgeTarget(e);
							dfsTraverseCallGraphToGenerateLogPattern(callGraph, nextVisitBranchNode, condition, true, branchChooseEdgeMap, astPath, true);
						}
					}

				} else {
					String childrenNodeGenerateSequence = dfsTraverseCallGraphToGenerateLogPattern(callGraph, vertex, condition, loop, branchChooseEdgeMap, astPath, may);
					if(childrenNodeGenerateSequence.contains("java")) {
						subSeq = "(" + childrenNodeGenerateSequence + ")";
					}
				}
				result += subSeq;
			}
		} else {
			if (entryVertex.isLog) {
				String splitRegex = "";
				if (File.separator.equals("\\")) {
					splitRegex = "\\\\";
				} else {
					splitRegex = "/";
				}
				String[] temp = entryVertex.filePath.split(splitRegex);
				String fileName = temp[temp.length-1];				
				String partSeq = "\\[" + fileName + ":" + entryVertex.cu.getLineNumber(entryVertex.astNode.getStartPosition()) + "\\]";
				result = partSeq;
			}
		}
		
		if (((result.contains("RETURN")||result.contains("RETMAY")) && entryVertex.astNode instanceof MethodDeclaration))
			return result.replaceAll("RETURN", "").replaceAll("RETMAY", "");
		
		
		if (result.contains("BREAK") && 
				(entryVertex.astNode instanceof ForStatement || entryVertex.astNode instanceof EnhancedForStatement 
						|| entryVertex.astNode instanceof WhileStatement || entryVertex.astNode instanceof SwitchStatement 
						|| entryVertex.astNode instanceof Block)) {
			return result.replaceAll("BREAK", "");
		}
		
		return result;
	}
	
	static String dfsTraverseCallGraphIsLogExist(DefaultDirectedGraph<InterProcNode, DefaultEdge> callGraph,
			InterProcNode entryVertex, boolean condition, boolean loop) {
		
		String result = "";
//		System.out.println(entryVertex);
		if (callGraph.outDegreeOf(entryVertex) > 0) {
			for(DefaultEdge edge : callGraph.outgoingEdgesOf(entryVertex)) {
//				System.out.println(edge);
				// if the vertex has already been visited
				String subSeq = "";
				InterProcNode vertex = callGraph.getEdgeTarget(edge);
				if (vertex.astNode instanceof IfStatement||
						vertex.astNode instanceof SwitchStatement) {
					
						String childrenNodeGenerateSequence = dfsTraverseCallGraphIsLogExist(callGraph, vertex, true, loop);
						if(childrenNodeGenerateSequence.contains("java")) {
							subSeq = "(" + childrenNodeGenerateSequence + ")?";
						}
				} else if (vertex.astNode instanceof EnhancedForStatement||
						vertex.astNode instanceof ForStatement ||
						vertex.astNode instanceof WhileStatement) {
					String childrenNodeGenerateSequence = dfsTraverseCallGraphIsLogExist(callGraph, vertex, condition, true);
					if(childrenNodeGenerateSequence.contains("java")) {
						subSeq = "(" + childrenNodeGenerateSequence + ")*";
					}
					
				} else {
					String childrenNodeGenerateSequence = dfsTraverseCallGraphIsLogExist(callGraph, vertex, condition, loop);
					if(childrenNodeGenerateSequence.contains("java")) {
						subSeq = "(" + childrenNodeGenerateSequence + ")";
					}
				}

				result += subSeq;
			}
			
		} else {
			if (entryVertex.isLog) {
				String splitRegex = "";
				if (File.separator.equals("\\")) {
					splitRegex = "\\\\";
				} else {
					splitRegex = "/";
				}
				String[] temp = entryVertex.filePath.split(splitRegex);
				String fileName = temp[temp.length-1];
				
				String partSeq = "\\[" + fileName + ":" + entryVertex.cu.getLineNumber(entryVertex.astNode.getStartPosition()) + "\\]";
				
				if(condition) {
					result = "(" + partSeq + ")?";
				} else {	
					result = partSeq;
				}

			}
		}
		return result;
	}
	
	/**
	 * This method is used to find all log-impact branches and 
	 * put their possibilities to a map
	 * @param callGraph
	 * @return
	 */
	static HashMap<InterProcNode, ArrayList<ArrayList<DefaultEdge>>> extractBranchPossibility(DefaultDirectedGraph<InterProcNode, DefaultEdge> callGraph) {
		HashMap<InterProcNode, ArrayList<ArrayList<DefaultEdge>>> branchEdgeListMap = new HashMap<>();
		for (InterProcNode v : callGraph.vertexSet()) {
			if (v.astNode instanceof IfStatement || v.astNode instanceof SwitchStatement
					|| v.astNode instanceof ForStatement || v.astNode instanceof WhileStatement || v.astNode instanceof EnhancedForStatement) {
				// if the statement is related to logs
				if (dfsTraverseCallGraphIsLogExist(callGraph,v,true,false).contains("java")) {
					ArrayList<ArrayList<DefaultEdge>> edgeList = new ArrayList<>();
					
					
					if (callGraph.outDegreeOf(v) == 1) {
						ArrayList<DefaultEdge> nBranch = new ArrayList<>();
						for( DefaultEdge e : callGraph.outgoingEdgesOf(v)) {
							nBranch.add(e);
						}
						edgeList.add(nBranch);
						edgeList.add(null);
						// two branch , one is empty coz the block is empty
					}
					else {
						for( DefaultEdge e : callGraph.outgoingEdgesOf(v)) {
							ArrayList<DefaultEdge> nBranch = new ArrayList<>();
							nBranch.add(e);
							edgeList.add(nBranch);
						}
					}
					
					if (isVertexBranching(v) && hasLoopParent(callGraph, v)) { 
						ArrayList<DefaultEdge> nBranch = new ArrayList<>();
						for (int i = 0; i < edgeList.size(); i ++) {
							if (edgeList.get(i) == null)
								nBranch.add(null);
							else {
								nBranch.addAll(edgeList.get(i));
							}
						}
					}
					
					branchEdgeListMap.put(v, edgeList);
				} else {
					// if the current node's all children nodes cannot generate logs
					
					if (v.astNode instanceof IfStatement) {
						
						InterProcNode mdInterProcNode = getContainingMethodDeclarationProcNode(callGraph, v);
						if(!dfsTraverseCallGraphIsLogExist(callGraph,mdInterProcNode,false,false).contains("java"))
							continue;
						boolean addToMap = false;
						
						IfStatement ifs = (IfStatement) v.astNode;
						if (ifs.getElseStatement() != null) {
							if (ifs.getElseStatement() instanceof Block) {
								Block elseBlock = (Block)ifs.getElseStatement();
								List stmtList = elseBlock.statements();
								for(int i = 0; i < stmtList.size(); i ++) {
									Statement stmt = (Statement)stmtList.get(i);
									if (stmt instanceof ReturnStatement || stmt instanceof BreakStatement) {
										addToMap = true;
									}
								}
							}
						}
						if (ifs.getThenStatement() != null) {
							if (ifs.getThenStatement() instanceof Block) {
								Block thenBlock = (Block) ifs.getThenStatement();
								List stmtList = thenBlock.statements();
								for(int i = 0; i < stmtList.size(); i ++) {
									Statement stmt = (Statement)stmtList.get(i);
									if (stmt instanceof ReturnStatement || stmt instanceof BreakStatement) {
										addToMap = true;
									}
								}
							}
						}
						if (addToMap) {
							ArrayList<ArrayList<DefaultEdge>> edgeList = new ArrayList<>();
							if (callGraph.outDegreeOf(v) == 1) {
								ArrayList<DefaultEdge> nBranch = new ArrayList<>();
								for( DefaultEdge e : callGraph.outgoingEdgesOf(v)) {
									nBranch.add(e);
									edgeList.add(nBranch);
								}
								edgeList.add(null);
							}
							else {
								for( DefaultEdge e : callGraph.outgoingEdgesOf(v)) {
									ArrayList<DefaultEdge> nBranch = new ArrayList<>();
									nBranch.add(e);
									edgeList.add(nBranch);
								}
							}
							
							if (isVertexBranching(v) && hasLoopParent(callGraph, v)) { 
								ArrayList<DefaultEdge> nBranch = new ArrayList<>();
								for (int i = 0; i < edgeList.size(); i ++) {
									if (edgeList.get(i) == null)
										nBranch.add(null);
									else {
										nBranch.addAll(edgeList.get(i));
									}
								}
								edgeList.add(nBranch);
							}
							branchEdgeListMap.put(v, edgeList);
						}
					}
				}
			}
		}
		return branchEdgeListMap;
	}
	
	static boolean isVertexBranching(InterProcNode vertex) {
		return (vertex.astNode instanceof IfStatement) || (vertex.astNode instanceof SwitchStatement);
	}
	
	static boolean isVertexLoop(InterProcNode vertex) {
		return (vertex.astNode instanceof ForStatement) || (vertex.astNode instanceof WhileStatement) || (vertex.astNode instanceof EnhancedForStatement);
	}
	
	static boolean hasLoopParent(DefaultDirectedGraph<InterProcNode, DefaultEdge> callGraph, InterProcNode vertex) {
		
		InterProcNode currentVertex = vertex;
		while(callGraph.inDegreeOf(currentVertex) != 0) {
//			System.out.println("loop");
			if (currentVertex.astNode instanceof ForStatement ||
					currentVertex.astNode instanceof WhileStatement ||
					currentVertex.astNode instanceof EnhancedForStatement) {
				return true;
			}
			
			Set<DefaultEdge> inEdges = callGraph.incomingEdgesOf(currentVertex);
			Iterator<DefaultEdge> iterator = inEdges.iterator();
			if (iterator.hasNext()) {
				DefaultEdge edge = iterator.next();
				currentVertex = callGraph.getEdgeSource(edge);
			} else {
				return false;
			}
		}
		return false;
	}
	
	static void combine(int index, HashMap<InterProcNode, ArrayList<DefaultEdge>> current, HashMap<InterProcNode,ArrayList<ArrayList<DefaultEdge>>> map, List<HashMap<InterProcNode, ArrayList<DefaultEdge>>> list) {
		
		if (index == map.size()) {
			HashMap<InterProcNode, ArrayList<DefaultEdge>> newMap = new HashMap<>();
//			System.out.println(current);
			for(InterProcNode key : current.keySet()) {
				newMap.put(key, current.get(key));
			}
//			System.out.println("mapfinished");
			list.add(newMap);
		} else {
			Object currentKey = map.keySet().toArray()[index];
			for (ArrayList<DefaultEdge> edge : map.get(currentKey)) {
				current.put((InterProcNode) currentKey, edge);
				combine(index+1, current, map, list);
				current.remove(currentKey);
			}
		}
		
	}
	
	static InterProcNode getContainingMethodDeclarationProcNode(DefaultDirectedGraph<InterProcNode, DefaultEdge> callGraph, InterProcNode currentNode) {
		InterProcNode resultNode = currentNode;
		while (! (resultNode.astNode instanceof MethodDeclaration)) {
			Set<DefaultEdge> edgeSet = callGraph.incomingEdgesOf(resultNode);
			Iterator<DefaultEdge> iterator = edgeSet.iterator();
			while(iterator.hasNext()) {
				DefaultEdge edge = iterator.next();
				resultNode = callGraph.getEdgeSource(edge);
				break;
			}
		}
		return resultNode;
	}
//	
//	static String testSwitch(int a) { 
//		switch (a) {
//		case 1:
//			a = a+1;
//			return "abc";
//		case 2:
//			return "def";
//		case 3:
//			return "ghi";
//		default:
//			return "none";
//		}
//	}
}	