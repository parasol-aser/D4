package edu.tamu.aser.tide.akkasys;

import java.util.HashSet;
import java.util.LinkedList;

import edu.tamu.aser.tide.trace.INode;

public class IncreRemoveLocalVar {

	private HashSet<String> checks;

	public IncreRemoveLocalVar(HashSet<String> interested_rw) {
		this.checks = interested_rw;
	}

	public HashSet<String> getNodes(){
		return checks;
	}

}
