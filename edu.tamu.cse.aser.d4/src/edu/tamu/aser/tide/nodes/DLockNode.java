package edu.tamu.aser.tide.nodes;

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
	int line;
	public SSAInstruction inst;
	PointerKey key;
	OrdinalSet<InstanceKey> instances;
	private HashSet<String> locksigs = new HashSet<>();
	private String prefix;
	private CGNode node;
	public IFile file;

	public DLockNode(int curTID, String instSig, int sourceLineNum, PointerKey key,
			OrdinalSet<InstanceKey> instances, CGNode node, SSAInstruction createinst,IFile file ) {
		this.TID = curTID;
		this.instSig = instSig;
		this.line = sourceLineNum;
		this.key = key;
		this.instances = instances;
//		this.prefix = prefix;
		this.node = node;
		this.inst = createinst;
		this.file = file;
	}

	public DLockNode copy(int line){
		String new_instSig = instSig.substring(0, instSig.lastIndexOf(":") + 1) + line;
		return new DLockNode(TID, new_instSig, line, key, instances, node, inst, file);
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

	public String getLockString(){
		return lock;
	}

	public int getLine() {
		return line;
	}

	public boolean setLine(int line){
		if(this.line != line){
			this.line = line;
			return true;
		}
		return false;
	}

	public String toString(){
		String classname = node.getMethod().getDeclaringClass().toString();
		String methodname = node.getMethod().getName().toString();
		String cn = null;
		boolean jdk = false;
		if(classname.contains("java/util/")){
			jdk = true;
			cn = classname.substring(classname.indexOf("L") +1, classname.length() -1);
		}else{
			cn = classname.substring(classname.indexOf(':') +3, classname.length());
		}
		if(jdk){
			return "(Ext Lib) Lock in " + cn +"." + methodname + " (line " + line + ")";
		}else{
			return "Lock in " + cn +"." + methodname + " (line " + line + ")";
		}
//		return "Lock in " + instSig.substring(0, instSig.indexOf(':')) +"." + methodname + " (line " + line + ")";
	}

	@Override
	public int hashCode(){
//		return locksigs.hashCode();
		if(key == null)
			return locksigs.hashCode();
		else
			return locksigs.hashCode() + key.hashCode();
	}

	@Override
	public boolean equals(Object o){
		if(o instanceof DLockNode){
			DLockNode that = (DLockNode) o;
			if(this.node.equals(that.node)
					&& this.instSig.equals(that.instSig)
					&& this.line == that.line)
				return true;
		}
		return false;
	}

	@Override
	public int getSelfTID() {
		return TID;
	}

}
