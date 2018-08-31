package edu.tamu.aser.tide.nodes;

import com.ibm.wala.ipa.callgraph.CGNode;

public interface INode {
	public int getTID();
	public String toString();
	public CGNode getBelonging();

}
