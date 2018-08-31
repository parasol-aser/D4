package edu.tamu.aser.tide.views;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.resource.ImageDescriptor;

import edu.tamu.aser.tide.engine.TIDERace;
import edu.tamu.aser.tide.nodes.ReadNode;
import edu.tamu.aser.tide.plugin.Activator;

public class CReadNode extends TreeNode{
	public String name;
	public ReadNode read;
	public TIDERace race;
	public HashMap<String, IFile> event_ifile_map;
	public HashMap<String, Integer> event_line_map;

	public CReadNode(TreeNode parent, String rsig, ReadNode read, TIDERace race, int idx) {
		super(parent);
		if(read.getLocalSig() != null){
			this.name = read.getPrefix() + read.getLocalSig() + " on line " + read.getLine();
		}else{
			this.name = read.getPrefix() + " on line " + read.getLine();
		}
		this.read = read;
		this.race = race;
		this.event_ifile_map = race.event_ifile_map;
		this.event_line_map = race.event_line_map;
//		createChild(idx);
		createChildNoSubTrace(idx);
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public ImageDescriptor getImage() {
		return Activator.getImageDescriptor("read-icon.png");
	}

	@Override
	protected void createChildren(ArrayList<LinkedList<String>> trace, String fix) {
		// TODO Auto-generated method stub

	}

	@SuppressWarnings("unchecked")
	private void createChild(int idx) {
		String name = "Trace of " + this.name + " :";
		SubTraceNode subtrace = new SubTraceNode(this, name, race.traceMsg.get(idx - 1));
		super.children.add(subtrace);
	}

	@SuppressWarnings("unchecked")
	private void createChildNoSubTrace(int idx) {
		LinkedList<String> events = race.traceMsg.get(idx - 1);
		for (String event : events) {
			EventNode eventNode = new EventNode(this, event);
			super.children.add(eventNode);
		}
	}

}
