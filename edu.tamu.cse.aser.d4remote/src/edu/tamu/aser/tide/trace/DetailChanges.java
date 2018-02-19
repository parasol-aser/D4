package edu.tamu.aser.tide.trace;

import java.util.HashMap;

public class DetailChanges {

	public HashMap<String, Integer> lockchanges = new HashMap<>();
	public HashMap<String, Integer> threadchanges = new HashMap<>();
	public HashMap<String, Integer> methodchanges = new HashMap<>();

	public DetailChanges() {
		// TODO Auto-generated constructor stub
	}

	//1,2,3 for adding info into detailchange
	public void add(int detail, String expr, int change) {
		if(detail == 1){
			lockchanges.put(expr, change);
		}else if(detail == 2){
			threadchanges.put(expr, change);
		}else if(detail == 3){
			methodchanges.put(expr, change);
		}
	}





}
