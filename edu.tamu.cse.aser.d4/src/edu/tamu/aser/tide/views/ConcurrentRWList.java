package edu.tamu.aser.tide.views;

import java.util.ArrayList;
import java.util.LinkedList;

import org.eclipse.jface.resource.ImageDescriptor;

import edu.tamu.aser.tide.engine.TIDERace;
import edu.tamu.aser.tide.nodes.MemNode;
import edu.tamu.aser.tide.nodes.ReadNode;
import edu.tamu.aser.tide.nodes.WriteNode;
import edu.tamu.aser.tide.plugin.Activator;

public class ConcurrentRWList extends TreeNode{

	protected String name;
	public ConcurrentReadList rList = null;
	public ConcurrentWriteList wList = null;

	public ConcurrentRWList(TreeNode parent) {
		super(parent);
		this.name = "Concurrent Write & Read";
		initialNode();
	}

	@SuppressWarnings("unchecked")
	private void initialNode() {
		rList = new ConcurrentReadList(this);
		wList = new ConcurrentWriteList(this);
		super.children.add(rList);
		super.children.add(wList);
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

	//create for one race and one node
	public void createChild(MemNode other, TIDERace race, int idx) {
		if(other instanceof ReadNode){
			ReadNode read = (ReadNode) other;
			String rsig = read.getSig();
			rList.createChild(rsig, read, race, idx);
		}else{//write node
			WriteNode write = (WriteNode) other;
			String wsig = write.getSig();
			wList.createChild(wsig, write, race, idx);
		}
	}

	public void createChild(MemNode other, TIDERace race, int idx, boolean isNewest) {
		if(other instanceof ReadNode){
			ReadNode read = (ReadNode) other;
			String rsig = read.getSig();
			rList.createChild(rsig, read, race, idx, isNewest);
		}else{//write node
			WriteNode write = (WriteNode) other;
			String wsig = write.getSig();
			wList.createChild(wsig, write, race, idx, isNewest);
		}
	}

	public boolean removeChild(MemNode other) {
		// if this.children == 0 return true, else return false
		if(other instanceof ReadNode){
			ReadNode read = (ReadNode) other;
			String rsig = read.getSig();
			rList.removeChild(rsig, read);
			if(rList.children.size() == 0){
				return true;
			}else{
				return false;
			}
		}else{//write node
			WriteNode write = (WriteNode) other;
			String wsig = write.getSig();
			wList.removeChild(wsig, write);
			if(wList.children.size() == 0 ){
				return true;
			}else{
				return false;
			}
		}
	}

	public void clear() {
		rList = null;
		wList = null;
		super.children.clear();
	}


}
