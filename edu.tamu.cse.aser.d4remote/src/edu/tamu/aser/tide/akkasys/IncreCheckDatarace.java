package edu.tamu.aser.tide.akkasys;

import java.util.HashSet;
import java.util.LinkedList;

import edu.tamu.aser.tide.trace.MemNode;
import edu.tamu.aser.tide.trace.ReadNode;
import edu.tamu.aser.tide.trace.WriteNode;

public class IncreCheckDatarace {
	private String sig;
	private HashSet<WriteNode> writes;
	private HashSet<ReadNode> reads;

	public IncreCheckDatarace(String sig, HashSet<WriteNode> writes2, HashSet<ReadNode> reads2) {
		this.sig = sig;
		this.writes = writes2;
		this.reads = reads2;
	}

	public HashSet<WriteNode> getWrites(){
		return writes;
	}

	public HashSet<ReadNode> getReads(){
		return reads;
	}

	public String getSig(){
		return sig;
	}

}
