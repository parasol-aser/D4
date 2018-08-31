package edu.tamu.aser.tide.akkabug;

import java.util.HashSet;

import edu.tamu.aser.tide.nodes.ReadNode;
import edu.tamu.aser.tide.nodes.WriteNode;

public class IncreCheckDatarace extends CheckDatarace{

	public IncreCheckDatarace(String sig, HashSet<WriteNode> writes, HashSet<ReadNode> reads) {
		super(sig, writes, reads);
	}

	public HashSet<WriteNode> getWrites(){
		return super.writes;
	}

	public HashSet<ReadNode> getReads(){
		return super.reads;
	}

	public String getSig(){
		return super.sig;
	}

}
