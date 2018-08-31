package edu.tamu.aser.tide.views;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.resource.ImageDescriptor;

import edu.tamu.aser.tide.engine.TIDERace;
import edu.tamu.aser.tide.nodes.MemNode;
import edu.tamu.aser.tide.nodes.WriteNode;
import edu.tamu.aser.tide.plugin.Activator;

public class CSuperWriteNode extends TreeNode{
	public String name;
	public WriteNode write;
	public ConcurrentRWList rwList = null;
	public HashMap<String, IFile> event_ifile_map;
	public HashMap<String, Integer> event_line_map;

	public CSuperWriteNode(TreeNode parent, WriteNode write, String wsig, TIDERace race, int idx) {
		super(parent);
		this.write = write;
		if(write.getLocalSig() != null){
			this.name = write.getPrefix() + write.getLocalSig() + " on line " + write.getLine();
		}else{
			this.name = wsig;
		}
//		this.name = wsig;
		initialNode(race, idx);
	}

	@SuppressWarnings("unchecked")
	private void initialNode(TIDERace race, int idx) {
		this.event_ifile_map = race.event_ifile_map;
		this.event_line_map = race.event_line_map;
		//trace
		LinkedList<String> events = race.traceMsg.get(idx - 1);
		for (String event : events) {
			EventNode eventNode = new EventNode(this, event);
			super.children.add(eventNode);
		}
		//rwlist
		rwList = new ConcurrentRWList(this);
//		super.children.add(rwList);
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public ImageDescriptor getImage() {
		return Activator.getImageDescriptor("write-icon.png");
	}

	@Override
	protected void createChildren(ArrayList<LinkedList<String>> trace, String fix) {
		// TODO Auto-generated method stub

	}

	//create for one race and one node
	public void createChild(MemNode other, TIDERace race, int idx) {
		rwList.createChild(other, race, idx);
	}

	public void createChild(MemNode other, TIDERace race, int idx, boolean isNewest) {
		rwList.createChild(other, race, idx, isNewest);
	}

	public boolean removeChild(MemNode other) {
		// if this.children == 0 return true, else return false
		return rwList.removeChild(other);
	}

	public Object getRelationDetail() {
		return rwList;
	}

	public void clear(){
		name = null;
		write = null;
		event_ifile_map = null;
		event_line_map = null;
		rwList.clear();
		super.children.clear();
	}




}
