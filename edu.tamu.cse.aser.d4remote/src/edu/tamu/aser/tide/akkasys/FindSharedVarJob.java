package edu.tamu.aser.tide.akkasys;

import java.util.HashMap;

public class FindSharedVarJob {

	String sig;
	HashMap<Integer, Integer> readMap;
	HashMap<Integer, Integer> writeMap;

	public FindSharedVarJob(String sig, HashMap<Integer, Integer> hashMap, HashMap<Integer, Integer> hashMap2) {
		// TODO Auto-generated constructor stub
		this.sig = sig;
		this.readMap = hashMap2;
		this.writeMap = hashMap;
	}

	public String getSig(){
		return sig;
	}

	public HashMap<Integer, Integer> getReadMap(){
		return readMap;
	}

	public HashMap<Integer, Integer> getWriteMap(){
		return writeMap;
	}

}
