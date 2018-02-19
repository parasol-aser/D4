package edu.tamu.aser.tide.trace;

public class DLLockPair {
	public DLockNode lock1;
	public DLockNode lock2;
	//make be wait node
	public DLLockPair(DLockNode lock1, DLockNode lock2)
	{
		this.lock1 = lock1;
		this.lock2 = lock2;
	}
}
