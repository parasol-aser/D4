package edu.tamu.aser.tide.views;

import java.util.ArrayList;

import org.eclipse.jface.resource.ImageDescriptor;

public interface ITreeNode {
	public String getName();
	public ImageDescriptor getImage();
	public ArrayList getChildren();
	public boolean hasChildren();
	public ITreeNode getParent();
}
