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
		return Activator.getImageDescriptor("buggy-tiny-green.png");
	}

	@SuppressWarnings("unchecked")
	@Override
	protected void createChildren(ArrayList<LinkedList<String>> trace, String fix) {
		// TODO Auto-generated method stub
		TraceNode tracenode = new TraceNode(this, trace);
		FixNode fixnode = new FixNode(this, fix);
		super.children.add(tracenode);
		super.children.add(fixnode);
	}

}
