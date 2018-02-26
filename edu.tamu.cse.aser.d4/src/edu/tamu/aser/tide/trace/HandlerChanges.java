package edu.tamu.aser.tide.trace;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import com.ibm.wala.ipa.callgraph.CGNode;

public class HandlerChanges {

	public HashMap<CGNode, DetailChanges> node_change_mapping = new HashMap<>();
	//lock -> 1; thread -> 2; method -> 3
//	public HashMap<CGNode, IR> node_ir_old = new HashMap<>();
//	public HashMap<CGNode, IR> node_ir_new = new HashMap<>();


	public HandlerChanges() {
		// TODO Auto-generated constructor stub
	}


	public Set<CGNode> lockKeySet(){
		Set<CGNode> keyset = new HashSet<>();
		for (CGNode node : node_change_mapping.keySet()) {
			DetailChanges detail = node_change_mapping.get(node);
			if(detail.lockchanges.keySet().size() != 0){
				keyset.add(node);
			}
		}
		return keyset;
	}

	public Set<CGNode> threadKeySet(){
		Set<CGNode> keyset = new HashSet<>();
		for (CGNode node : node_change_mapping.keySet()) {
			DetailChanges detail = node_change_mapping.get(node);
			if(detail.threadchanges.keySet().size() != 0){
				keyset.add(node);
			}
		}
		return keyset;
	}

	public boolean isEmpty(){
		if(node_change_mapping.keySet().size() == 0)
			return true;
		else
			return false;
	}

	public void initial(CGNode node, DetailChanges changes){//IR new_ir, IR old_ir,
//		node_ir_new.put(node, new_ir);
//		node_ir_old.put(node, old_ir);
		node_change_mapping.put(node, changes);
	}

	public void clear(){
		node_change_mapping.clear();
//		node_ir_new.clear();
//		node_ir_old.clear();
	}


}
