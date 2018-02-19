package edu.tamu.aser.tide.engine;

import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;

public class Locknode  {

	final int TID;
//	final int line;
	final InstanceKey lockobj;
	final String locksig;
    int startPosition;
	int endPosition;
	
	public Locknode(int TID, InstanceKey lockobj, String locksig)
	{
		this.TID = TID;
		this.lockobj = lockobj;
//		this.line = line;
		this.locksig = locksig;
		this.startPosition = startPosition;
	}
	
	public void setStartPosition(int startPosition) {
		this.startPosition = startPosition;
	}
	
	public int getStartPosition(){
		return this.startPosition;
	}
	
	public void setEndPosition(int endPosition) {
		this.endPosition = endPosition;
	}
	
	public int getEndPosition(){
		return this.endPosition;
	}

	public int getTID() {
		return TID;
	}

	public String getLockSig()
	{
		return locksig;
	}
//	public int getLine()
//	{
//		return line;
//	}
	public InstanceKey getLockobj()
	{
		return lockobj;
	}


}

