package edu.tamu.aser.tide.views;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

import org.eclipse.jface.resource.ImageDescriptor;

import edu.tamu.aser.tide.engine.TIDERace;
import edu.tamu.aser.tide.plugin.Activator;

public class RelationDetail extends TreeNode{
	protected String name;
	protected HashMap<String, RWRelationNode> map = new HashMap<>();

	public RelationDetail(TreeNode parent) {
		super(parent);
		this.name = "Concurrent Read/Write Relations";
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public ImageDescriptor getImage() {
		return Activator.getImageDescriptor("parallel.png");
	}

	@Override
	protected void createChildren(ArrayList<LinkedList<String>> trace, String fix) {
		// TODO Auto-generated method stub
	}

	@SuppressWarnings("unchecked")
	protected void createChild(TIDERace race, String sig) {
		if(map.keySet().contains(sig)){
			System.err.println("should not create new, should add to existing.");
		}
		RWRelationNode rwRelation = new RWRelationNode(this, sig, race);
		super.children.add(rwRelation);
		map.put(sig, rwRelation);
	}

	@SuppressWarnings("unchecked")
	protected void createChild(TIDERace race, String sig, boolean isNewest) {
		if(map.keySet().contains(sig)){
			System.err.println("should not create new, should add to existing.");
		}
		RWRelationNode rwRelation = new RWRelationNode(this, sig, race, isNewest);
		super.children.add(rwRelation);
		map.put(sig, rwRelation);
	}

	//has the entry, but add an race
	protected void addChild(TIDERace race, String sig) {
		addChild(race, sig, false);
	}

	protected void addChild(TIDERace race, String sig, boolean isNewest) {
		if(map.keySet().contains(sig)){
			RWRelationNode rwRelation = map.get(sig);
			rwRelation.isNewest = isNewest;
			rwRelation.addChild(race, isNewest);
		}
	}

	//remove one race from relation/entry
	public boolean removeChild(TIDERace race, String sig) {
		if(map.keySet().contains(sig)){
			RWRelationNode rwRelation = map.get(sig);
			return rwRelation.removeChild(race);
		}
		return true;//map does not have raceMsg
	}

	//remove this sig and its map
	public void removeThisEntry(String raceMsg) {
		if(map.keySet().contains(raceMsg)){
			RWRelationNode rwrelation = map.get(raceMsg);
			map.remove(raceMsg);
			super.children.remove(rwrelation);
		}
	}


	public void clear() {
		super.children.clear();
		map.clear();
	}


//	protected void removeChild(TIDERace race) {
//		RWRelationNode relationNode = map.get(race.initsig);
//		relationNode.removeChild(race);
//		if (relationNode.children.isEmpty()) {
//			children.remove(relationNode);
//		}
//	}


//	@SuppressWarnings("unchecked")
//	protected void createChildren(HashMap<String, HashMap<String, ConcurrentRelation> > relations) {
//		RWRelationNode relationNode;
//		for(Map.Entry<String, HashMap<String, ConcurrentRelation>> relation : relations.entrySet()) {
//			if (map.containsKey(relation.getKey())) {
//				relationNode = map.get(relation.getKey());
//				relationNode.createChildren(relation.getValue());
//			} else {
//				relationNode = new RWRelationNode(this, relation.getKey());
//				relationNode.createChildren(relation.getValue());
//				super.children.add(relationNode);
//				map.put(relation.getKey(), relationNode);
//			}
//		}
//	}
}