package edu.tamu.aser.tide.akkabug;

import java.util.HashSet;

import edu.tamu.aser.tide.trace.DLockNode;

public class IncrementalDeadlock {

	private HashSet<DLockNode> interested_locks;

	public IncrementalDeadlock(HashSet<DLockNode> interested_l) {
		this.interested_locks = interested_l;
	}

	public HashSet<DLockNode> getCheckSigs(){
		return interested_locks;
	}

}
