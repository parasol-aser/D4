package edu.tamu.aser.tide.graph;

import java.util.ArrayList;
import java.util.HashSet;

import com.ibm.wala.ipa.callgraph.CGNode;

import edu.tamu.aser.tide.trace.INode;

public class SHBEdge {

	private INode node;
	private CGNode n;
	private HashSet<Integer> tids = new HashSet<>();	//edge tid mapping


	public SHBEdge(INode node, CGNode n) {
		this.node = node;
		this.n = n;
		int tid = node.getTID();
		if(!tids.contains(tid)){
			tids.add(tid);
		}
	}

	public INode getSource(){
		return node;
	}

	public CGNode getSink(){
		return n;
	}

	public void includeTid(int tid){
		tids.add(tid);
	}

	public void includeTids(ArrayList<Integer> tids){
		this.tids.addAll(tids);
	}

	public void removeTid(int tid){
		if(tids.contains(tid)){
			tids.remove(tid);
		}
	}

	public HashSet<Integer> getEdgeTids(){
		return tids;
	}

	public boolean doesIncludeTid(int tid){
		return tids.contains(tid);
	}



}
