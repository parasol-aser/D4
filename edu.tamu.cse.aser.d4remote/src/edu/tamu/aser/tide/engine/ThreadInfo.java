package edu.tamu.aser.tide.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;


public class ThreadInfo {
	
	private int parent;
	private ArrayList<Integer> kids = new ArrayList<>();
//	private branch;
	private HashMap<Locknode, HashSet<Locknode>> dependency = new HashMap<>();
	private int startPosition;
	private int joinPosition;
	
	public ThreadInfo(int parent, int startPosition){
		this.parent = parent;
		this.startPosition = startPosition;
	}
	
	public void addKids(int kid){
		this.kids.add(kid);
	}
	
	public HashMap<Locknode, HashSet<Locknode>> getDepend(){
		return dependency;
	}
	
	public void setJoinP(int joinPosition){
		this.joinPosition = joinPosition;
	}
	
	public int getJoinP(){
		return this.joinPosition;
	}
	
	public int getStartP(){
		return this.startPosition;
	}
	
	public ArrayList<Integer> getKids(){
		return this.kids;
	}
	
	public int getParent(){
		return this.parent;
	}

}
