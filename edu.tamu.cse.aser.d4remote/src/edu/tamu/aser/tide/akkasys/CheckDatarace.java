package edu.tamu.aser.tide.akkasys;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

import org.eclipse.core.resources.IFile;

import edu.tamu.aser.tide.engine.ITIDEBug;
import edu.tamu.aser.tide.trace.ReadNode;
import edu.tamu.aser.tide.trace.WriteNode;


public class CheckDatarace implements ITIDEBug{

	HashSet<WriteNode> writes;
	HashSet<ReadNode> reads;
	String sig;

	public CheckDatarace(String sig, HashSet<WriteNode> writes2, HashSet<ReadNode> reads2) {
		// TODO Auto-generated constructor stub
		this.sig = sig;
		this.reads = reads2;
		this.writes = writes2;
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
