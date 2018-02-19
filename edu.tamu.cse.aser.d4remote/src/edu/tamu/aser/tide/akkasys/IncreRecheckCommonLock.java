package edu.tamu.aser.tide.akkasys;

import java.util.HashSet;

import edu.tamu.aser.tide.engine.TIDERace;

public class IncreRecheckCommonLock {
	private HashSet<TIDERace> group ;

	public IncreRecheckCommonLock(HashSet<TIDERace> group) {
		this.group = group;
	}

	public HashSet<TIDERace> getGroup() {
		// TODO Auto-generated method stub
		return group;
	}



}
