package edu.tamu.aser.tide.views;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

import org.eclipse.jface.resource.ImageDescriptor;

import edu.tamu.aser.tide.engine.TIDERace;
import edu.tamu.aser.tide.nodes.WriteNode;
import edu.tamu.aser.tide.plugin.Activator;

public class RWRelationNode extends TreeNode {
	protected String name;
	protected HashSet<TIDERace> races = new HashSet<>();
	protected HashMap<String, CSuperWriteNode> map = new HashMap<>();

	public RWRelationNode(TreeNode parent, String sig, TIDERace race) {
		this(parent, sig, race, false);
	}

	public RWRelationNode(TreeNode parent, String sig, TIDERace race, boolean isNewest) {
		super(parent);
		this.name = sig;
		this.isNewest = isNewest;
		initialNode(race, isNewest);
	}

	private void initialNode(TIDERace race) {
		initialNode(race, false);
	}

	private void initialNode(TIDERace race, boolean isNewest) {
		races.add(race);
		createChild(race, isNewest);
	}

	public String getSig(){
		return name;
	}

	public HashSet<TIDERace> getRaces(){
		return races;
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
	private void createChild(TIDERace race, boolean isNewest) {
		races.add(race);
		if(race.node1 instanceof WriteNode){
			WriteNode wNode = (WriteNode) race.node1;
			String wsig = wNode.getPrefix() + " on line " + wNode.getLine();
			CSuperWriteNode cwnode = new CSuperWriteNode(this, wNode, wsig, race, 1);
			cwnode.isNewest = isNewest;
			cwnode.createChild(race.node2, race, 2, isNewest);
			super.children.add(cwnode);
			map.put(wsig, cwnode);
		}
		if(race.node2 instanceof WriteNode){
			WriteNode wNode = (WriteNode) race.node2;
			String wsig = wNode.getPrefix() + " on line " + wNode.getLine();
			CSuperWriteNode cwnode = new CSuperWriteNode(this, wNode, wsig, race, 2);
			cwnode.isNewest = isNewest;
			cwnode.createChild(race.node1, race, 1, isNewest);
			super.children.add(cwnode);
			map.put(wsig, cwnode);
		}
	}

	//add a new race here
	@SuppressWarnings("unchecked")
	public void addChild(TIDERace race) {
		addChild(race, false);
	}

	@SuppressWarnings("unchecked")
	public void addChild(TIDERace race, boolean isNewest) {
		races.add(race);
		if(race.node1 instanceof WriteNode){
			WriteNode wNode = (WriteNode) race.node1;
			String wsig = wNode.getPrefix() + " on line "+ wNode.getLine();
			CSuperWriteNode cwnode = map.get(wsig);
			if(cwnode == null){
				//new write node
				cwnode = new CSuperWriteNode(this, wNode, wsig, race, 1);
				cwnode.isNewest = isNewest;
				cwnode.createChild(race.node2, race, 2, isNewest);
				super.children.add(cwnode);
				map.put(wsig, cwnode);
			}else{
				cwnode.createChild(race.node2, race, 2, isNewest);
			}
		}
		if(race.node2 instanceof WriteNode){
			WriteNode wNode = (WriteNode) race.node2;
			String wsig = wNode.getPrefix() + " on line " + wNode.getLine();
			CSuperWriteNode cwnode = map.get(wsig);
			if(cwnode == null){
				//new write node
				cwnode = new CSuperWriteNode(this, wNode, wsig, race, 2);
				cwnode.isNewest = isNewest;
				cwnode.createChild(race.node1, race, 1, isNewest);
				super.children.add(cwnode);
				map.put(wsig, cwnode);
			}else{
				cwnode.createChild(race.node1, race, 1, isNewest);
			}
		}
	}


	public boolean removeChild(TIDERace race) {
		races.remove(race);
		// if this.children == 0 return true, else return false
		if(race.node1 instanceof WriteNode){
			WriteNode wNode = (WriteNode) race.node1;
			String wsig = wNode.getPrefix() + " on line " + wNode.getLine();
			CSuperWriteNode cwnode = map.get(wsig);
			if(cwnode == null){
				System.err.println("cwritenode not exist in map");
			}else{
				if(cwnode.removeChild(race.node2)){//no read/write left, should remove this
					return true;
				}else{
					return false;
				}
			}
		}
		if(race.node2 instanceof WriteNode){
			WriteNode wNode = (WriteNode) race.node2;
			String wsig = wNode.getPrefix() + " on line " + wNode.getLine();
			CSuperWriteNode cwnode = map.get(wsig);
			if(cwnode == null){
				System.err.println("cwritenode not exist in map");
			}else{
				if(cwnode.removeChild(race.node1)){//no read/write left, should remove this
					return true;
				}else{
					return false;
				}
			}
		}
		return true;//nothing exist in map, should be removed
	}


}