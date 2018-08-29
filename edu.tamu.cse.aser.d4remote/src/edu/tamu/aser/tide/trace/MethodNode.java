package edu.tamu.aser.tide.trace;

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
//		this.gid = gid;
		this.target = target;
		this.tid = tid;
		this.line = line;
		this.file = file;
		this.inst = inst;
	}

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

//	public int getGID() {
//		// TODO Auto-generated method stub
//		return gid;
//	}

	@Override
	public int getTID() {
		// TODO Auto-generated method stub
		return tid;
	}

	@Override
	public CGNode getBelonging() {
		// TODO Auto-generated method stub
		return method;
	}

	@Override
	public String toString(){
		String classname1 = target.getMethod().getDeclaringClass().toString();
		String methodname1 = target.getMethod().getName().toString();
		String classname = method.getMethod().getDeclaringClass().toString();
		String methodname = method.getMethod().getName().toString();
		return "Call " + classname1.substring(classname1.indexOf(':') +3, classname1.length()) + "." + methodname1 +
				 " from " + classname.substring(classname.indexOf(':') +3, classname.length()) + "." + methodname;
	}

}
