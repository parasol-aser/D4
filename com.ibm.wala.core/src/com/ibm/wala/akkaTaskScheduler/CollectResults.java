package com.ibm.wala.akkaTaskScheduler;

import java.util.ArrayList;

public class CollectResults{
	private static ArrayList<Result> results;
	static CollectResults cResults;

	private CollectResults(ArrayList<Result> results) {
		this.results = results;
	}

	public static CollectResults getInstance(){
		if(results == null){
			cResults = new CollectResults(new ArrayList<Result>());
		}
		return cResults;
	}

	public void addResult(Result r){
		results.add(r);
	}

	public ArrayList<Result> getAllResults(){
		return results;
	}

	public int getSize(){
		return results.size();
	}

	public Result get(int i){
		return results.get(i);
	}

}