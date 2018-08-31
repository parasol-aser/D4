package edu.tamu.aser.tide.views;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

import org.eclipse.jface.resource.ImageDescriptor;

import edu.tamu.aser.tide.engine.TIDERace;
import edu.tamu.aser.tide.plugin.Activator;

public class RaceDetail extends TreeNode{
	protected String name;
	protected HashMap<String, RaceNode> map = new HashMap<>();

	public RaceDetail(TreeNode parent) {
		super(parent);
		this.name = "Race Detail";
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

	protected void createChild(TIDERace race) {
		createChild(race, false);
	}

	@SuppressWarnings("unchecked")
	protected void createChild(TIDERace race, boolean isNewest) {
		//transfer to racenode
		RaceNode node = new RaceNode(this, race, isNewest);
		super.children.add(node);
		map.put(race.raceMsg, node);
	}

	protected void removeChild(TIDERace race){
		String msg = race.raceMsg;
		if(map.containsKey(msg)){
			RaceNode remove = map.get(msg);
			map.remove(msg);
			children.remove(remove);
		}
	}

	public void clear() {
		super.children.clear();
		map.clear();
	}

}
