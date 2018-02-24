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
//import edu.tamu.aser.tide.trace.ReadNode;
//
//public class ReadList extends TreeNode{
//
//	public String name;
//	public HashMap<ReadNode, CReadNode> map = new HashMap<>();
//
//	public ReadList(TreeNode parent, HashSet<ReadNode> reads) {
//		super(parent);
//		this.name = "Concurrent Reads: ";
//		createChildren(reads);
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
//	@SuppressWarnings("unchecked")
//	protected void createChildren(HashSet<ReadNode> reads) {
//		for (ReadNode read : reads) {
//			CReadNode cr = new CReadNode(this, read);
//			super.children.add(cr);
//			map.put(read, cr);
//		}
//	}
//
//	public void removeChild(ReadNode read){
//		CReadNode cr = map.get(read);
//		super.children.remove(cr);
//		map.remove(read);
//	}
//
//	@Override
//	protected void createChildren(ArrayList<LinkedList<String>> trace, String fix) {
//		// TODO Auto-generated method stub
//	}
//
//}
