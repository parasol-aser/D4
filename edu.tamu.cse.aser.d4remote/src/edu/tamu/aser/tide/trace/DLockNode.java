package edu.tamu.aser.tide.trace;

import java.util.HashSet;

import org.eclipse.core.resources.IFile;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.util.intset.OrdinalSet;

public class DLockNode extends SyncNode{

	final int TID;
	String lock;

	public String instSig;
	final int line;
	public SSAInstruction inst;
	PointerKey key;
	OrdinalSet<InstanceKey> instances;
	private HashSet<String> locksigs = new HashSet<>();
	private String prefix;
	private CGNode node;
	public IFile file;

	public DLockNode(int TID, String inString, String lockobj, int line, SSAInstruction createinst)
	{//int GID,
//		this.GID = GID;
		this.TID = TID;
		this.lock = lockobj;
		this.line = line;
		this.inst = createinst;
		this.instSig = inString;
	}

	public DLockNode(int curTID, String instSig2, int sourceLineNum, PointerKey key,
			OrdinalSet<InstanceKey> instances, CGNode node, SSAInstruction createinst,IFile file ) {
		this.TID = curTID;
		this.instSig = instSig2;
		this.line = sourceLineNum;
		this.key = key;
		this.instances = instances;
//		this.prefix = prefix;
		this.node = node;
		this.inst = createinst;
		this.file = file;
	}

	public IFile getFile(){
		return file;
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

	public void replaceLockSig(HashSet<String> new_sigs) {
		this.locksigs.clear();
		this.locksigs.addAll(new_sigs);
	}

	public String getInstSig(){
		return instSig;
	}

	public SSAInstruction getCreateInst() {
		return inst;
	}

	public int getTID() {
		return TID;
	}
//	public int getGID() {
//		return GID;
//	}
	public String getLockString()
	{
		return lock;
	}

	public int getLine() {
		return line;
	}

	public String toString(){
		String methodname = node.getMethod().getName().toString();
		return "Lock at line "+ line + " in " + instSig.substring(0, instSig.indexOf(':')) +"." + methodname;
//		return " "+TID+" lock "+ locksigs;
	}

	public int hashCode()
	{
//		return locksigs.hashCode();//return the lock string
		if(key == null)
			return locksigs.hashCode();
		else
			return locksigs.hashCode() + key.hashCode();
	}

	public boolean equals(Object o)
	{
		if(o instanceof DLockNode){
			if(((DLockNode) o).getLockSig().equals(locksigs) && ((DLockNode) o).instSig.equals(instSig))
				return true;
//			if(lock.equals(((DLockNode) o).getLockString()))
//				return true;
		}
		return false;
	}

	@Override
	public int getSelfTID() {
		// TODO Auto-generated method stub
		return 0;
	}

}
