package edu.tamu.aser.tide.akkabug;

import edu.tamu.aser.tide.trace.DLockNode;

public class IncreCheckDeadlock {

	private DLockNode lock;

	public IncreCheckDeadlock(DLockNode lock) {
		this.lock = lock;
	}

	public DLockNode getCheckLock(){
		return lock;
	}

}
