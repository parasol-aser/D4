package edu.tamu.aser.tide.shb;

import java.util.ArrayList;
import java.util.HashSet;

import edu.tamu.aser.tide.nodes.INode;

public class SHBEdge {

	private INode node;
	private String cgnode;
	private HashSet<Integer> tids = new HashSet<>();	//edge tid mapping


	public SHBEdge(INode node, String n) {
		this.node = node;
		this.cgnode = n;
		int tid = node.getTID();
		if(!tids.contains(tid)){
			tids.add(tid);
		}
	}

	public INode getSource(){
		return node;
	}

	/**
	 * return sig of cgnode
	 * @return
	 */
	public String getSink(){
		return cgnode;
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
