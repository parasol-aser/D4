package edu.tamu.aser.tide.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import com.ibm.wala.ipa.callgraph.CGNode;

import edu.tamu.aser.tide.engine.AstCGNode2;
import edu.tamu.aser.tide.trace.INode;
import edu.tamu.aser.tide.trace.JoinNode;
import edu.tamu.aser.tide.trace.MethodNode;
import edu.tamu.aser.tide.trace.StartNode;
import edu.tamu.aser.tide.trace.SyncNode;

public class SHBGraph{

	private HashMap<CGNode, Trace> traceMapping = new HashMap<>(); //TODO: may change to <String, Trace>
	public EdgeManager edgeManager = new EdgeManager();
	public CGNode main;

	public SHBGraph() {
		// TODO Auto-generated constructor stub
	}

	public void mainCGNode(CGNode n) {
		main = n;
		edgeManager.tellMain(main);
	}

	public ArrayList<Trace> getAllTraces(){
		ArrayList<Trace> traces = new ArrayList<>();
		for (Iterator<CGNode> iterator = traceMapping.keySet().iterator(); iterator.hasNext();) {
			CGNode node = (CGNode) iterator.next();
			Trace trace = traceMapping.get(node);
//			Trace temp = trace.copy();
//			ArrayList<INode> nodes = trace.getContent();
//			for (INode node1 : nodes) {
//				if (node1 instanceof MemNode) {
//					MemNode rw = (MemNode) node1;
//					temp.add(rw);
//				}
//			}
//			traces.add(temp);
			if(trace != null)
				traces.add(trace);
		}
		return traces;
	}

	public int getNumOfEdges(){
		return edgeManager.getAllEdges().keySet().size();
	}

	public boolean addTrace(CGNode node, Trace trace, int tid){
		if(traceMapping.containsKey(node)){
			traceMapping.get(node).includeTid(tid);
			return false;
		}else{
			trace.includeTid(tid);
			traceMapping.put(node, trace);
			return true;
		}
	}

	public boolean delTrace(CGNode node, int tid){
		if(traceMapping.containsKey(node)){
			ArrayList<Integer> tids = traceMapping.get(node).getTraceTids();
			if(tids.contains(tid) && tids.size() == 1)
				traceMapping.remove(node);
			else
				traceMapping.get(node).removeTid(tid);
			return true;
		}else{
			//no such trace
			return false;
		}
	}


	private HashMap<CGNode, HashSet<CGNode>> ignore2Callers = new HashMap<>();
	public HashSet<CGNode> getCallersForIgnored(CGNode node){
		return ignore2Callers.get(node);
	}

	public boolean delTrace(CGNode node){
		if(traceMapping.containsKey(node)){
			//if remove these nodes, when consider back, they are missing, then require re-traversal insts.
//			clearSourceINodeFor(node);
			HashSet<CGNode> callers = clearIncomingEdgesFor(node);
			ignore2Callers.put(node, callers);
			clearOutgoingEdgesFor(node);
			traceMapping.remove(node);
			//what if node only has one kid with one tid?? should delete
			return true;
		}else{
			//no such trace
			return false;
		}
	}

	public boolean replaceTrace(CGNode node, Trace curTrace){
		if(traceMapping.containsKey(node)){
			ArrayList<Integer> tids = traceMapping.get(node).getTraceTids();
			curTrace.includeTids(tids);//?
			curTrace.clearOldKids();
			traceMapping.put(node, curTrace);
			return true;
		}else{
			return addTrace(node, curTrace);
		}
	}

	public SHBEdge addEdge(INode inst, CGNode method) {
		return edgeManager.addEdge(inst, method);
	}

	public boolean delEdge(INode inst, CGNode method) {
		return edgeManager.delEdge(inst, method);
	}

	public Trace getTrace(CGNode node){
		return traceMapping.get(node);
	}



	public boolean addTrace(CGNode node, Trace trace) {
		// TODO Auto-generated method stub
		return false;
	}

	public int compareParent(SyncNode syncNode, INode inode, int sTID, int iTID) {//inode stays in parent thread
		// -1: sync -> comper; 1: comper -> sync; 0: ?
		CGNode iCgNode = inode.getBelonging();
		ArrayList<INode> list = getTrace(iCgNode).getContent();
		int idxI = list.indexOf(inode);
		int idxS = list.indexOf(syncNode);
		if(idxS == -1){
			return furtherCompareParent(syncNode, inode, sTID, iTID);
		}else{
			if(idxS < idxI)
				return -1;
			else
				return 1;
		}
	}

	public int furtherCompareParent(SyncNode sync, INode inode, int stid, int itid){
		//start : < ; join: >
		boolean start = true;
		if(sync instanceof JoinNode){
			start = false;
		}
//		CGNode sCgNode = sync.getBelonging();
//		CGNode iCgNode = inode.getBelonging();
		// -1: sync -> comper; 1: comper -> sync; 0: ?
		HashSet<INode> stops = findTheTopNode(sync, stid);
		HashSet<INode> itops = findTheTopNode(inode, itid);
		if(stops.containsAll(itops) && itops.containsAll(stops)){
			//same origins
			Object[] origins = stops.toArray();
			CGNode origin = (CGNode) ((MethodNode)origins[0]).getBelonging();
			Trace oTrace = getTrace(origin);
			ArrayList<INode> content = oTrace.getContent();
			if(content.contains(sync)){
				return -1;
			}
			if(content.contains(inode)){
				return 1;
			}
		}else{//different origins
			ArrayList<INode> list = getTrace(main).getContent();
			for (INode stop : stops) {
				for (INode itop : itops) {
					int idxS = list.indexOf(stop);
					int idxI = list.indexOf(itop);
					if((idxS < idxI) && start)
						return -1;
					if((idxS > idxI) && !start)
						return 1;
				}
			}
		}
		if(start)
			return 1;
		else
			return -1;
	}


	public HashSet<INode> findTheTopNode(INode node, Integer tid){
		HashSet<INode> tops = new HashSet<>();
		HashSet<CGNode> traversed = new HashSet<>();
		findTheTopNodeOnSinglePath(node, traversed, tid, tops);
		return tops;
	}

	private void findTheTopNodeOnSinglePath(INode iNode, HashSet<CGNode> traversed, Integer tid, HashSet<INode> tops) {
		CGNode inCgNode = iNode.getBelonging();
		if(iNode instanceof StartNode){
			tid = ((StartNode)iNode).getParentTID();
		}
		if(!traversed.contains(inCgNode)){
			traversed.add(inCgNode);
		}else{
			//recursive call chain, not useful
			return;
		}
		if(inCgNode.equals(main)){
			tops.add(iNode);
		}
		HashSet<SHBEdge> inEdges = getAllIncomingEdgeWithTid(inCgNode, tid);
		if(!inEdges.isEmpty()){
			for (SHBEdge inEdge : inEdges) {
				INode iNode0 = inEdge.getSource();
				HashSet<CGNode> traversed0 = new HashSet<>();
				traversed0.addAll(traversed);
				findTheTopNodeOnSinglePath(iNode0, traversed0, tid, tops);
			}
		}else
			return;
	}

	public int compareStartJoin(StartNode start, JoinNode join, CGNode parent) {
		// -1: start -> join; 1: join -> start; 0: ?
		ArrayList<INode> list = getTrace(parent).getContent();
		int idxS = list.indexOf(start);
		int idxJ = list.indexOf(join);
		if(idxJ == -1 || idxS == -1){
			parent = start.getBelonging();
			return compareStartJoin(start, join, parent);
		}else{
			if(idxS < idxJ)
				return -1;
			else
				return 1;
		}
	}

	public int whoHappensFirst(SyncNode sync, INode node, int sTID, int nTID) {//node maybe startnode or r/wnode
		// -1: start1 -> start2; 1: start2 -> start1; 0: ?
		CGNode cgsync = sync.getBelonging();
		CGNode cgnode = node.getBelonging();
		int step1 = edgeManager.getNumOfEdgesToMain(cgsync);
		int step2 = edgeManager.getNumOfEdgesToMain(cgnode);
		if(step1 < step2)
			return -1;
		else
			return 1;
	}

	public void clearOutgoingEdgesFor(CGNode node) {
		//only clear outgoing edges: inode -> other node
		ArrayList<INode> list = getTrace(node).getContent();
		for (INode inode : list) {
			if(edgeManager.containSource(inode)){
				edgeManager.delEdgesWith(inode);
			}
		}
	}

	public HashSet<INode> getInComingSourcesOf(CGNode node) {
		HashSet<INode> sources = new HashSet<>();
		HashSet<SHBEdge> inEdges = getIncomingEdgesOf(node);
		for (SHBEdge in : inEdges) {
			INode source = in.getSource();
			sources.add(source);
		}
		return sources;
	}

	public HashSet<CGNode> getInComingSourcesOf(CGNode node, int tid) {
		HashSet<CGNode> sources = new HashSet<>();
		HashSet<SHBEdge> inEdges = getIncomingEdgesOf(node);
		for (SHBEdge in : inEdges) {
			INode source = in.getSource();
			CGNode scg = source.getBelonging();
			ArrayList<Integer> tids = getTrace(scg).getTraceTids();
			if(tids.contains(tid)){
				sources.add(source.getBelonging());
			}
		}
		return sources;
	}

	public HashSet<CGNode> getOutGoingSinksOf(CGNode ignore) {
		HashSet<CGNode> sinks = new HashSet<>();
		HashSet<SHBEdge> outEdges = getOutGoingEdgesOf(ignore);
		for (SHBEdge out : outEdges) {
			CGNode sink = out.getSink();
			sinks.add(sink);
		}
		return sinks;
	}


	public HashSet<SHBEdge> getOutGoingEdgesOf(CGNode node) {
		HashSet<SHBEdge> edges = new HashSet<>();
		Trace trace = getTrace(node);
		if(trace == null)
			System.out.println();
		ArrayList<INode> list = trace.getContent();
		for (INode inode : list) {
			if(edgeManager.containSource(inode)){
				edges.add(edgeManager.getEdge(inode));
			}
		}
		return edges;
	}

	public HashSet<SHBEdge> getIncomingEdgesOf(CGNode node){
		return edgeManager.getIncomingEdgesOf(node);
	}

	public HashSet<CGNode> clearIncomingEdgesFor(CGNode node){
		//only clear incming edges:other node's inode -> node
		return edgeManager.clearIncomingEdgesFor(node);
	}

	private void clearSourceINodeFor(CGNode node) {
		//remove these inode since their sink cgnode has been removed
		HashSet<INode> sources = getInComingSourcesOf(node);
		for (INode source : sources) {
			Trace trace = getTrace(source.getBelonging());
			if(trace != null){
				trace.remove(source);
			}
		}
	}

	public void addBackEdge(CGNode node, JoinNode jNode) {
		edgeManager.addBackEdge(node, jNode);
	}

	public SHBEdge getIncomingEdgeWithTid(CGNode node, int tid){//may need to return hashset<shbedge>
		return edgeManager.getIncomingEdgeWithTid(node, tid);
	}

	public HashSet<SHBEdge> getAllIncomingEdgeWithTid(CGNode node, int tid){//may need to return hashset<shbedge>
		return edgeManager.getAllIncomingEdgeWithTid(node, tid);
	}

	public void print() {
		System.out.println("Traces: *********** ");
		for (CGNode cgnode : traceMapping.keySet()) {
			Trace trace = traceMapping.get(cgnode);
			System.out.println("    @@ Method " + trace.getTraceTids().toString() + " "+ cgnode.getMethod().toString() + " " + " has Trace" + trace.toString());
			HashSet<SHBEdge> edges = getOutGoingEdgesOf(cgnode);
			if(edges.size() > 0)
				System.out.println("         @@ Its outgoing Edges: *********** ");
			for (SHBEdge edge : edges) {
				System.out.println("               Node " + edge.getEdgeTids() + " "+ edge.getSource().toString() + " leads to Method " + edge.getSink().getMethod().toString());
			}
		}
		System.out.println("FINISHED *********** ");
	}

	/**
	 * assume only one removed; ignore run/main
	 * @param HashSet<String> removed_rw
	 * @return CGNode
	 */
	public CGNode removeNotUsedTrace(HashSet<String> removed_rw) {//should be a set
		//assume only one removed
		for (CGNode node : traceMapping.keySet()) {
			if(node.getMethod().toString().contains("run()V")){
				continue;
			}
			HashSet<SHBEdge> edges = getIncomingEdgesOf(node);
			if(edges == null){
				removed_rw.addAll(getInvolvedRWinTrace(node));
				return node;
			}else{
				if(edges.size() == 0){
					removed_rw.addAll(getInvolvedRWinTrace(node));
					return node;
				}
			}
		}
		//do we remove the trace??
		return null;
	}

	/**
	 * remove isolated traces because of ignore; including run/main/method
	 * @param mayIsolates
	 * @param CGNode ignore
	 * @param HashSet<CGNode> mayIsolates
	 * @return HashSet<CGNode>
	 */
	public HashSet<CGNode> removeNotUsedTrace(HashSet<String> removed_rw, CGNode ignore, HashSet<CGNode> mayIsolates) {
		HashSet<CGNode> removes = new HashSet<>();
		HashSet<CGNode> nextIsolates = new HashSet<>();
		while(!mayIsolates.isEmpty()){
			for (CGNode tar : mayIsolates) {
				HashSet<SHBEdge> inEdges_sink = getIncomingEdgesOf(tar);
				boolean remove = false;
				if(inEdges_sink == null){
					//tar and ignore are the same node, and it recursively call itself. already removed
					continue;
				}
				if(inEdges_sink.size() == 0){
					//should be removed
					removes.add(tar);
					removed_rw.addAll(getInvolvedRWinTrace(tar));
					HashSet<CGNode> mays = getOutGoingSinksOf(tar);
					nextIsolates.addAll(mays);
				}
			}
			mayIsolates = nextIsolates;
			nextIsolates.clear();
		}
		return removes;
	}

	private HashSet<String> getInvolvedRWinTrace(CGNode node){
		HashSet<String> removed_rw = new HashSet<>();
		Trace trace = traceMapping.get(node);
		removed_rw.addAll(trace.getRsigMapping().keySet());
		removed_rw.addAll(trace.getWsigMapping().keySet());
		return removed_rw;
	}

	public void removeTidFromALlTraces(CGNode node, int oldkid) {//and edge
		Trace curTrace = getTrace(node);
		if(!curTrace.removeTid(oldkid)){
			return;
		}
		HashSet<SHBEdge> outgoings = getOutGoingEdgesOf(node);
		if(outgoings != null){
			while(outgoings.size() > 0){
				HashSet<SHBEdge> nexts = new HashSet<>();
				for (SHBEdge outgoing : outgoings) {
					CGNode node2 = outgoing.getSink();
					if(node2 instanceof AstCGNode2){
						node2 = ((AstCGNode2) node2).getCGNode();
					}
					Trace curTrace2 = getTrace(node2);
					if(curTrace2.removeTid(oldkid)){
						nexts.addAll(getOutGoingEdgesOf(node2));
					}
					outgoing.removeTid(oldkid);
				}
				outgoings.clear();
				outgoings.addAll(nexts);
			}
		}
	}

	public boolean includeTidForKidTraces(CGNode node, int newTid) {//and edge
		Trace curTrace = getTrace(node);
		if(curTrace == null){//should not be?? curtrace just created or retreived.
			return false;
		}
		if(!curTrace.includeTid(newTid)){
			return false;
		}
		HashSet<SHBEdge> outgoings = getOutGoingEdgesOf(node);
		if(outgoings != null){
//			int counter = outgoings.size();
//			boolean exit = false;
			while(outgoings.size() > 0){
				HashSet<SHBEdge> nexts = new HashSet<>();
				for (SHBEdge outgoing : outgoings) {
					CGNode node2 = outgoing.getSink();
					if(node2 instanceof AstCGNode2){
						node2 = ((AstCGNode2) node2).getCGNode();//?
					}
					Trace curTrace2 = getTrace(node2);
					if(curTrace2 != null){
						boolean already = !curTrace2.includeTid(newTid);
						if(already){
							continue;
						}
						nexts.addAll(getOutGoingEdgesOf(node2));
						outgoing.includeTid(newTid);
					}
				}
				outgoings.clear();
				outgoings.addAll(nexts);
//				counter += outgoings.size();
			}
//			System.out.println("how many edges i processed: " + counter);
		}
		return true;
	}

	public int getNumOfNodes() {
		ArrayList<Trace> traces = getAllTraces();
		int size = 0;
		for (Trace trace : traces) {
			ArrayList<INode> nodes = trace.getNodes();
			size += nodes.size();
		}
		return size;
	}

	public void reconnectIncomingSHBEdgesFor(Trace caller, Trace callee, CGNode eeCGNode) {
		ArrayList<Integer> erTids = caller.getTraceTids();
		ArrayList<INode> list = caller.getContent();
		for (INode node : list) {
			if(node instanceof MethodNode){
				MethodNode method = (MethodNode) node;
				CGNode target = method.getTarget();
				if(target.equals(eeCGNode)){
					//reconnect
					SHBEdge edge = addEdge(method, target);
					edge.includeTids(erTids);
				}
			}
		}
		//TODO: what if eeCGNode is a run/thread?
	}


//	public boolean replaceEdge(INode inst, CGNode new_method){
// 		if(edgeMapping.containsKey(inst)){
// 			edgeMapping.put(inst, new_method);
// 			return true;
// 		}else{
// 			//no such key
// 			return false;
// 		}
// 	}
}

class EdgeManager {
	//edge node mapping
//	private HashSet<SHBEdge> edges = new HashSet<>();
	private HashMap<INode, SHBEdge> edgeMapping = new HashMap<>();
	private HashMap<CGNode, HashSet<SHBEdge>> re_edgeMapping = new HashMap<>();
	public CGNode main;
	private HashMap<CGNode, JoinNode> backeddges = new HashMap<>();

	public EdgeManager() {
		// TODO Auto-generated constructor stub
	}

	public SHBEdge getEdge(INode inode) {
		return edgeMapping.get(inode);
	}

	public HashMap<INode, SHBEdge> getAllEdges() {
		return edgeMapping;
	}

	public void addBackEdge(CGNode node, JoinNode jNode) {
		//only for join node
		backeddges.put(node, jNode);
	}

	public void delEdgesWith(INode inode) {
		SHBEdge edge = edgeMapping.get(inode);
		CGNode node = edge.getSink();
		edgeMapping.remove(inode, edge);
		re_edgeMapping.get(node).remove(edge);
	}

	public boolean containSource(INode inode) {
		return edgeMapping.containsKey(inode);
	}

	public HashSet<CGNode> clearIncomingEdgesFor(CGNode node) {
		HashSet<SHBEdge> related = re_edgeMapping.get(node);
		HashSet<CGNode> callers = new HashSet<>();
		HashSet<INode> removes = new HashSet<>();
		for (INode inode : edgeMapping.keySet()) {
			SHBEdge edge = edgeMapping.get(inode);
			if(related.contains(edge)){
				removes.add(inode);
				callers.add(inode.getBelonging());
			}
		}
		for (INode remove : removes) {
			edgeMapping.remove(remove);
		}
		re_edgeMapping.remove(node);
		return callers;
	}



	public HashSet<SHBEdge> getIncomingEdgesOf(CGNode node){
		return re_edgeMapping.get(node);
	}

	public void tellMain(CGNode main) {
		this.main = main;
	}

	public int getNumOfEdgesToMain(CGNode node) {
		int steps = -1;
		HashSet<SHBEdge> edges = re_edgeMapping.get(node);
		if(edges != null){
			SHBEdge[] list = edges.toArray(new SHBEdge[0]);
			if(list.length == 0)
				return steps;
			CGNode parent = list[0].getSource().getBelonging();
			steps = 0;
			if(parent == null){//this is already in the main method
				return steps;
			}
			while(!parent.equals(main)){
				steps++;
				edges = re_edgeMapping.get(parent);
				list = edges.toArray(new SHBEdge[0]);
				parent = list[0].getSource().getBelonging();
			}
		}
		return steps;
	}

	public SHBEdge exist(INode inst, CGNode method){
		SHBEdge edge1 = edgeMapping.get(inst);
		if(edge1 == null){//new
			return null;
		}else{
			HashSet<SHBEdge> edges = re_edgeMapping.get(method);
			if(edges.contains(edge1)){
				return edge1;
			}else{//edges !include edge1 or edges == null
				return null;
			}
		}
	}

	public SHBEdge addEdge(INode inst, CGNode method){
		SHBEdge edge = exist(inst, method);
		if(edge == null){
			edge = new SHBEdge(inst, method);
			edgeMapping.put(inst, edge);
			addReverseEdge(method, edge);
		}else{
			int tid = inst.getTID();
			edge.includeTid(tid);
		}
		return edge;
	}

 	private void addReverseEdge(CGNode method, SHBEdge edge) {
 		HashSet<SHBEdge> contains = re_edgeMapping.get(method);
 		if(contains == null){
 			contains = new HashSet<>();
 			contains.add(edge);
 	 		re_edgeMapping.put(method, contains);
 		}else{
 			re_edgeMapping.get(method).add(edge);
 		}
 	}

	public boolean delEdge(INode inst, CGNode method){
		SHBEdge edge = exist(inst, method);
		if(edge == null){ //not exist
			return false;
		}else{
			edgeMapping.remove(inst);
 			delReverseEdge(method, edge);
			return true;
		}
 	}

 	private void delReverseEdge(CGNode method, SHBEdge edge) {
 		HashSet<SHBEdge> contains = re_edgeMapping.get(method);
 		if(contains.contains(edge)){
 			re_edgeMapping.get(method).remove(edge);
 		}else{//not exist
 		}
	}

	public SHBEdge getIncomingEdgeWithTid(CGNode cgnode, int tid) {
		HashSet<SHBEdge> all = getAllIncomingEdgeWithTid(cgnode, tid);
 		//may need to collect all cgnodes
		HashSet<SHBEdge> contains = re_edgeMapping.get(cgnode);
		if(contains == null)
			return null;
		for (SHBEdge shbEdge : contains) {
			if(shbEdge.doesIncludeTid(tid)){
				return shbEdge;
			}
		}
		return null;
	}

	public HashSet<SHBEdge> getAllIncomingEdgeWithTid(CGNode cgnode, int tid) {
		HashSet<SHBEdge> returnValue = new HashSet<>();
		HashSet<SHBEdge> contains = re_edgeMapping.get(cgnode);
		if(contains == null)
			return null;
		for (SHBEdge shbEdge : contains) {
			if(shbEdge.doesIncludeTid(tid)){
				returnValue.add(shbEdge);
			}
		}
		return returnValue;
	}
}
