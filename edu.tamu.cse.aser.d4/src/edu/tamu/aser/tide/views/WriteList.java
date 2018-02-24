package edu.tamu.aser.tide.views;
//package edu.tamu.aser.tide.views;
//
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.HashSet;
//import java.util.LinkedList;
//
//import org.eclipse.jface.resource.ImageDescriptor;
//
//import edu.tamu.aser.tide.plugin.Activator;
//import edu.tamu.aser.tide.trace.WriteNode;
//
//public class WriteList extends TreeNode{
//	public String name;
//	public HashMap<WriteNode, CWriteNode> map = new HashMap<>();
//
//	public WriteList(TreeNode parent, HashSet<WriteNode> writes) {
//		super(parent);
//		this.name = "Concurrent Writes: ";
//		createChildren(writes);
//	}
//
//	@Override
//	public String getName() {
//		return name;
//	}
//
//	@Override
//	public ImageDescriptor getImage() {
//		return Activator.getImageDescriptor("folder_icon.gif");
//	}
//
//
//	@SuppressWarnings("unchecked")
//	protected void createChildren(HashSet<WriteNode> writes) {
//		for (WriteNode write : writes) {
//			CWriteNode cw = new CWriteNode(this, write);
//			super.children.add(cw);
//			map.put(write, cw);
//		}
//	}
//
//	public void removeChild(WriteNode write){
//		CWriteNode cw = map.get(write);
//		super.children.remove(cw);
//		map.remove(write);
//	}
//
//
//	@Override
//	protected void createChildren(ArrayList<LinkedList<String>> trace, String fix) {
//		// TODO Auto-generated method stub
//
//	}
//
//}
