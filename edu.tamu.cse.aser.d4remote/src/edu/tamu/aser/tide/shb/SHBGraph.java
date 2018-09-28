package edu.tamu.aser.tide.shb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;

import edu.tamu.aser.tide.nodes.INode;
import edu.tamu.aser.tide.nodes.JoinNode;
import edu.tamu.aser.tide.nodes.MethodNode;
import edu.tamu.aser.tide.nodes.StartNode;
import edu.tamu.aser.tide.nodes.SyncNode;

public class SHBGraph{

	private HashMap<String, CGNode> id2CGNode = new HashMap<>();
	private HashMap<String, Trace> traceMapping = new HashMap<>();

	public EdgeManager edgeManager = new EdgeManager();
	public String main;


	public SHBGraph() {
	}

	public HashMap<String, CGNode> getId2CGNode() {
		return id2CGNode;
	}

	public CGNode getNode(String id){
		return id2CGNode.get(id);
	}

	public void mainCGNode(CGNode n) {
		main = n.getMethod().toString();
		edgeManager.tellMain(main);
	}

	public ArrayList<Trace> getAllTraces(){
		ArrayList<Trace> traces = new ArrayList<>();
		for (Iterator<String> iterator = traceMapping.keySet().iterator(); iterator.hasNext();) {
			String node = (String) iterator.next();
			Trace trace = traceMapping.get(node);
			if(trace != null)
				traces.add(trace);
		}
		return traces;
	}

	public int getNumOfEdges(){
		return edgeManager.getAllEdges().keySet().size();
	}

	public boolean addTrace(CGNode cgnode, Trace trace, int tid){
		String node = cgnode.getMethod().toString();
		if(traceMapping.containsKey(node)){
			traceMapping.get(node).includeTid(tid);
			return false;
		}else{
			id2CGNode.put(node, cgnode);
			trace.includeTid(tid);
			traceMapping.put(node, trace);
			return true;
		}
	}


	public boolean delTrace(CGNode cgnode, int tid){
		String node = cgnode.getMethod().toString();
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

	public boolean delTrace(CGNode cgnode){
		String node = cgnode.getMethod().toString();
		if(traceMapping.containsKey(node)){
			//if remove these nodes, when consider back, they are missing, then require re-traversal insts.
			//			clearSourceINodeFor(node);
			clearIncomingEdgesFor(node);
			clearOutgoingEdgesFor(node);
			traceMapping.remove(node);
			id2CGNode.remove(node);
			//what if node only has one kid with one tid?? should delete
			return true;
		}else{
			//no such trace
			return false;
		}
	}

	public boolean replaceTrace(CGNode cgnode, Trace curTrace){
		String node = cgnode.getMethod().toString();
		if(traceMapping.containsKey(node)){
			ArrayList<Integer> tids = traceMapping.get(node).getTraceTids();
			curTrace.includeTids(tids);
			curTrace.clearOldKids();
			traceMapping.put(node, curTrace);
			return true;
		}else{
			System.err.println("SHBG SHOULD HAVE A TRACE FOR " + node + " TO REPLACE.");
			return false;
		}
	}

	public SHBEdge addEdge(INode inst, CGNode method) {
		String node = method.getMethod().toString();
		return edgeManager.addEdge(inst, node);
	}

	public boolean delEdge(INode inst, CGNode method) {
		String node = method.getMethod().toString();
		return edgeManager.delEdge(inst, node);
	}

	public Trace getTrace(CGNode cgnode){
		String node = cgnode.getMethod().toString();
		return traceMapping.get(node);
	}

	public Trace getTrace(String node){
		return traceMapping.get(node);
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
		if(inCgNode.getMethod().toString().equals(main)){
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

	public int compareStartJoin(StartNode start, JoinNode join, CGNode parent, CallGraph cg) {
		// -1: start -> join; 1: join -> start; 0: ?
		ArrayList<INode> list = getTrace(parent).getContent();
		int idxS = list.indexOf(start);
		int idxJ = list.indexOf(join);
		if(idxJ == -1 || idxS == -1){
			CGNode sNode = start.getBelonging();
			CGNode jNode = join.getBelonging();
			CGNode cgParent = null;
			if(sNode.equals(jNode)){
				cgParent = sNode;
			}else{
				Iterator<CGNode> iters = cg.getPredNodes(sNode);
				while(iters.hasNext()){
					CGNode sNode2 = iters.next();
					for(Iterator<CGNode> iterj = cg.getPredNodes(jNode); iterj.hasNext();){
						CGNode jNode2 = iterj.next();
						if(jNode2.equals(sNode2))
							cgParent = sNode2;
					}
				}
			}
			if(cgParent == null){
				return 0;
			}else{
				list = getTrace(cgParent).getContent();
				idxS = list.indexOf(start);
				idxJ = list.indexOf(join);
				if(idxJ == -1 || idxS == -1){
					return 0;
				}else{
					if(idxS < idxJ)
						return -1;
					else
						return 1;
				}
			}
		}else{
			if(idxS < idxJ)
				return -1;
			else
				return 1;
		}
	}

	public int whoHappensFirst(SyncNode sync, INode node, int sTID, int nTID) {//node maybe startnode or r/wnode
		// -1: start1 -> start2; 1: start2 -> start1; 0: ?
		String cgsync = sync.getBelonging().getMethod().toString();
		String cgnode = node.getBelonging().getMethod().toString();
		int step1 = edgeManager.getNumOfEdgesToMain(cgsync);
		int step2 = edgeManager.getNumOfEdgesToMain(cgnode);
		if(step1 < step2)
			return -1;
		else
			return 1;
	}

	public void clearOutgoingEdgesFor(String node) {
		//only clear outgoing edges: inode -> other node
		ArrayList<INode> list = getTrace(node).getContent();
		for (INode inode : list) {
			if(edgeManager.containSource(inode)){
				edgeManager.delEdgesWith(inode);
			}
		}
	}

	public HashSet<INode> getIncomingSourcesOf(CGNode cgnode) {
		String node = cgnode.getMethod().toString();
		HashSet<INode> sources = new HashSet<>();
		HashSet<SHBEdge> inEdges = getIncomingEdgesOf(node);
		for (SHBEdge in : inEdges) {
			INode source = in.getSource();
			sources.add(source);
		}
		return sources;
	}

	public HashSet<CGNode> getIncomingSourcesOf(CGNode cgnode, int tid) {
		String node = cgnode.getMethod().toString();
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

	public HashSet<CGNode> getOutGoingSinksOf(CGNode node) {
		String n = node.getMethod().toString();
		HashSet<CGNode> sinks = new HashSet<>();
		HashSet<SHBEdge> outEdges = getOutGoingEdgesOf(n);
		for (SHBEdge out : outEdges) {
			String sink = out.getSink();
			CGNode cgsink = id2CGNode.get(sink);
			sinks.add(cgsink);
		}
		return sinks;
	}

	public HashSet<CGNode> getOutGoingSinksOf(String ignore) {
		HashSet<CGNode> sinks = new HashSet<>();
		HashSet<SHBEdge> outEdges = getOutGoingEdgesOf(ignore);
		for (SHBEdge out : outEdges) {
			String sink = out.getSink();
			CGNode node = id2CGNode.get(sink);
			sinks.add(node);
		}
		return sinks;
	}


	public HashSet<SHBEdge> getOutGoingEdgesOf(String node) {
		HashSet<SHBEdge> edges = new HashSet<>();
		Trace trace = getTrace(node);
		if(trace != null){
			ArrayList<INode> list = trace.getContent();
			for (INode inode : list) {
				if(edgeManager.containSource(inode)){
					edges.add(edgeManager.getEdge(inode));
				}
			}
		}else{
			System.out.println();
		}
		return edges;
	}

	public HashSet<SHBEdge> getIncomingEdgesOf(String node){
		return edgeManager.getIncomingEdgesOf(node);
	}

	public void clearIncomingEdgesFor(String node){
		//only clear incming edges:other node's inode -> node
		edgeManager.clearIncomingEdgesFor(node);
	}

	private void clearSourceINodeFor(CGNode node) {
		//remove these inode since their sink cgnode has been removed
		HashSet<INode> sources = getIncomingSourcesOf(node);
		for (INode source : sources) {
			Trace trace = getTrace(source.getBelonging());
			if(trace != null){
				trace.remove(source);
			}
		}
	}

	public void addBackEdge(CGNode cgnode, JoinNode jNode) {
		String node = cgnode.getMethod().toString();
		edgeManager.addBackEdge(node, jNode);
	}

	public HashSet<SHBEdge> getIncomingEdgeWithTid(CGNode cgnode, int tid){//may need to return hashset<shbedge>
		String node = cgnode.getMethod().toString();
		return edgeManager.getIncomingEdgeWithTid(node, tid);
	}

	public SHBEdge getIncomingEdgeWithTidForShowTrace(CGNode cgnode, int tid){//may need to return hashset<shbedge>
		String node = cgnode.getMethod().toString();
		return edgeManager.getIncomingEdgeWithTidForShowTrace(node, tid);
	}

	public HashSet<SHBEdge> getAllIncomingEdgeWithTid(CGNode cgnode, int tid){//may need to return hashset<shbedge>
		String node = cgnode.getMethod().toString();
		return edgeManager.getAllIncomingEdgeWithTid(node, tid);
	}

	public void print() {
		System.out.println("Traces: *********** ");
		for (String cgnode : traceMapping.keySet()) {
			Trace trace = traceMapping.get(cgnode);
			System.out.println("    @@ Method " + trace.getTraceTids().toString() + " "+ cgnode  + " " + " has Trace" + trace.toString());
			HashSet<SHBEdge> edges = getOutGoingEdgesOf(cgnode);
			if(edges.size() > 0)
				System.out.println("         @@ Its outgoing Edges: *********** ");
			for (SHBEdge edge : edges) {
				System.out.println("               Node " + edge.getEdgeTids() + " "+ edge.getSource().toString() + " leads to Method " + edge.getSink());
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
		for (String node : traceMapping.keySet()) {
			if(node.contains("run()V")){
				continue;
			}
			HashSet<SHBEdge> edges = getIncomingEdgesOf(node);
			if(edges == null){
				removed_rw.addAll(getInvolvedRWinTrace(node));
				return id2CGNode.get(node);
			}else{
				if(edges.size() == 0){
					removed_rw.addAll(getInvolvedRWinTrace(node));
					return id2CGNode.get(node);
				}
			}
		}
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
			for (CGNode tarnode : mayIsolates) {
				String tar = tarnode.getMethod().toString();
				HashSet<SHBEdge> inEdges_sink = getIncomingEdgesOf(tar);
				if(inEdges_sink == null){
					//tar and ignore are the same node, and it recursively call itself. already removed
					continue;
				}
				if(inEdges_sink.size() == 0){
					//should be removed
					removes.add(tarnode);
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

	private HashSet<String> getInvolvedRWinTrace(String node){
		HashSet<String> removed_rw = new HashSet<>();
		Trace trace = traceMapping.get(node);
		removed_rw.addAll(trace.getRsigMapping().keySet());
		removed_rw.addAll(trace.getWsigMapping().keySet());
		return removed_rw;
	}

	public void removeTidFromAllTraces(CGNode cgnode, int oldkid) {//and edge
		String node = cgnode.getMethod().toString();
		Trace curTrace = getTrace(node);
		if(!curTrace.removeTid(oldkid)){
			return;
		}
		HashSet<SHBEdge> outgoings = getOutGoingEdgesOf(node);
		if(outgoings != null){
			while(outgoings.size() > 0){
				HashSet<SHBEdge> nexts = new HashSet<>();
				for (SHBEdge outgoing : outgoings) {
					String node2 = outgoing.getSink();
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

	public boolean includeTidForKidTraces(CGNode cgnode, int newTid) {//and edge
		String node = cgnode.getMethod().toString();
		Trace curTrace = getTrace(node);
		if(curTrace == null){//should not be?? curtrace just created or retreived.
			return false;
		}
		if(!curTrace.includeTid(newTid)){
			return false;
		}
		HashSet<SHBEdge> outgoings = getOutGoingEdgesOf(node);
		if(outgoings != null){
			while(outgoings.size() > 0){
				HashSet<SHBEdge> nexts = new HashSet<>();
				for (SHBEdge outgoing : outgoings) {
					String node2 = outgoing.getSink();
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
			}
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

}

class EdgeManager {
	//edge node mapping
	private HashMap<INode, SHBEdge> edgeMapping = new HashMap<>();
	private HashMap<String, HashSet<SHBEdge>> re_edgeMapping = new HashMap<>();
	public String main;
	private HashMap<String, JoinNode> backeddges = new HashMap<>();//for join node edges

	public EdgeManager() {
	}

	public SHBEdge getEdge(INode inode) {
		return edgeMapping.get(inode);
	}

	public HashMap<INode, SHBEdge> getAllEdges() {
		return edgeMapping;
	}

	public void addBackEdge(String node, JoinNode jNode) {
		//only for join node
		backeddges.put(node, jNode);
	}

	public void delEdgesWith(INode inode) {
		SHBEdge edge = edgeMapping.get(inode);
		String node = edge.getSink();
		edgeMapping.remove(inode, edge);
		re_edgeMapping.get(node).remove(edge);
	}

	public boolean containSource(INode inode) {
		return edgeMapping.containsKey(inode);
	}

	public void clearIncomingEdgesFor(String node) {
		HashSet<SHBEdge> related = re_edgeMapping.get(node);
		HashSet<INode> removes = new HashSet<>();
		for (INode inode : edgeMapping.keySet()) {
			SHBEdge edge = edgeMapping.get(inode);
			if(related.contains(edge)){
				removes.add(inode);
			}
		}
		for (INode remove : removes) {
			edgeMapping.remove(remove);
		}
		re_edgeMapping.remove(node);
	}



	public HashSet<SHBEdge> getIncomingEdgesOf(String node){
		return re_edgeMapping.get(node);
	}

	public void tellMain(String main) {
		this.main = main;
	}

	public int getNumOfEdgesToMain(String node) {
		int steps = 0;
		HashSet<SHBEdge> edges = re_edgeMapping.get(node);
		if(edges != null){
			SHBEdge[] list = edges.toArray(new SHBEdge[0]);
			if(list.length == 0)
				return steps;
			CGNode cgparent = ((SHBEdge)list[0]).getSource().getBelonging();
			if(cgparent == null){//this is already in the main method
				return steps;
			}
			String parent = cgparent.getMethod().toString();
			while(!parent.equals(main)){
				steps++;
				edges = re_edgeMapping.get(parent);
				list = edges.toArray(new SHBEdge[0]);
				cgparent = list[0].getSource().getBelonging();
				parent = cgparent.getMethod().toString();
			}
		}
		return steps;
	}

	public SHBEdge exist(INode inst, String method){
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

	public SHBEdge addEdge(INode inst, String method){
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

	private void addReverseEdge(String method, SHBEdge edge) {
		HashSet<SHBEdge> contains = re_edgeMapping.get(method);
		if(contains == null){
			contains = new HashSet<>();
			contains.add(edge);
			re_edgeMapping.put(method, contains);
		}else{
			re_edgeMapping.get(method).add(edge);
		}
	}

	public boolean delEdge(INode inst, String method){
		SHBEdge edge = exist(inst, method);
		if(edge == null){ //not exist
			return false;
		}else{
			edgeMapping.remove(inst);
			delReverseEdge(method, edge);
			return true;
		}
	}

	private void delReverseEdge(String method, SHBEdge edge) {
		HashSet<SHBEdge> contains = re_edgeMapping.get(method);
		if(contains.contains(edge)){
			re_edgeMapping.get(method).remove(edge);
		}else{//not exist
		}
	}

	public HashSet<SHBEdge> getIncomingEdgeWithTid(String node, int tid) {
		return getAllIncomingEdgeWithTid(node, tid);
	}

	public SHBEdge getIncomingEdgeWithTidForShowTrace(String node, int tid) {
		//may need to collect all cgnodes
		HashSet<SHBEdge> contains = re_edgeMapping.get(node);
		if(contains == null)
			return null;
		for (SHBEdge shbEdge : contains) {
			if(shbEdge.doesIncludeTid(tid)){
				return shbEdge;
			}
		}
		return null;
	}

	public HashSet<SHBEdge> getAllIncomingEdgeWithTid(String node, int tid) {
		HashSet<SHBEdge> returnValue = new HashSet<>();
		HashSet<SHBEdge> contains = re_edgeMapping.get(node);
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
