package edu.tamu.aser.tide.views;

import java.util.ArrayList;
import java.util.LinkedList;

import org.eclipse.jface.resource.ImageDescriptor;

import edu.tamu.aser.tide.engine.TIDEDeadlock;
import edu.tamu.aser.tide.plugin.Activator;

public class DeadlockNode extends TreeNode{
	protected TIDEDeadlock deadlock;
	protected String name;

	public DeadlockNode(TreeNode parent, TIDEDeadlock deadlock) {
		this(parent, deadlock, false);
	}

	public DeadlockNode(TreeNode parent, TIDEDeadlock deadlock, boolean isNewest) {
		super(parent);
		this.deadlock = deadlock;
		this.isNewest = isNewest;
		initialNode();
	}

	private void initialNode() {
		name = deadlock.deadlockMsg;
		createChildren(deadlock.traceMsg, deadlock.fixMsg);
	}

	public TIDEDeadlock getDeadlock(){
		return deadlock;
	}

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return name;
	}

	@Override
	public ImageDescriptor getImage() {
		return Activator.getImageDescriptor("lock-icon.png");
	}

	@SuppressWarnings("unchecked")
	@Override
	protected void createChildren(ArrayList<LinkedList<String>> traces, String fix) {
		LinkedList<String> trace1 = traces.get(0);
		LinkedList<String> trace2 = traces.get(1);
		if(this instanceof DeadlockNode){
			//1st lockpair
			String name1 = "Trace of " + deadlock.lp1.lock1.getInstSig() + " => " + deadlock.lp1.lock2.getInstSig() + " :";
			SubTraceNode subtrace1 = new SubTraceNode(this, name1, trace1);
			//2nd lockpair
			String name2 = "Trace of " + deadlock.lp2.lock1.getInstSig() + " => " + deadlock.lp2.lock2.getInstSig() + " :";
			SubTraceNode subtrace2 = new SubTraceNode(this, name2, trace2);
			super.children.add(subtrace1);
			super.children.add(subtrace2);
		}
	}

}
