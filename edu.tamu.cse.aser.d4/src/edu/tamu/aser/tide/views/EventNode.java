package edu.tamu.aser.tide.views;

import java.util.ArrayList;
import java.util.LinkedList;

import org.eclipse.jface.resource.ImageDescriptor;

import edu.tamu.aser.tide.plugin.Activator;

public class EventNode extends TreeNode{
	protected String name;

	public EventNode(TreeNode parent, String event) {
		super(parent);
		this.name = event;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public ImageDescriptor getImage() {
//		return Activator.getImageDescriptor("file_icon.png");
		return null;
	}

	@Override
	protected void createChildren(ArrayList<LinkedList<String>> trace, String fix) {
		// TODO Auto-generated method stub
	}

}
