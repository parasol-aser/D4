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
		return Activator.getImageDescriptor("buggy-tiny-orange.png");
	}

	@SuppressWarnings("unchecked")
	@Override
	protected void createChildren(ArrayList<LinkedList<String>> traceMsg, String fix) {
		// TODO Auto-generated method stub
		TraceNode tracenode = new TraceNode(this, traceMsg);
		FixNode fixnode = new FixNode(this, fix);
		super.children.add(tracenode);
		super.children.add(fixnode);
	}

}
