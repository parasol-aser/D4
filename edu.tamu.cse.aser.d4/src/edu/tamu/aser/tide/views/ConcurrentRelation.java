package edu.tamu.aser.tide.views;

import java.util.HashSet;

import edu.tamu.aser.tide.nodes.MemNode;
import edu.tamu.aser.tide.nodes.ReadNode;
import edu.tamu.aser.tide.nodes.WriteNode;

public class ConcurrentRelation{
	public WriteNode writeNode;
	public HashSet<ReadNode> concurrentReads;
	public HashSet<WriteNode> concurrentWrites;

	public ConcurrentRelation(WriteNode writeNode) {
		this.writeNode = writeNode;
		this.concurrentReads = new HashSet<>();
		this.concurrentWrites = new HashSet<>();
	}
	
	public void addConcurrentRW(MemNode node) {
		if (node instanceof ReadNode) {
			addConcurrentReads((ReadNode) node);
		} else {
			addConcurrentWrites((WriteNode) node);
		}
	}
	
	public void addConcurrentReads(ReadNode node) {
		concurrentReads.add(node);
	}
	
	public void addConcurrentWrites(WriteNode node) {
		concurrentWrites.add(node);
	}
}