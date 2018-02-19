package edu.tamu.aser.tide.graph;

import java.util.ArrayList;

import com.ibm.wala.ipa.callgraph.CGNode;

import edu.tamu.aser.tide.trace.INode;

public interface BaseGraph {

//	public ArrayList<Trace> getAllTraces();
	public boolean addTrace(CGNode node, Trace trace);
	public boolean delTrace(CGNode node);
	public boolean replaceTrace(CGNode node, Trace curTrace);
	public Trace getTrace(CGNode node);
	public boolean addEdge(INode inst, CGNode method);
	public boolean delEdge(INode inst, CGNode method);


}
