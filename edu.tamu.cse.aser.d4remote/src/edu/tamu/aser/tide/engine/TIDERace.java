package edu.tamu.aser.tide.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

import org.eclipse.core.resources.IFile;

import edu.tamu.aser.tide.trace.MemNode;
import edu.tamu.aser.tide.trace.WriteNode;

public class TIDERace implements ITIDEBug{

	public final MemNode node1;
	public final MemNode node2;
	public String sig;
	public String initsig;
	//
	public int tid1;
	public int tid2;
	//
	public String raceMsg, fixMsg;
	public ArrayList<LinkedList<String>> traceMsg;
	public HashMap<String, IFile> event_ifile_map = new HashMap<>();
	public HashMap<String, Integer> event_line_map = new HashMap<>();

	//for recheck
	public TIDERace(MemNode node1, MemNode node2){
		this.node1=node1;
		this.node2=node2;
		this.sig = "";
	}

	public TIDERace(String sig, MemNode node1, MemNode node2){
		int index1 = sig.indexOf(".");
		if(sig.substring(0,index1).contains("array"))
			this.sig = sig.substring(0,index1);
		else{
		int index2 = sig.substring(index1+1).indexOf(".");
		if(index2>0)//must be suffixed with .'hashcode'
			this.sig = sig.substring(0,index1+index2+1);
		else
			this.sig = sig;
		}
		this.node1=node1;
		this.node2=node2;
		this.initsig = sig;
	}

	public TIDERace(String sig, MemNode xnode, int xtid, WriteNode wnode, int wtid) {
		setUpSig(sig);
		this.node1 = xnode;
		this.node2 = wnode;
		this.tid1 = xtid;
		this.tid2 = wtid;
		this.initsig = sig;
	}

	public void setUpSig(String sig){
		int index1 = sig.indexOf(".");
		if(sig.substring(0,index1).contains("array"))
			this.sig = sig.substring(0,index1);
		else{
			int index2 = sig.substring(index1+1).indexOf(".");
			if(index2>0)//must be suffixed with .'hashcode'
				this.sig = sig.substring(0,index1+index2+1);
			else
				this.sig = sig;
		}
	}

	public HashMap<String, Integer> getEventLineMap(){
		return event_line_map;
	}

	public void addEventLineToMap(String event, int line){
		event_line_map.put(event, line);
	}

	public HashMap<String, IFile> getEventIFileMap(){
		return event_ifile_map;
	}

	public void addEventIFileToMap(String event, IFile ifile){
		event_ifile_map.put(event, ifile);//check later
	}

	public int hashCode()
	{
		return sig.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if(o instanceof TIDERace){
			if(this.sig.equals(((TIDERace) o).sig) &&
				((this.node1.getSig().equals(((TIDERace) o).node1.getSig())&&
				this.node2.getSig().equals(((TIDERace) o).node2.getSig()))
				||(this.node1.getSig().equals(((TIDERace) o).node2.getSig())&&
						this.node2.getSig().equals(((TIDERace) o).node1.getSig()))
						))
				return true;
		}

		return false;
	}

	public void setBugInfo(String raceMsg, ArrayList<LinkedList<String>> traceMsg2, String fixMsg) {
		// TODO Auto-generated method stub
		this.raceMsg = raceMsg;
		this.fixMsg = fixMsg;
		this.traceMsg = traceMsg2;
	}
}
