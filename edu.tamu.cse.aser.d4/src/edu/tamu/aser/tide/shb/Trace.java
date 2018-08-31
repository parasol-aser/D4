package edu.tamu.aser.tide.shb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

import com.ibm.wala.ssa.SSAInstruction;

import edu.tamu.aser.tide.nodes.DLockNode;
import edu.tamu.aser.tide.nodes.DUnlockNode;
import edu.tamu.aser.tide.nodes.INode;
import edu.tamu.aser.tide.nodes.JoinNode;
import edu.tamu.aser.tide.nodes.LockPair;
import edu.tamu.aser.tide.nodes.ReadNode;
import edu.tamu.aser.tide.nodes.StartNode;
import edu.tamu.aser.tide.nodes.WriteNode;

public class Trace {

	private ArrayList<INode> trace = new ArrayList<INode>();
	private ArrayList<Integer> tids = new ArrayList<>();
	private HashMap<Integer, HashSet<Integer>> pidkidMapping = new HashMap<>();//should update; tid <-> kid tids mapping
	private ArrayList<Integer> kids = new ArrayList<>();// kid tids
	private HashMap<Integer, Integer> kid_line_map = new HashMap<>();
	private ArrayList<Integer> oldkids = new ArrayList<>();// kid tids
	private HashMap<Integer, Integer> oldkid_line_map = new HashMap<>();

	//should be set
	private HashMap<String, ArrayList<ReadNode>> rsigMapping = new HashMap<>();
	private HashMap<String, ArrayList<WriteNode>> wsigMapping = new HashMap<>();
	private ArrayList<LockPair> lockPairs = new ArrayList<>();
	private int hasStart = 0; //num of startnodes for an inst
	private int hasJoin = 0;

	private HashMap<SSAInstruction, StartNode> inst_start_mapping = new HashMap<>();
	private HashMap<SSAInstruction, JoinNode> inst_join_mapping = new HashMap<>();


	public Trace(int tid) {
		includeTid(tid);
	}

	public ArrayList<INode> getNodes(){
		return trace;
	}

	public ArrayList<Integer> getTraceKids(){
		return kids;
	}

	public StartNode getStartForInst(SSAInstruction inst){
		return inst_start_mapping.get(inst);
	}

	public void addCurKidTidMapping(int curTid, int kid){
//		pidkidMapping.put(curTid, kid);//not unique, updated
		HashSet<Integer> kids = pidkidMapping.get(curTid);
		if(kids == null){
			kids = new HashSet<>();
		}
		kids.add(kid);
		pidkidMapping.put(curTid, kids);
	}

	public void removeKidTidFor(int tid, int kid){
//		if(pidkidMapping.get(tid) == kid){//not unique,  updated
//			pidkidMapping.remove(tid);
//			kids.remove(kid);
//		}else{
//			System.out.println("tid <=> kid pair need to be updated.");
//		}
		HashSet<Integer> ekids = pidkidMapping.get(tid);
		if(ekids == null){
			System.out.println("tid <=> kid pair not exist.");
			return;
		}
		if(ekids.contains(kid)){
			ekids.remove(kid);
			kids.remove(kid);
		}else{
			System.out.println("tid <=> kid pair need to be updated.");
		}
	}

	public HashSet<Integer> getKidTidFor(int tid){
//		if(pidkidMapping.containsKey(tid))
//		    return pidkidMapping.get(tid);//should update
//		else
//			return -1;//no such relation
		HashSet<Integer> ekids = pidkidMapping.get(tid);
		if(ekids == null){
			System.out.println("tid <=> kid pair not exist.");
			return null;
		}
		return ekids;
	}


	public boolean ifHasStart(){
		return hasStart != 0;
	}

	public boolean ifHasJoin(){
		return hasJoin != 0;
	}

	public void addRsigMapping(String rsig, ReadNode node){
		ArrayList<ReadNode> map = rsigMapping.get(rsig);
		if(map == null){
			map = new ArrayList<>();
			map.add(node);
			rsigMapping.put(rsig, map);
		}else{
			rsigMapping.get(rsig).add(node);
		}
	}

	public void addWsigMapping(String wsig, WriteNode node){
		ArrayList<WriteNode> map = wsigMapping.get(wsig);
		if(map == null){
			map = new ArrayList<>();
			map.add(node);
			wsigMapping.put(wsig, map);
		}else
			wsigMapping.get(wsig).add(node);
	}

	public void addLockPair(LockPair lp){
		lockPairs.add(lp);
	}


	public void removeLockPair(LockPair pair) {
		lockPairs.remove(pair);
	}

	public HashMap<String, ArrayList<ReadNode>> getRsigMapping(){
		return rsigMapping;
	}

	public HashMap<String, ArrayList<WriteNode>> getWsigMapping(){
		return wsigMapping;
	}

	public ArrayList<LockPair> getLockPair(){
		return lockPairs;
	}

	public boolean includeTid(int tid){
		if(!tids.contains(tid)){
			tids.add(tid);
			return true;
		}
		return false;
	}

	public void includeTids(ArrayList<Integer> _tids){
		if(tids.size() == 0){
			tids.addAll(_tids);
		}
	}

	public boolean removeTid(Integer tid){
		if(tids.contains(tid)){
			tids.remove(tid);
			return true;
		}
		return false;
	}

	public ArrayList<Integer> getTraceTids(){
		return tids;
	}

	public boolean doesIncludeTid(int tid){
		return tids.contains(tid);
	}

	public int indexOf(INode node){
		int idx = trace.indexOf(node);
		if(idx == -1){//should not use this
			for (INode exist : trace) {
				if(node.toString().equals(exist.toString()))
					idx = trace.indexOf(exist);
			}
		}
		return idx;
	}

	public INode getLast(){
		return trace.get(trace.size() - 1);
	}

	public void add(INode node){
		trace.add(node);
	}

	/**
	 * currently, only insert lock/unlock
	 * @param node
	 */
	public void insert(DLockNode lnode, DUnlockNode unode, int idx){
		trace.add(idx, lnode);
		trace.add(idx + 2, unode);
	}

	public void remove(INode node){
		//inst_start_mapping,  hasStart,  hasJoin
		if(node instanceof StartNode){
			SSAInstruction key = null;
			for (SSAInstruction inst : inst_start_mapping.keySet()) {
				StartNode s = inst_start_mapping.get(inst);
				if(s.equals(node)){
					key = inst;
				}
			}
			if(key != null){
				inst_start_mapping.remove(key);
				//further startnodes added from loops or threads with same instructions
				int idx = trace.indexOf(node);
				while(hasStart > 0){
					hasStart --;
					trace.remove(idx + hasStart);
				}
			}
		}else if(node instanceof JoinNode){
			SSAInstruction key = null;
			for (SSAInstruction inst : inst_join_mapping.keySet()) {
				JoinNode s = inst_join_mapping.get(inst);
				if(s.equals(node)){
					key = inst;
				}
			}
			if(key != null){
				inst_join_mapping.remove(key);
				//further joinnodes added from loops or threads with same instructions
				int idx = trace.indexOf(node) + 1;
				while(hasJoin > 0){
					hasJoin --;
					trace.remove(idx + hasJoin);
				}
			}
		}
//		trace.remove(node);
	}

	public void removeLastNode() {
		trace.remove(trace.size() - 1);
	}

	public void addS(StartNode node, SSAInstruction inst, int tid_child){//only add start
		trace.add(node);
		inst_start_mapping.put(inst, node);
		if(hasStart == 0){
			hasStart ++;
		}
		addCurKidTidMapping(node.getParentTID(), tid_child);
		kids.add(tid_child);//target kid
		kid_line_map.put(tid_child, node.line);
	}

	//update: inst => 3 starts
	//loop or threads with same instructions(2nd traversal)
	public void add2S(StartNode node, SSAInstruction inst, int tid_child) {//add 2nd insert
		StartNode origin = inst_start_mapping.get(inst);
		trace.add(trace.indexOf(origin) + 1, node);
		addCurKidTidMapping(node.getParentTID(), tid_child);
		kids.add(tid_child);//target kid
		kid_line_map.put(tid_child, node.line);
		hasStart ++;
	}


	public void addJ(JoinNode node, SSAInstruction inst){//only add start
		trace.add(node);
		inst_join_mapping.put(inst, node);
		if(hasJoin == 0){
			hasJoin ++;
		}
	}

	public void add2J(JoinNode node, SSAInstruction inst, int tid_child) {//add 2nd insert
		JoinNode origin = inst_join_mapping.get(inst);
		trace.add(trace.indexOf(origin) + 1, node);
		hasJoin ++;
	}


	public ArrayList<INode> getContent(){
		return trace;
	}

	public String print(){
		StringBuffer sb = new StringBuffer();
		for (INode node : trace) {
			sb.append(node.toString() + "\n");
		}
		return new String(sb);
	}

	public INode get(int i){
		return trace.get(i);
	}

	public INode getFirst(){
		return trace.get(0);
	}

	public Iterator<INode> iterator(){
		return trace.iterator();
	}

	public int size(){
		return trace.size();
	}

//	public Trace copy() {
//		Trace temp = new Trace(tid);
//		temp.includeTids(tids);
//		return temp;
//	}

	public void clear(){
		trace.clear();
		tids.clear();
		rsigMapping.clear();
		wsigMapping.clear();
		lockPairs.clear();
		hasStart = 0;
		hasJoin = 0;
		pidkidMapping.clear();
		inst_start_mapping.clear();
		inst_join_mapping.clear();
		kids.clear();
		kid_line_map.clear();
	}

	public void clearContent(){//no tids, sjlater
		trace.clear();
		rsigMapping.clear();
		wsigMapping.clear();
		lockPairs.clear();
		pidkidMapping.clear();
		hasStart = 0;
		hasJoin = 0;
		inst_start_mapping.clear();
		inst_join_mapping.clear();
		oldkids.addAll(kids);
		oldkid_line_map.putAll(kid_line_map);
		kids.clear();
		kid_line_map.clear();
	}

	public HashSet<String> replaceRSigMap(HashSet<String> old_sigs, HashSet<String> new_sigs, ReadNode node) {
		HashSet<String> newAddedSigs = new HashSet<String>();
		for (String old : old_sigs) {
			ArrayList<ReadNode> exists = rsigMapping.get(old);
			if(exists != null){//should not be null
				exists.remove(node);
				if(exists.size() == 0){
					rsigMapping.remove(old);
				}
			}
		}
		for (String new_sig : new_sigs) {
			ArrayList<ReadNode> exists = rsigMapping.get(new_sig);
			if(exists == null){
				exists = new ArrayList<>();
				rsigMapping.put(new_sig, exists);
				newAddedSigs.add(new_sig);
			}
			exists.add(node);
		}
		return newAddedSigs;
	}

	public HashSet<String> replaceWSigMap(HashSet<String> old_sigs, HashSet<String> new_sigs, WriteNode node) {
		HashSet<String> newAddedSigs = new HashSet<String>();
		for (String old : old_sigs) {
			ArrayList<WriteNode> exists = wsigMapping.get(old);
			if(exists != null){//should not be null
				exists.remove(node);
				if(exists.size() == 0){
					wsigMapping.remove(old);
				}
			}
		}
		for (String new_sig : new_sigs) {
			ArrayList<WriteNode> exists = wsigMapping.get(new_sig);
			if(exists == null){
				exists = new ArrayList<>();
				wsigMapping.put(new_sig, exists);
				newAddedSigs.add(new_sig);
			}
			exists.add(node);
		}
		return newAddedSigs;
	}

	public void clearOldKids() {
		oldkids.clear();
		oldkid_line_map.clear();
	}

	public ArrayList<Integer> getOldKids() {
		return oldkids;
	}

	public Map<Integer, Integer> getOldkidsMap() {
		return oldkid_line_map;
	}





}
