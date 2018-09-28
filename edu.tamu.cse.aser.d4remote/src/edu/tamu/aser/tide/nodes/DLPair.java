package edu.tamu.aser.tide.nodes;

public class DLPair {
	public DLockNode lock1;
	public DLockNode lock2;

	/**
	 * lock1 -> lock2
	 * @param lock1
	 * @param lock2
	 */
	public DLPair(DLockNode lock1, DLockNode lock2){
		this.lock1 = lock1;
		this.lock2 = lock2;
	}

	@Override
	public int hashCode() {
		return lock1.hashCode() + lock2.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if(obj instanceof DLPair){
			DLPair that = (DLPair) obj;
			if(this.lock1.equals(that.lock1)
					&& this.lock2.equals(that.lock2)){
				return true;
			}
		}
		return false;
	}
}
