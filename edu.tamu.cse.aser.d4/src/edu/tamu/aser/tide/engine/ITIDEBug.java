package edu.tamu.aser.tide.engine;

import java.util.HashMap;

import org.eclipse.core.resources.IFile;

public interface ITIDEBug
{
	public abstract HashMap<String, IFile> getEventIFileMap();
	public abstract void addEventIFileToMap(String event, IFile ifile);
	public abstract HashMap<String, Integer> getEventLineMap();
	public abstract void addEventLineToMap(String event, int line);
}
