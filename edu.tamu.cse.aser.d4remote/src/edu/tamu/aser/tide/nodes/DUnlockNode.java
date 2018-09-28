package edu.tamu.aser.tide.nodes;

import java.util.HashSet;

import org.eclipse.core.resources.IFile;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.util.intset.OrdinalSet;

public class DUnlockNode extends SyncNode{

	final int TID;
	int sourceLineNum;
	String lock, instSig;
	PointerKey key;
	OrdinalSet<InstanceKey> instances;
	private HashSet<String> locksigs = new HashSet<>();
	private String prefix;
	private CGNode node;

	public DUnlockNode(int curTID, String instSig2, int sourceLineNum, PointerKey key,
			OrdinalSet<InstanceKey> instances, CGNode node, int sln) {
		this.TID = curTID;
		this.instSig = instSig2;
		this.sourceLineNum = sourceLineNum;
		this.key = key;
		this.instances = instances;
//		this.prefix = prefix;
		this.node = node;
		this.sourceLineNum = sln;
	}

	public CGNode getBelonging(){
		return node;
	}

	public PointerKey getPointer(){
		return key;
	}

	public String getPrefix(){
		return prefix;
	}

	public void addLockSig(String sig){
		locksigs.add(sig);
	}

	public HashSet<String> getLockSig(){
		return locksigs;
	}

	public int hashCode(){
//		return locksigs.hashCode();
		if(key == null)
			return locksigs.hashCode();
		else
			return locksigs.hashCode() + key.hashCode();
	}

	public boolean equals(Object o){
		if(o instanceof DUnlockNode){
			if(((DUnlockNode) o).getLockSig().equals(locksigs) && ((DUnlockNode) o).instSig.equals(instSig))
				return true;
		}
		return false;
	}



	public String getLockedObj() {
		return lock;
	}

	public int getTID() {
		return TID;
	}

	public String getLockString(){
		return lock;
	}

	public String toString(){
		String methodname = node.getMethod().getName().toString();
		return "UnLock in " + instSig.substring(0, instSig.indexOf(':')) +"." + methodname;
//		return "Unlock on " + instSig + " on line " + sourceLineNum;
	}

	@Override
	public int getSelfTID() {
		return TID;
	}

	@Override
	public IFile getFile() {
		return null;
	}

	@Override
	public int getLine() {
		return sourceLineNum;
	}
}
