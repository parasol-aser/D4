package edu.tamu.aser.tide.views;

import java.util.ArrayList;
import java.util.LinkedList;

import org.eclipse.jface.resource.ImageDescriptor;

public class SubTraceNode extends TreeNode{
	protected String name;
	protected LinkedList<String> events;

	public SubTraceNode(TreeNode parent, String name, LinkedList<String> trace) {
		super(parent);
		this.name = name;
		this.events = trace;
		createEventChildren(events);
	}

	@SuppressWarnings("unchecked")
	private void createEventChildren(LinkedList<String> events) {
		for (String event : events) {
			EventNode eventNode = new EventNode(this, event);
			super.children.add(eventNode);
		}

	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public ImageDescriptor getImage() {
		return null;
	}

	@Override
	protected void createChildren(ArrayList<LinkedList<String>> events, String fix) {
		// TODO Auto-generated method stub
	}

}
