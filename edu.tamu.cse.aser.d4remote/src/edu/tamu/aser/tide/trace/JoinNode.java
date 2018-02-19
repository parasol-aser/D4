package edu.tamu.aser.tide.trace;

import org.eclipse.core.resources.IFile;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.util.intset.IntSetUtil;
import com.ibm.wala.util.intset.MutableIntSet;

public class JoinNode extends SyncNode{

	final int parentTID;//direct parent
	final int selfTID;
	final MutableIntSet TID_parents = IntSetUtil.make();
	CGNode node;// -> tid_p
	CGNode target;// -> tid_kid
	int line;
	private IFile file;

	public JoinNode(int TID, int TID_end, CGNode n, CGNode node, int line, IFile file)//int GID,
	{
//		this.GID = GID;
		this.parentTID = TID;
		this.selfTID = TID_end;
		this.node = n;
		this.target = node;
		this.line = line;
		this.file = file;
	}

	public CGNode getTarget() {
		return target;
	}

//	public int getGID() {
//		return GID;
//	}
	public int getTID() {
		return parentTID;
	}
	public int getParentTID(){
		return parentTID;
	}

	public int getSelfTID() {
		return selfTID;
	}

	public String toString()
	{
//		return " "+TID+" join "+TID_end;
		String pclassname = node.getMethod().getDeclaringClass().toString();
		String pmethodname = node.getMethod().getName().toString();
		return "Thread " + selfTID + " joins Thread" + parentTID + " in " + pclassname.substring(pclassname.indexOf(':') +3, pclassname.length()) + "." + pmethodname + " on line " + line;
	}

	@Override
	public CGNode getBelonging() {
		// TODO Auto-generated method stub
		return node;
	}

	@Override
	public IFile getFile() {
		return file;
	}

	@Override
	public int getLine() {
		return line;
	}

	public void addParent(int parent){
		TID_parents.add(parent);
	}
	public void addParents(MutableIntSet parents){
		TID_parents.addAll(parents);
	}
	public MutableIntSet getTID_Parents()
	{
		return TID_parents;
	}
	public void removeParent(int pid) {
		TID_parents.remove(pid);
	}
}
