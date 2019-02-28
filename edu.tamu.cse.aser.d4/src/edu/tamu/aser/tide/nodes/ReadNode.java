package edu.tamu.aser.tide.nodes;

import org.eclipse.core.resources.IFile;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ssa.SSAInstruction;

public class ReadNode extends MemNode{

	public ReadNode(int curTID, String instSig, int sourceLineNum, PointerKey key,
			String prefix, CGNode node, SSAInstruction inst, IFile file) {
		super(curTID, instSig, sourceLineNum, key, prefix, node, inst, file);
	}

	public String toString(){
		String classname = super.node.getMethod().getDeclaringClass().toString();
		String methodname = super.node.getMethod().getName().toString();
		String cn = null;
		boolean jdk = false;
		if(classname.contains("java/util/")){
			jdk = true;
			cn = classname.substring(classname.indexOf("L") +1, classname.length() -1).replace("$", "");
		}else{
			cn = classname.substring(classname.indexOf(':') +3, classname.length()).replace("$", "");
		}
		if(jdk){
				return "(Ext Lib) Read on " + super.getPrefix() + " in " +  cn + "." + methodname + " (line " + line + ")";
		}else{
				return "Read on " + super.getPrefix() + " in " +  cn + "." + methodname + " (line " + line + ")";
		}
	}
}
