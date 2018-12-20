package edu.tamu.aser.tide.nodes;

import java.util.HashSet;

import org.eclipse.core.resources.IFile;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ssa.SSAInstruction;

public abstract class MemNode implements INode {
	final int TID;
	String sig;
	int line;
	PointerKey pointerKey;
	private HashSet<String> objsigs = new HashSet<>();
	public String prefix;
	protected CGNode node;
	public SSAInstruction inst;
	public IFile file;
	public String localSig = "";


	public MemNode(int curTID, String instSig, int sourceLineNum, PointerKey key,
			String prefix, CGNode node, SSAInstruction inst, IFile file) {//current used
		this.TID = curTID;
		this.sig = instSig;
		this.line = sourceLineNum;
		this.pointerKey = key;
		this.prefix = prefix;
		this.node = node;
		this.inst = inst;
		this.file = file;
	}

	public MemNode copy(int line){
		//update sig
		String new_sig = sig.substring(0, sig.lastIndexOf(":") + 1) + line;
		if(this instanceof ReadNode)
			return new ReadNode(TID, new_sig, line, pointerKey, prefix, node, inst, file);
		else
			return new WriteNode(TID, new_sig, line, pointerKey, prefix, node, inst, file);
	}

	public void setLocalSig(String lsig){
		this.localSig = lsig;
	}

	public String getLocalSig() {
		return localSig;
	}

	public IFile getFile(){
		return file;
	}


	@Override
	public boolean equals(Object that){
		if(that instanceof MemNode){
			MemNode thatnode = (MemNode) that;
			if((this instanceof ReadNode && that instanceof ReadNode)
					|| (this instanceof WriteNode && that instanceof WriteNode)){
				if(this.objsigs.equals(thatnode.objsigs)
						&& this.prefix.equals(thatnode.prefix)
						&& this.localSig.equals(((MemNode) that).localSig)
						&& this.line == ((MemNode) that).line){
					if(this.file != null && thatnode.file != null){
						if(this.file.equals(thatnode.file))
							return true;
					}else if(this.file == null && thatnode.file == null){
						return true;
					}else{
						return false;
					}				
				}
			}
		}
		return false;
	}

	public void replaceObjSig(HashSet<String> new_sigs){
		objsigs.clear();
		objsigs.addAll(new_sigs);
	}

	public CGNode getBelonging(){
		return node;
	}

	public void setBelonging(CGNode node){
		this.node = node;
	}

	public String getPrefix(){
		return prefix;
	}

	public void setPrefix(String prefix){
		this.prefix = prefix;
	}

	public void addObjSig(String sig){
		objsigs.add(sig);
	}

	public void setObjSigs(HashSet<String> sigs){
		objsigs.addAll(sigs);
	}

	public HashSet<String> getObjSig(){
		return objsigs;
	}

	public PointerKey getPointer(){
		return pointerKey;
	}

	public int getTID() {
		return TID;
	}

	public String getSig(){
		return sig;
	}

	public void setSig(String sig){
		this.sig = sig;
	}

	public int getLine(){
		return line;
	}

	public boolean setLine(int line){
		if(this.line != line){
			this.line = line;
			return true;
		}
		return false;
	}

	public SSAInstruction getInst(){
		return inst;
	}


}
