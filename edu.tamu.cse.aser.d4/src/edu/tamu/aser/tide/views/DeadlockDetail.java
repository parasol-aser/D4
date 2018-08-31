package edu.tamu.aser.tide.views;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

import org.eclipse.jface.resource.ImageDescriptor;

import edu.tamu.aser.tide.engine.TIDEDeadlock;
import edu.tamu.aser.tide.plugin.Activator;

public class DeadlockDetail extends TreeNode{
	protected String name;
	protected HashMap<String, DeadlockNode> map = new HashMap<>();

	public DeadlockDetail(TreeNode parent) {
		super(parent);
		this.name = "Deadlock Detail";
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public ImageDescriptor getImage() {
		return Activator.getImageDescriptor("folder_icon.gif");
	}

	@Override
	protected void createChildren(ArrayList<LinkedList<String>> trace, String fix) {
		// TODO Auto-generated method stub
	}

	protected void createChild(TIDEDeadlock deadlock) {
		createChild(deadlock, false);
	}

	@SuppressWarnings("unchecked")
	protected void createChild(TIDEDeadlock deadlock, boolean isNewest) {
		//translate to deadlocknode
		DeadlockNode node = new DeadlockNode(this, deadlock, isNewest);
		super.children.add(node);
		map.put(deadlock.deadlockMsg, node);
	}

	protected void removeChild(TIDEDeadlock deadlock){
		String msg = deadlock.deadlockMsg;
		if(map.containsKey(msg)){
			DeadlockNode remove = map.get(msg);
			map.remove(msg);
			children.remove(remove);
		}
	}

	public void clear() {
		super.children.clear();
		map.clear();
	}

}
