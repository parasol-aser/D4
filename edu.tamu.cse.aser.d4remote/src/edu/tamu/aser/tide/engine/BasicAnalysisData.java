/**
 * This file is licensed under the University of Illinois/NCSA Open Source License. See LICENSE.TXT for details.
 */
package edu.tamu.aser.tide.engine;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

import com.ibm.wala.analysis.pointers.BasicHeapGraph;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.propagation.HeapModel;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.cha.IClassHierarchy;

import edu.tamu.aser.tide.trace.INode;
import edu.tamu.aser.tide.trace.StartNode;

/**
 *
 * @author Mohsen Vakilian
 * @author Stas Negara
 *
 */
public class BasicAnalysisData {

	public int curTID,curGID;
	public int getIncrementGID()
	{
		curGID++;
		return curGID;
	}
	public HashMap<String, HashMap<Integer,String>> variableReadMap= new HashMap<String, HashMap<Integer,String>>();
	public HashMap<String, HashMap<Integer,String>> variableWriteMap= new HashMap<String, HashMap<Integer,String>>();
	public HashSet<CGNode> alreadyProcessedNodes = new HashSet<CGNode>();
	public LinkedList<INode> trace = new LinkedList<INode>();
	public HashMap<Integer, StartNode> mapOfStartNode = new HashMap<>();

	public final IClassHierarchy classHierarchy;

	public final CallGraph callGraph;

	public final PointerAnalysis pointerAnalysis;

	public final HeapModel heapModel;

	public final BasicHeapGraph basicHeapGraph;

	public final LinkedList<CGNode> threadNodes;

	public final HashMap<String,CGNode> threadSigNodeMap;
	public final KeshmeshCGModel model;

	public BasicAnalysisData(KeshmeshCGModel model, IClassHierarchy classHierarchy, CallGraph callGraph, PointerAnalysis pointerAnalysis, HeapModel heapModel, BasicHeapGraph basicHeapGraph,  LinkedList<CGNode> threadNodes,HashMap<String, CGNode> threadSigNodeMap) {

		this.model = model;

		this.classHierarchy = classHierarchy;
		this.callGraph = callGraph;
		this.pointerAnalysis = pointerAnalysis;
		this.heapModel = heapModel;
		this.basicHeapGraph = basicHeapGraph;
		this.threadNodes = threadNodes;
		this.threadSigNodeMap = threadSigNodeMap;
	}

}
