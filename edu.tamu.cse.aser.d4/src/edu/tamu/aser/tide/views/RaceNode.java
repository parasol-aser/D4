package edu.tamu.aser.tide.views;

import java.util.ArrayList;
import java.util.LinkedList;

import org.eclipse.jface.resource.ImageDescriptor;

import edu.tamu.aser.tide.engine.TIDERace;
import edu.tamu.aser.tide.plugin.Activator;

public class RaceNode extends TreeNode{
	protected TIDERace race;
	protected String name;

	public RaceNode(TreeNode parent, TIDERace race) {
		this(parent, race, false);
	}

	public RaceNode(TreeNode parent, TIDERace race, boolean isNewest) {
		super(parent);
		this.race = race;
		this.isNewest = isNewest;
		initialNode();
	}

	private void initialNode() {
		name = race.raceMsg;
		createChildren(race.traceMsg, race.fixMsg);
	}

	public TIDERace getRace(){
		return race;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public ImageDescriptor getImage() {
		return Activator.getImageDescriptor("circle-running-icon.png");
	}

	@SuppressWarnings("unchecked")
	@Override
	protected void createChildren(ArrayList<LinkedList<String>> trace, String fix) {
		LinkedList<String> trace1 = trace.get(0);
		LinkedList<String> trace2 = trace.get(1);
		if(this instanceof RaceNode){
			//1st rwnode
			String name1 = "Trace of " + race.node1.getSig() + " is :";
			SubTraceNode subtrace1 = new SubTraceNode(this, name1, trace1);
			//2nd rwnode
			String name2 = "Trace of " + race.node2.getSig() + " is :";
			SubTraceNode subtrace2 = new SubTraceNode(this, name2, trace2);
			super.children.add(subtrace1);
			super.children.add(subtrace2);
		}
	}

}
