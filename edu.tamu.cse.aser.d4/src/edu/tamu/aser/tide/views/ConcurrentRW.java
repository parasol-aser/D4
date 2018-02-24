package edu.tamu.aser.tide.views;
//package edu.tamu.aser.tide.views;
//
//import java.util.ArrayList;
//import java.util.HashSet;
//import java.util.LinkedList;
//
//import org.eclipse.jface.resource.ImageDescriptor;
//
//import edu.tamu.aser.tide.plugin.Activator;
//import edu.tamu.aser.tide.trace.MemNode;
//import edu.tamu.aser.tide.trace.ReadNode;
//import edu.tamu.aser.tide.trace.WriteNode;
//
//public class ConcurrentRW extends TreeNode{
//	public String name;
//
//	public ConcurrentRW(TreeNode parent, String sig, HashSet<WriteNode> writes, HashSet<ReadNode> reads) {
//		super(parent);
//		this.name = sig;
//		createChild(writes, reads);
//	}
//
//	@Override
//	public String getName() {
//		return name;
//	}
//
//	@Override
//	public ImageDescriptor getImage() {
//		return Activator.getImageDescriptor("blank.gif");
//	}
//
//	@SuppressWarnings("unchecked")
//	public void createChild(HashSet<WriteNode> writes, HashSet<ReadNode> reads) {
//		ReadList rlist = new ReadList(this, reads);
//		WriteList wlist = new WriteList(this, writes);
//		super.children.add(wlist);//only 1
//		super.children2.add(rlist);//only 1
//	}
//
//	public void removeChild(MemNode remove){
//		if(remove instanceof ReadNode){
//			ReadNode r = (ReadNode) remove;
//			((ConcurrentRW) super.children2.get(0)).removeChild(r);
//		}else if(remove instanceof WriteNode){
//			WriteNode w = (WriteNode) remove;
//			((ConcurrentRW) super.children.get(0)).removeChild(w);
//		}
//	}
//
//	@Override
//	protected void createChildren(ArrayList<LinkedList<String>> trace, String fix) {
//		// TODO Auto-generated method stub
//
//	}
//
//}
