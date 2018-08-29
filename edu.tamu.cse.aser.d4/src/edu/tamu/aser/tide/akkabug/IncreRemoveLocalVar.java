package edu.tamu.aser.tide.akkabug;

import java.util.HashSet;

public class IncreRemoveLocalVar {

	private HashSet<String> checks;

	public IncreRemoveLocalVar(HashSet<String> interested_rw) {
		this.checks = interested_rw;
	}

	public HashSet<String> getNodes(){
		return checks;
	}

}
