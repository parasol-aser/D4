package edu.tamu.aser.tide.views;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

import org.eclipse.jface.resource.ImageDescriptor;

import edu.tamu.aser.tide.engine.TIDERace;
import edu.tamu.aser.tide.nodes.WriteNode;
import edu.tamu.aser.tide.plugin.Activator;

public class ConcurrentWriteList extends TreeNode{

	public String name = "Concurrent Write List";
	public HashMap<String, CWriteNode> map = new HashMap<>();

	public ConcurrentWriteList(TreeNode parent) {
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
		// TODO Auto-generated method stub

	}

	public void createChild(String wsig, WriteNode write, TIDERace race, int idx) {
		createChild(wsig, write, race, idx, false);
	}

	@SuppressWarnings("unchecked")
	public void createChild(String wsig, WriteNode write, TIDERace race, int idx, boolean isNewest) {
		if(!map.keySet().contains(wsig)){
			CWriteNode cwrite = new CWriteNode(this, wsig, write, race, idx);
			cwrite.isNewest = isNewest;
			map.put(wsig, cwrite);
			super.children.add(cwrite);
		}
	}

	public void removeChild(String wsig, WriteNode write) {
		if(map.keySet().contains(wsig)){
			CWriteNode cwrite = map.get(wsig);
			super.children.remove(cwrite);
			map.remove(cwrite);
		}
	}

}
