package edu.tamu.aser.tide.nodes;

import org.eclipse.core.resources.IFile;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;

public class MethodNode implements INode {

	private CGNode method, target;
	private int tid;
	private int line;
	private IFile file;
	private SSAAbstractInvokeInstruction inst;

	public MethodNode(CGNode method, CGNode target, int tid, int line, IFile file, SSAAbstractInvokeInstruction inst) {// int gid,
		this.method = method;
		this.target = target;
		this.tid = tid;
		this.line = line;
		this.file = file;
		this.inst = inst;
	}

	public boolean generalEqual(MethodNode that){
		if(this.method.equals(that.method)
				&& this.target.equals(that.target)){
			return true;
		}
		return false;
	}

//	@Override
//	public boolean equals(Object obj) {
//		if(obj instanceof MethodNode){
//			MethodNode that = (MethodNode) obj;
//			if(this.inst.equals(that.inst))
//				return true;
//		}
//		return super.equals(obj);
//	}

	public SSAAbstractInvokeInstruction getInvokeInst(){
		return inst;
	}

	public int getLine(){
		return line;
	}

	public IFile getFile(){
		return file;
	}

	public CGNode getTarget(){
		return target;
	}

	@Override
	public int getTID() {
		return tid;
	}

	@Override
	public CGNode getBelonging() {
		return method;
	}

	@Override
	public String toString(){
		String classname1 = target.getMethod().getDeclaringClass().toString();
		String methodname1 = target.getMethod().getName().toString();
		String classname = method.getMethod().getDeclaringClass().toString();
		String methodname = method.getMethod().getName().toString();
		String cn1 = null;
		String cn = null;
		boolean jdk = false;
		if(classname1.contains("java/util/")){
			cn1 = classname1.substring(classname1.indexOf("L") +1, classname1.length() -1);
		}else{
			cn1 = classname1.substring(classname1.indexOf(':') +3, classname1.length());
		}
		if(classname.contains("java/util/")){
			jdk = true;
			cn = classname.substring(classname.indexOf("L") +1, classname.length() -1);
		}else{
			cn = classname.substring(classname.indexOf(':') +3, classname.length());
		}
		if(jdk){
			return "(Ext Lib) Call " + cn1 + "." + methodname1 + " from " + cn + "." + methodname  + " (line " + line + ")";
		}else{
			return "Call " + cn1 + "." + methodname1 + " from " + cn + "." + methodname  + " (line " + line + ")";
		}
	}

}
