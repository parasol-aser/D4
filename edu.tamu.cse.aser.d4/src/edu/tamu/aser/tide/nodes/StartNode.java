package edu.tamu.aser.tide.nodes;

import org.eclipse.core.resources.IFile;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.util.intset.IntSetUtil;
import com.ibm.wala.util.intset.MutableIntSet;

public class StartNode extends SyncNode{

	final int parentTID;
	final int selfTID;
	final MutableIntSet TID_child = IntSetUtil.make();
	CGNode node;
	CGNode target;
	public int line;
	private IFile file;

	public StartNode(int TID, int TID_child, CGNode node, CGNode node2, int linenum, IFile file){
		this.parentTID = TID;
		this.selfTID = TID_child;
		this.node = node;
		this.target = node2;
		this.line = linenum;
		this.file = file;
	}

	public IFile getFile(){
		return file;
	}

	public CGNode getTarget(){
		return target;
	}

	public int getTID() {
		return parentTID;
	}
	public int getParentTID(){
		return parentTID;
	}

	public int getSelfTID() {
		return selfTID;
	}
	public void addChild(int child){
		TID_child.add(child);
	}
	public void addChildren(MutableIntSet children){
		TID_child.addAll(children);
	}
	public MutableIntSet getTID_Child()
	{
		return TID_child;
	}

	public String toString(){
		if(node == null){//main
			String tclassname = target.getMethod().getDeclaringClass().toString();
			String tmethodname = target.getMethod().getName().toString();
			return "Main thread created by " + tclassname.substring(tclassname.indexOf(':') +3, tclassname.length()) + "." + tmethodname + " (line " + line + ")";
		}else{
			String pclassname = node.getMethod().getDeclaringClass().toString();
			String pmethodname = node.getMethod().getName().toString();
			return "Child thread created by " + pclassname.substring(pclassname.indexOf(':') +3, pclassname.length()) + "." + pmethodname  + " (line " + line + ")";
		}
	}

	@Override
	public CGNode getBelonging() {
		return node;
	}

	public void removeChild(int kid) {
		TID_child.remove(kid);
	}

	@Override
	public int getLine() {
		return line;
	}
}
