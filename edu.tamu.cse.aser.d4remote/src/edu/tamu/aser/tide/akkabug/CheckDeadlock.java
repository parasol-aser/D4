package edu.tamu.aser.tide.akkabug;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import org.eclipse.core.resources.IFile;

import edu.tamu.aser.tide.engine.ITIDEBug;
import edu.tamu.aser.tide.nodes.DLPair;

public class CheckDeadlock implements ITIDEBug{

	private ArrayList<DLPair> dLLockPairs;
	private int tid;
	private Set<Integer> tids;

	public CheckDeadlock(Integer tid1, Set<Integer> tids, ArrayList<DLPair> dLLockPairs2) {
		// TODO Auto-generated constructor stub
		this.tid = tid1;
		this.dLLockPairs = dLLockPairs2;
		this.tids = tids;
	}

	public ArrayList<DLPair> getPairs(){
		return dLLockPairs;
	}

	public int getTid(){
		return tid;
	}

	public Set<Integer> getTids(){
		return tids;
	}

	@Override
	public HashMap<String, IFile> getEventIFileMap() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void addEventIFileToMap(String event, IFile ifile) {
		// TODO Auto-generated method stub

	}

	@Override
	public HashMap<String, Integer> getEventLineMap() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void addEventLineToMap(String event, int line) {
		// TODO Auto-generated method stub

	}
}
