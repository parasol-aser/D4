package edu.tamu.aser.tide.engine;

import java.util.HashMap;

import org.eclipse.core.resources.IFile;

public class DLNode implements ITIDEBug{
	public final Locknode node1;
	public final Locknode node2;
	public final Locknode node3;

	public final Locknode node4;

	public final String sig;

	public DLNode(String sig, Locknode node1, Locknode node2, Locknode node3,Locknode node4){
		this.sig = sig;
		this.node1=node1;
		this.node2=node2;
		this.node3=node3;
		this.node4=node4;

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
