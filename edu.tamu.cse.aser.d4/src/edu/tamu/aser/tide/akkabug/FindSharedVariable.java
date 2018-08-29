package edu.tamu.aser.tide.akkabug;

import java.util.HashMap;

public class FindSharedVariable {

	private HashMap<String, HashMap<Integer, Integer>> variableReadMap;
	private HashMap<String, HashMap<Integer, Integer>> variableWriteMap;

	public FindSharedVariable(HashMap<String, HashMap<Integer, Integer>> rsig_tid_num_map,
			HashMap<String, HashMap<Integer, Integer>> wsig_tid_num_map) {
		// TODO Auto-generated constructor stub
		this.variableReadMap = rsig_tid_num_map;
		this.variableWriteMap = wsig_tid_num_map;
	}

	public HashMap<String, HashMap<Integer, Integer>> getVReadMap(){
		return variableReadMap;
	}

	public HashMap<String, HashMap<Integer, Integer>> getVWriteMap() {
		return variableWriteMap;
	}

}
