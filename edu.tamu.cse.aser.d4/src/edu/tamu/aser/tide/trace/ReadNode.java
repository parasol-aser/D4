package edu.tamu.aser.tide.trace;

import org.eclipse.core.resources.IFile;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ssa.SSAInstruction;

public class ReadNode extends MemNode{


	public ReadNode(int TID, String addr, String sig, int line)//int GID,
	{
		super(TID, addr, sig, line);
	}
	public ReadNode(int curTID, String instSig, int sourceLineNum, PointerKey key,
			String prefix, CGNode node, SSAInstruction inst, IFile file) {
		super(curTID, instSig, sourceLineNum, key, prefix, node, inst, file);
	}

//	public void addObjSig(String sig){
//		objsigs.add(sig);
//	}
//
//	public HashSet<String> getObjSig(){
//		return objsigs;
//	}

	public String toString()
	{
//		return " "+TID+" read "+addr +" "+ sig+" "+line;
		String classname = super.node.getMethod().getDeclaringClass().toString();
		String methodname = super.node.getMethod().getName().toString();
		return "Reading at line " + line + " in " + classname.substring(classname.indexOf(':') +3, classname.length()) + "." + methodname;
	}
}
