package edu.tamu.aser.tide.views;

import java.util.ArrayList;
import java.util.LinkedList;

public abstract class TreeNode implements ITreeNode{
	protected TreeNode parent;
	protected boolean isNewest = false;
	protected ArrayList children = new ArrayList<>();
//	protected ArrayList children2 = new ArrayList<>();//read


	public TreeNode(TreeNode parent) {
		this.parent = parent;
	}

	public boolean hasChildren() {
		return children.size() > 0;
	}

	public ITreeNode getParent() {
		return parent;
	}

	public ArrayList getChildren() {
		return children;
	}

//	public ArrayList getChildren2() {
//		return children2;
//	}
	
	public boolean isNewest() {
		return this.isNewest;
	}

	/* subclasses should override this method and add the child nodes */
	protected abstract void createChildren(ArrayList<LinkedList<String>> trace, String fix);

}