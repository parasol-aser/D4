package edu.tamu.aser.tide.trace;

import com.ibm.wala.ipa.callgraph.CGNode;

public interface INode {
//	public int getGID();
	public int getTID();
	public String toString();
	public CGNode getBelonging();

}
