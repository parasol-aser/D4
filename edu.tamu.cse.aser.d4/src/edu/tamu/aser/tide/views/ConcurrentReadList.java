package edu.tamu.aser.tide.views;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

import org.eclipse.jface.resource.ImageDescriptor;

import edu.tamu.aser.tide.engine.TIDERace;
import edu.tamu.aser.tide.nodes.ReadNode;
import edu.tamu.aser.tide.plugin.Activator;

public class ConcurrentReadList extends TreeNode{

	public String name = "Concurrent Read List";
	public HashMap<String, CReadNode> map = new HashMap<>();

	public ConcurrentReadList(TreeNode parent) {
		super(parent);
	}

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	public ImageDescriptor getImage() {
		return Activator.getImageDescriptor("parallel.png");
	}

	@Override
	protected void createChildren(ArrayList<LinkedList<String>> trace, String fix) {

	}


	public void createChild(String rsig, ReadNode read, TIDERace race, int idx) {//fix later
		createChild(rsig, read, race, idx, false);
	}

	@SuppressWarnings("unchecked")
	public void createChild(String rsig, ReadNode read, TIDERace race, int idx, boolean isNewest) {//fix later
		if(!map.keySet().contains(rsig)){
			CReadNode cread = new CReadNode(this, rsig, read, race, idx);
			cread.isNewest = isNewest;
			map.put(rsig, cread);
			super.children.add(cread);
		}
	}

	public void removeChild(String rsig, ReadNode read) {
		if(map.keySet().contains(rsig)){
			CReadNode cread = map.get(rsig);
			super.children.remove(cread);
			map.remove(rsig);
		}
	}

}
