package edu.tamu.aser.tide.nodes;

import org.eclipse.core.resources.IFile;

public abstract class SyncNode implements INode {
	public abstract String toString();
	public abstract int getSelfTID() ;
	public abstract IFile getFile();
	public abstract int getLine();
}
