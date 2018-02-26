package edu.tamu.aser.tide.engine;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.eclipse.core.resources.IFile;

import com.ibm.wala.cast.java.loader.JavaSourceLoaderImpl.ConcreteJavaMethod;
import com.ibm.wala.cast.loader.AstMethod.DebuggingInformation;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.IMethod.SourcePosition;
import com.ibm.wala.fixedpoint.impl.AbstractFixedPointSolver;
import com.ibm.wala.fixpoint.IVariable;
import com.ibm.wala.ide.util.JdtPosition;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.propagation.AllocationSiteInNode;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ipa.callgraph.propagation.PointsToSetVariable;
import com.ibm.wala.ipa.callgraph.propagation.PropagationGraph;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAArrayLoadInstruction;
import com.ibm.wala.ssa.SSAArrayReferenceInstruction;
import com.ibm.wala.ssa.SSAArrayStoreInstruction;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSACFG.BasicBlock;
import com.ibm.wala.ssa.SSACheckCastInstruction;
import com.ibm.wala.ssa.SSAFieldAccessInstruction;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAMonitorInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.util.graph.dominators.NumberedDominators;
import com.ibm.wala.util.intset.IntSetUtil;
import com.ibm.wala.util.intset.MutableIntSet;
import com.ibm.wala.util.intset.OrdinalSet;

import akka.actor.ActorRef;
import edu.tamu.aser.tide.akkasys.BugHub;
import edu.tamu.aser.tide.akkasys.DistributeDatarace;
import edu.tamu.aser.tide.akkasys.DistributeDeadlock;
import edu.tamu.aser.tide.akkasys.FindSharedVariable;
import edu.tamu.aser.tide.akkasys.IncreRemoveLocalVar;
import edu.tamu.aser.tide.akkasys.IncrementalCheckDatarace;
import edu.tamu.aser.tide.akkasys.IncrementalDeadlock;
import edu.tamu.aser.tide.akkasys.IncrementalRecheckCommonLock;
import edu.tamu.aser.tide.akkasys.RemoveLocalVar;
import edu.tamu.aser.tide.graph.SHBEdge;
import edu.tamu.aser.tide.graph.SHBGraph;
import edu.tamu.aser.tide.graph.Trace;
import edu.tamu.aser.tide.trace.DLLockPair;
import edu.tamu.aser.tide.trace.DLockNode;
import edu.tamu.aser.tide.trace.DUnlockNode;
import edu.tamu.aser.tide.trace.INode;
import edu.tamu.aser.tide.trace.JoinNode;
import edu.tamu.aser.tide.trace.LockPair;
import edu.tamu.aser.tide.trace.MemNode;
import edu.tamu.aser.tide.trace.MethodNode;
import edu.tamu.aser.tide.trace.ReadNode;
import edu.tamu.aser.tide.trace.StartNode;
import edu.tamu.aser.tide.trace.SyncNode;
import edu.tamu.aser.tide.trace.WriteNode;

public class TIDEEngine {

	//	private HashMap<String, HashMap<Integer,Integer>> variableReadMap= new HashMap<String, HashMap<Integer,Integer>>();
	//	private HashMap<String, HashMap<Integer,Integer>> variableWriteMap= new HashMap<String, HashMap<Integer,Integer>>();
//	private HashMap<String, HashSet<Integer>> variableReadMap= new HashMap<>();
//	private HashMap<String, HashSet<Integer>> variableWriteMap= new HashMap<>();
	//count the number of sigs from different traces: only for shared fields
	private HashMap<String, HashMap<Integer, Integer>> rsig_tid_num_map = new HashMap<>();
	private HashMap<String, HashMap<Integer, Integer>> wsig_tid_num_map = new HashMap<>();
	//record shared sigs and nodes
	public HashMap<String, HashSet<ReadNode>> sigReadNodes = new HashMap<String, HashSet<ReadNode>>();
	public HashMap<String, HashSet<WriteNode>> sigWriteNodes = new HashMap<String, HashSet<WriteNode>>();
	//record the ignored sigs by users
	public HashSet<String> excludedSigForRace = new HashSet<>();
	public HashMap<String, HashSet<ReadNode>> excludedReadSigMapping = new HashMap<>();
	public HashMap<String, HashSet<WriteNode>> excludedWriteSigMapping = new HashMap<>();
	//record the ignored function by users
	public HashSet<CGNode> excludedMethodForBugs = new HashSet<>();
	//to check isolates: only for testing
	public HashMap<CGNode, HashSet<CGNode>> excludedMethodIsolatedCGNodes = new HashMap<>();

	private LinkedList<CGNode> alreadyProcessedNodes = new LinkedList<CGNode>();
	private LinkedList<CGNode> twiceProcessedNodes = new LinkedList<CGNode>();
	private LinkedList<CGNode> thirdProcessedNodes = new LinkedList<CGNode>();
	private LinkedList<CGNode> scheduledAstNodes = new LinkedList<CGNode>();


	private LinkedList<CGNode> mainEntryNodes = new LinkedList<CGNode>();
	private LinkedList<CGNode> threadNodes = new LinkedList<CGNode>();

	private MutableIntSet stidpool = IntSetUtil.make();//start
	private HashMap<Integer, AstCGNode2> dupStartJoinTidMap = new HashMap<>();//join with dup tid
	private HashMap<TypeName, CGNode> threadSigNodeMap = new HashMap<TypeName,CGNode>();

	private boolean hasSyncBetween = false;
//	private HashMap<CGNode, CGNode> newRunTargets = new HashMap<>();

	public HashMap<Integer, StartNode> mapOfStartNode = new HashMap<>();
	public HashMap<Integer, JoinNode> mapOfJoinNode = new HashMap<>();
	//lock pairs for deadlock
	public HashMap<Integer, ArrayList<DLLockPair>> threadDLLockPairs = new HashMap<Integer, ArrayList<DLLockPair>>();
	//currently locked objects
	public 	HashMap<Integer, HashSet<DLockNode>> threadLockNodes = new HashMap<Integer, HashSet<DLockNode>>();
	//node <->inloop created astnode
	public HashMap<CGNode, AstCGNode2> n_loopn_map = new HashMap<>();

	protected CallGraph callGraph;
	public PointerAnalysis<InstanceKey> pointerAnalysis;//protected
	protected PropagationGraph propagationGraph;
	private int maxGraphNodeID;

	public long timeForDetectingRaces = 0;
	public long timeForDetectingDL = 0;

	//globalized
	public HashSet<String> sharedFields = new HashSet<String>();

	public HashSet<ITIDEBug> bugs = new HashSet<ITIDEBug>();
	public HashSet<ITIDEBug> removedbugs = new HashSet<ITIDEBug>();
	public HashSet<ITIDEBug> addedbugs = new HashSet<ITIDEBug>();

	//added
	public ActorRef bughub;
	public SHBGraph shb;
	//	//to track changes from pta
	public HashMap<PointerKey, HashSet<MemNode>> pointer_rwmap = new HashMap<>();
	public HashMap<PointerKey, HashSet<SyncNode>> pointer_lmap = new HashMap<>();

	public int curTID;
	public HashMap<CGNode, Integer> astCGNode_ntid_map = new HashMap<>();//HashMap<CGNode, hashset<Integer>>??
	public boolean useMayAlias = true;//false => lockObject.size == 1
	private static boolean PLUGIN = false;

	public static void setPlugin(boolean b){
		PLUGIN = b;
	}

	String special = "";

	public TIDEEngine(String entrySignature,CallGraph callGraph, PropagationGraph flowgraph, PointerAnalysis<InstanceKey> pointerAnalysis, ActorRef bughub){
		this.callGraph = callGraph;//?update
		this.pointerAnalysis = pointerAnalysis;//?update
		this.maxGraphNodeID = callGraph.getNumberOfNodes() + 1000;//? update
		this.propagationGraph = flowgraph;//?update
		this.bughub = bughub;
		Collection<CGNode> cgnodes = callGraph.getEntrypointNodes();
		for(CGNode n: cgnodes){
			String sig = n.getMethod().getSignature();
			//find the main node
			if(sig.contains(entrySignature)){
				mainEntryNodes.add(n);
				if(sig.contains("net.sourceforge.pmd.cpd")){
					special = "pmd";
				}else if(sig.contains("org.codehaus.janino.samples.ExpressionDemo")){
					special = "tomcat";
				}else if(sig.contains("org.apache.xerces.impl.xpath.regex.REUtil")){
					special = "trade";
				}else if(sig.contains("org.apache.xalan.processor.XSLProcessorVersion")){
					special = "xalan";
				}
			}else{
				TypeName name  = n.getMethod().getDeclaringClass().getName();
				threadSigNodeMap.put(name, n);
			}
		}
	}


	private ArrayList<StartNode> shareGrandParent(int earlier, int later) {
		int parentoflater = mapOfStartNode.get(later).getParentTID();
		int grandpoflater = mapOfStartNode.get(parentoflater).getParentTID();
		if(grandpoflater == -1){
			return null;
		}else if(earlier == grandpoflater){
			StartNode grandp = mapOfStartNode.get(grandpoflater);
			StartNode parent = mapOfStartNode.get(parentoflater);
			ArrayList<StartNode> result = new ArrayList<>();
			result.add(grandp);
			result.add(parent);
			return result;
		}else{
			return shareGrandParent(earlier, parentoflater);
		}
	}


	public HashSet<ITIDEBug> detectBothBugs(PrintStream ps) {

		long start = System.currentTimeMillis();

		for(CGNode main: mainEntryNodes){
			//threadSigNodeMap.clear();
			//threadNodes.clear();
			twiceProcessedNodes.clear();
			alreadyProcessedNodes.clear();//a new tid
			thirdProcessedNodes.clear();
//			variableWriteMap.clear();
//			variableReadMap.clear();
			//			trace.clear();
			mapOfStartNode.clear();
			mapOfJoinNode.clear();
			stidpool.clear();
			threadDLLockPairs.clear();
			rsig_tid_num_map.clear();
			wsig_tid_num_map.clear();
			sharedFields.clear();
			sigReadNodes.clear();
			sigWriteNodes.clear();
			pointer_lmap.clear();
			pointer_rwmap.clear();
			excludedSigForRace.clear();
			excludedReadSigMapping.clear();
			excludedWriteSigMapping.clear();
			excludedMethodForBugs.clear();
//			threadSyncNodes.clear();
//			lockEngine = new LockSetEngine();
			bugs.clear();
			astCGNode_ntid_map.clear();
			shb = new SHBGraph();

//			if(mainEntryNodes.size() >1 )
//				System.err.println("MORE THAN 1 MAIN ENTRY!");

			//main
			threadNodes.add(main);
			int mainTID = main.getGraphNodeId();
			stidpool.add(mainTID);
			//find main node ifile
			SSAInstruction[] insts = main.getIR().getInstructions();
			SSAInstruction inst1st = null;
			for(int i=0; i<insts.length; i++){
				SSAInstruction inst = insts[i];
				if(inst!=null){
					inst1st = inst;
					break;
				}
			}

			IMethod method = main.getMethod();
			IFile file = null;
			int sourceLineNum = 0;
			try{//get source code line number of this inst
				if(main.getIR().getMethod() instanceof IBytecodeMethod){
					int bytecodeindex = ((IBytecodeMethod) main.getIR().getMethod()).getBytecodeIndex(inst1st.iindex);
					sourceLineNum = (int)main.getIR().getMethod().getLineNumber(bytecodeindex);
				}else{
					SourcePosition position = main.getMethod().getSourcePosition(inst1st.iindex);
					sourceLineNum = position.getFirstLine();//.getLastLine();
					if(position instanceof JdtPosition){
						file = ((JdtPosition) position).getEclipseFile();
					}
				}
				//					System.out.println(inst.iindex);
			}catch(Exception e){
				e.printStackTrace();
			}
			//
			StartNode mainstart = new StartNode(-1, mainTID,null, main, sourceLineNum -1, file);//?
//			inst_start_map.put(main, mainstart);
			mapOfStartNode.put(mainTID, mainstart);
			//add edge in shb
			shb.mainCGNode(main);
			shb.addEdge(mainstart, main);

			if(special.equals("pmd") || special.equals("tomcat") || special.equals("trade") || special.equals("xalan")){
				for (TypeName name : threadSigNodeMap.keySet()) {
					CGNode kidnode = threadSigNodeMap.get(name);
					int threadID = kidnode.getGraphNodeId();
					StartNode clientstart = new StartNode(mainTID, threadID, main, kidnode, sourceLineNum, file);
					mapOfStartNode.put(threadID, clientstart);
					mapOfStartNode.get(mainTID).addChild(threadID);
					threadNodes.add(kidnode);
				}
			}

			while(!threadNodes.isEmpty()){
				CGNode n = threadNodes.removeFirst();
				curTID = n.getGraphNodeId();

				if(n instanceof AstCGNode2){
					CGNode real = ((AstCGNode2)n).getCGNode();
					if(thirdProcessedNodes.contains(real))//already processed once
						continue;
					else
						thirdProcessedNodes.add(real);
				}else{
					//only twice at most for a node
					if(alreadyProcessedNodes.contains(n))
						if (twiceProcessedNodes.contains(n))
							continue;
						else
							twiceProcessedNodes.add(n);
				}

				//			alreadyProcessedNodes.clear();//a new tid
				hasSyncBetween = false;
				//				System.out.println("traversing  ----- " + n.toString());


				traverseNodePN(n); //path insensitive
				//			traverseNode(n);  //path sensitive
			}

			//print shb
			//			System.err.println("-----------------traces-------------------");
			//			ArrayList<Trace> traces = shb.getAllTraces();
			//			for (Trace trace : traces) {
			//				System.out.println(trace.getContent().toString());
			//			}


			//extended happens-before relation
			organizeThreadsRelations();// grand kid threads
//			if(mapOfStartNode.size() == 1){
//				System.out.println("ONLY HAS MAIN THREAD, NO NEED TO PROCEED:   " + main.getMethod().toString());
//				mapOfStartNode.clear();
//				mapOfJoinNode.clear();
//				continue;
//			}else{
//				System.out.println("mapOfStartNode =========================");
//				for (Integer tid : mapOfStartNode.keySet()) {
//					System.out.println(mapOfStartNode.get(tid).toString());
//				}
//				System.out.println("mapOfJoinNode =========================");
//				for (Integer tid : mapOfJoinNode.keySet()) {
//					System.out.println(mapOfJoinNode.get(tid).toString());
//				}
//				System.out.println();
//			}

			//race detection
//			System.err.println("Start to detect race: find shared variables");

			//organize lockengine
//			lockEngine.organizeEngine();
//			organizeThreadDlockpairs();//?threadDLLockPairs
			//analyze trace
			//1. find shared variables
			//organize variable read/write map
			organizeRWMaps();
			if(wsig_tid_num_map.size() >= 10){
				//use hub to speed up
				bughub.tell(new FindSharedVariable(rsig_tid_num_map, wsig_tid_num_map), bughub);
				awaitBugHubComplete();
			}else{
				//seq
				for(String sig: wsig_tid_num_map.keySet()){
					HashMap<Integer, Integer> writeTids = wsig_tid_num_map.get(sig);
					if(writeTids.size()>1){
						sharedFields.add(sig);
					}else{
						if(rsig_tid_num_map.containsKey(sig)){
							HashMap<Integer, Integer> readTids = rsig_tid_num_map.get(sig);
							if(readTids!=null){
								if(readTids.size() + writeTids.size() > 1){
									sharedFields.add(sig);
								}
							}
						}
					}
				}
			}


//			System.err.println("Start to detect race: remove local nodes");

			//2. remove local nodes
			//			ReachabilityEngine reachEngine = new ReachabilityEngine();
			//			HashMap<String,LockNode> lockcurrentNode = new HashMap<String,LockNode>();
			//			HashMap<String,Integer> lockcurrentCount = new HashMap<String,Integer>();

			//seperate into two parts: main process syncnode, hub process r/wnodes
			//tell hub jobs
			bughub.tell(new RemoveLocalVar(), bughub);
			awaitBugHubComplete();

			//3. performance race detection with Fork-Join
			bughub.tell(new DistributeDatarace(), bughub);
			awaitBugHubComplete();

			timeForDetectingRaces = timeForDetectingRaces + (System.currentTimeMillis() - start);
			start = System.currentTimeMillis();


			//detect deadlock
			bughub.tell(new DistributeDeadlock(), bughub);
			awaitBugHubComplete();

			timeForDetectingDL = timeForDetectingDL + (System.currentTimeMillis() -start);
		}

//		System.err.println("Initial Race Detection Time: " + timeForDetectingRaces);
//		System.err.println("Initial Deadlock Detection Time: " + timeForDetectingDL);
//		ps.print("Total Race Detection Time: " + timeForDetectingRaces);
//		ps.println();
//		ps.print("Total Deadlock Detection Time: " + timeForDetectingDL);
//		ps.println();
//		ps.println();

		//		writeRaceTime(timeForDetectingRaces);
		//		writeDlTime(timeForDetectingDL);
		//      ps.print(timeForDetectingRaces +" "+timeForDetectingDL+" ");
		bugs.removeAll(removedbugs);
		bugs.addAll(addedbugs);
		return bugs;
	}


	//	private ArrayList<Trace> constractWRNodesFromSHB() {//need to update
	//		LinkedList<INode> results = new LinkedList<>();
	//		ArrayList<Trace> traces = shb.getAllrwTraces();
	//		for (int i = 0; i < traces.size(); i++) {
	//			Trace t = traces.get(i);
	//			results.addAll(t.getContent());
	//		}
	//		return traces;
	//	}

//	private void organizeThreadDlockpairs() {
//		for (int tid : threadDLLockPairs.keySet()) {
//			ArrayList<DLLockPair> threadlocks = threadDLLockPairs.get(tid);
//			for (DLLockPair pair : threadlocks) {
//				DLockNode lockNode = pair.lock1;
//				ArrayList<Integer> tids = shb.getTrace(lockNode.getBelonging()).getTraceTids();
//				for (Integer tid_add : tids) {
//					ArrayList<DLLockPair> exists = threadDLLockPairs.get(tid_add);
//					if(exists == null){
//						exists = new ArrayList<>();
//					}
//					exists.add(pair);
//					threadDLLockPairs.put(tid_add, exists);
//				}
//			}
//		}
//
//	}

/**
 * collect the rwnode sig from all trace, and count the number
 */
	private void organizeRWMaps() {//parallel?
		ArrayList<Trace> alltraces = shb.getAllTraces();
		for (Trace trace : alltraces) {
			//sig-tid-num map
			singleOrganizeRWMaps(trace);
		}
	}


	private void singleOrganizeRWMaps(Trace trace) {
		HashMap<String, ArrayList<ReadNode>> rsigMapping = trace.getRsigMapping();
		HashMap<String, ArrayList<WriteNode>> wsigMapping = trace.getWsigMapping();
		ArrayList<Integer> tids = trace.getTraceTids();
		//read
		for (String rsig : rsigMapping.keySet()) {
			HashMap<Integer, Integer> tidnummap = rsig_tid_num_map.get(rsig);
			if(tidnummap == null){
				tidnummap = new HashMap<>();
				for (Integer tid : tids) {
					tidnummap.put(tid, 1);
				}
				rsig_tid_num_map.put(rsig, tidnummap);
			}else{
				for (Integer tid : tids) {
					if(tidnummap.keySet().contains(tid)){
						int num = tidnummap.get(tid);
						tidnummap.replace(tid, ++num);
					}else{
						tidnummap.put(tid, 1);
					}
				}
			}
		}
		//write
		for (String wsig : wsigMapping.keySet()) {
			HashMap<Integer, Integer> tidnummap = wsig_tid_num_map.get(wsig);
			if(tidnummap == null){
				tidnummap = new HashMap<>();
				for (Integer tid : tids) {
					tidnummap.put(tid, 1);
				}
				wsig_tid_num_map.put(wsig, tidnummap);
			}else{
				for (Integer tid : tids) {
					if(tidnummap.keySet().contains(tid)){
						int num = tidnummap.get(tid);
						tidnummap.replace(tid, ++num);
					}else{
						tidnummap.put(tid, 1);
					}
				}
			}
		}
	}


	private void awaitBugHubComplete() {
		boolean goon = true;
		while(goon){
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			goon = BugHub.askstatus();
		}
	}





	private void organizeThreadsRelations() {
		//start nodes --> add kids
		Iterator<Integer> tids = mapOfStartNode.keySet().iterator();
		LinkedList<Integer> reverse_tids = new LinkedList<>();
		while(tids.hasNext()){
			int cur_tid = tids.next();
			reverse_tids.addFirst(cur_tid);
		}
		//kid and grand kids
		Iterator<Integer> reverse_iter = reverse_tids.iterator();
		while(reverse_iter.hasNext()){
			int cur_tid = reverse_iter.next();
			StartNode cur_node = mapOfStartNode.get(cur_tid);
			int direct_kid = cur_node.getSelfTID();
			StartNode dkid_node = mapOfStartNode.get(direct_kid);
			if(dkid_node != null){
				MutableIntSet grand_kids = dkid_node.getTID_Child();
				if(!grand_kids.isEmpty()){
					cur_node.addChildren(grand_kids);
				}
			}
		}
		//join nodes --> add parents
		tids = mapOfJoinNode.keySet().iterator();
		reverse_tids = new LinkedList<>();
		while(tids.hasNext()){
			int cur_tid = tids.next();
			reverse_tids.addFirst(cur_tid);
		}
		reverse_iter = reverse_tids.iterator();
		while(reverse_iter.hasNext()){
			int cur_tid = reverse_iter.next();
			JoinNode cur_node = mapOfJoinNode.get(cur_tid);
			int direct_parent = cur_node.getParentTID();
			JoinNode dparent_node = mapOfJoinNode.get(direct_parent);
			if(dparent_node != null){
				MutableIntSet grand_parents = dparent_node.getTID_Parents();
				if(!grand_parents.isEmpty()){
					cur_node.addParents(grand_parents);
				}
			}

		}
	}


	//	private boolean checkLockSetAndHappensBefore(HashMap<Integer, LinkedList<SyncNode>> threadSyncNodes,
	//			LockSetEngine lockEngine, WriteNode wnode, MemNode xnode) {//ReachabilityEngine reachEngine,
	//		if(xnode.getTID() != wnode.getTID()){
	//			if(!lockEngine.hasCommonLock(xnode.getTID(), trace.indexOf(xnode), wnode.getTID(), trace.indexOf(wnode))){
	//				boolean isRace = false;
	//				int wTID = wnode.getTID();
	//				int xTID = xnode.getTID();
	//				StartNode wStartNode = mapOfStartNode.get(wTID);
	//				StartNode xStartNode = mapOfStartNode.get(xTID);
	//				MutableIntSet wkids = wStartNode.getTID_Child();
	//				MutableIntSet xkids = xStartNode.getTID_Child();
	//
	//				if(wkids.contains(xTID)){
	//					//wtid is parent of xtid
	//					if(trace.indexOf(xStartNode) < trace.indexOf(wnode)){
	//						isRace = true;
	//					}
	//				}else if(xkids.contains(wTID)){
	//					//xtid is parent of wtid
	//					if(trace.indexOf(wStartNode) < trace.indexOf(xnode)){
	//						isRace = true;
	//					}
	//				}else{
	//					StartNode sNode = sameParent(wTID, xTID);
	//					if(sNode != null){
	//						//same parent
	//						JoinNode wJoinNode = mapOfJoinNode.get(wTID);
	//						JoinNode xJoinNode = mapOfJoinNode.get(xTID);
	//						if(wJoinNode == null && xJoinNode == null){
	//							//should check the distance
	//							isRace = true;
	//						}else if(wJoinNode == null){
	//							if(trace.indexOf(xJoinNode) > trace.indexOf(wStartNode)){
	//								isRace = true;
	//							}
	//						}else if(xJoinNode == null){
	//							if(trace.indexOf(wJoinNode) > trace.indexOf(xStartNode)){
	//								isRace = true;
	//							}
	//						}else{
	//							if((trace.indexOf(xJoinNode) > trace.indexOf(wStartNode))
	//									&& (trace.indexOf(wJoinNode) > trace.indexOf(xStartNode))){
	//								isRace = true;
	//							}
	//						}
	//					}else{
	//						//other conditions
	//						int earlier;
	//						int later;
	//						if(trace.indexOf(wStartNode) < trace.indexOf(xStartNode)){
	//							//wtid starts early
	//							earlier = wTID;
	//							later = xTID;
	//							//							System.out.println("earlier: "+earlier + "  later: "+later);
	//							//check relation
	//							ArrayList<StartNode> relatives = shareGrandParent(earlier, later);
	//							if(relatives == null){
	//								isRace = false;
	//							}else {
	//								StartNode parenStart = relatives.get(1);
	//								if(trace.indexOf(wnode) > trace.indexOf(parenStart)){
	//									isRace = true;
	//								}
	//							}
	//						}else{
	//							earlier = xTID;
	//							later = wTID;
	//							//							System.out.println("earlier: "+earlier + "  later: "+later);
	//							//check relation
	//							ArrayList<StartNode> relatives = shareGrandParent(earlier, later);
	//							if(relatives == null){
	//								isRace = false;
	//							}else{
	//								StartNode parenStart = relatives.get(1);
	//								if(trace.indexOf(xnode) > trace.indexOf(parenStart)){
	//									isRace = true;
	//								}
	//							}
	//						}
	//					}
	//				}
	//
	//				//				LinkedList<SyncNode> list = threadSyncNodes.get(xnode.getTID());
	//				//				if(list!=null)
	//				//					for(int k=0;k<list.size();k++)
	//				//					{
	//				//						SyncNode sn = list.get(k);
	//				//						if(sn instanceof StartNode)
	//				//						{
	//				//							if(sn.getGID()>xnode.getGID())
	//				//							{
	//				//								if(reachEngine.canReach(sn.getGID()+"", wnode.getTID()+"s"))
	//				//								{	isRace = false; break;}
	//				//							}
	//				//						}
	//				//						else if(sn instanceof JoinNode)
	//				//						{
	//				//							//join
	//				//							if(sn.getGID()<xnode.getGID())
	//				//							{
	//				//								if(reachEngine.canReach(wnode.getTID()+"e",sn.getGID()+""))
	//				//								{	isRace = false; break;}
	//				//							}
	//				//						}
	//				//					}
	//				//
	//				//				if(isRace)
	//				//				{
	//				//					LinkedList<SyncNode> list2 = threadSyncNodes.get(wnode.getTID());
	//				//					if(list2!=null)
	//				//						for(int k=0;k<list2.size();k++)
	//				//						{
	//				//							SyncNode sn = list2.get(k);
	//				//							if(sn instanceof StartNode)
	//				//							{
	//				//								if(sn.getGID()>wnode.getGID())
	//				//								{
	//				//									if(reachEngine.canReach(sn.getGID()+"", xnode.getTID()+"s"))
	//				//									{	isRace = false; break;}
	//				//								}
	//				//							}
	//				//							else if(sn instanceof JoinNode)
	//				//							{
	//				//								//join
	//				//								if(sn.getGID()<wnode.getGID())
	//				//								{
	//				//									if(reachEngine.canReach(xnode.getTID()+"e",sn.getGID()+""))
	//				//									{	isRace = false; break;}
	//				//								}
	//				//							}
	//				//						}
	//				//				}
	//				return isRace;
	//			}
	//		}
	//		return false;
	//	}


//	private void addToThreadSyncNodes(HashSet<DLockNode> wills){
//		LinkedList<SyncNode> syncNodes = threadSyncNodes.get(curTID);
//		if(syncNodes==null){
//			syncNodes = new LinkedList<SyncNode>();
//			threadSyncNodes.put(curTID,syncNodes);
//		}
//		syncNodes.addAll(wills);
//	}

//	private void addToThreadSyncNodes(SyncNode node){
//		LinkedList<SyncNode> syncNodes = threadSyncNodes.get(curTID);
//		if(syncNodes==null){
//			syncNodes = new LinkedList<SyncNode>();
//			threadSyncNodes.put(curTID,syncNodes);
//		}
//		syncNodes.add(node);
//	}

	//HashMap<cgnode, HashMap<int, invoke>>
	private HashMap<Integer, SSAAbstractInvokeInstruction> threadInits = new HashMap<>();

	private Trace traverseNodePN(CGNode n) { //path insensitive
		Trace curTrace = shb.getTrace(n);
		if(!(n instanceof AstCGNode2)){
			if(alreadyProcessedNodes.contains(n)){
				//allow multiple entries of a method if there exist sync in between
				if(!hasSyncBetween){
					if(curTrace == null){
						curTrace = new Trace(curTID);
						shb.addTrace(n, curTrace, curTID);
						return curTrace;
					}else if(!change){
						return curTrace;
					}
				}else{
					hasSyncBetween = false;
				}
			}
			alreadyProcessedNodes.add(n);
		}

		//create new trace if not in shbgraph
		if(curTrace != null){
			if(!curTrace.doesIncludeTid(curTID)){
//				curTrace.includeTid(curTID);
				if(change){
					if(curTrace.ifHasJoin() || curTrace.ifHasStart()){
						return traverseNodePN2(curTrace, n);
					}else{
						shb.includeTidForKidTraces(n, curTID);
						return curTrace;
					}
				}else{
					//exist edges include new tid>>
					traverseNodePN2(curTrace, n);
				}
			}
			return curTrace;
		}else{
			if(n instanceof AstCGNode2){
				n = ((AstCGNode2)n).getCGNode();
			}
			curTrace = new Trace(curTID);
		}

		//		System.out.println("Traverse Node: "+ n.toString());
		//		if(alreadyProcessedNodes.contains(n)){
		//			//allow multiple entries of a method if there exist sync in between
		//			if(!hasSyncBetween)
		//				return null;
		//			else
		//				hasSyncBetween = false;
		//		}
		//		alreadyProcessedNodes.add(n);

		//add back to shb
		shb.addTrace(n, curTrace, curTID);

		if(n.getIR() == null)
			return null;
//		if(change){
//			//let curtrace edges include new tids
//			ArrayList<SHBEdge> edges = shb.getOutGoingEdgesOf(n);
//			for (SHBEdge edge : edges) {
//				edge.includeTid(curTID);
//			}
//		}
		//start traverse inst
		SSACFG cfg = n.getIR().getControlFlowGraph();
//				System.out.println(cfg.toString());

		HashSet<SSAInstruction> catchinsts = InstInsideCatchBlock(cfg);

		SSAInstruction[] insts = n.getIR().getInstructions();

		for(int i=0; i<insts.length; i++){
			SSAInstruction inst = insts[i];

			if(inst!=null){
				if(catchinsts.contains(inst)){
					continue;
				}
				//					System.out.println(bb.toString());
				//				System.out.println(" ------ " + inst.toString());
				IMethod method = n.getMethod();
				IFile file = null;
				int sourceLineNum = -1;
				if(!method.isSynthetic()){
					try{//get source code line number of this inst
						if(n.getIR().getMethod() instanceof IBytecodeMethod){
							int bytecodeindex = ((IBytecodeMethod) n.getIR().getMethod()).getBytecodeIndex(inst.iindex);
							sourceLineNum = (int)n.getIR().getMethod().getLineNumber(bytecodeindex);
						}else{
							//						sourceLineNum = n.getMethod().getSourcePosition(inst.iindex).getFirstLine();//.getLastLine();
							SourcePosition position = n.getMethod().getSourcePosition(inst.iindex);
							sourceLineNum = position.getFirstLine();//.getLastLine();
							if(position instanceof JdtPosition){
								file = ((JdtPosition) position).getEclipseFile();
							}
						}
						//					System.out.println(inst.iindex);
					}catch(Exception e){
						e.printStackTrace();
					}
				}

				//System.out.println(inst.toString());
				if(inst instanceof SSAFieldAccessInstruction){

					//not in constructor
					if(n.getMethod().isClinit()||n.getMethod().isInit())
						continue;
					//field access before monitorenter, check
					if(i+1 < insts.length){
						SSAInstruction next = insts[i+1];
						if(next instanceof SSAMonitorInstruction){
							SSAFieldAccessInstruction access = (SSAFieldAccessInstruction)inst;
							int result = access.getDef();//result
							int locked = ((SSAMonitorInstruction) next).getRef();
							if(result == locked){
								//pre-read of lock/monitor enter, do not record
								//check previous read
								if(i-1 >= 0){
									SSAInstruction pred = insts[i-1];
									int ref = access.getRef();
									if(pred instanceof SSAGetInstruction){
										int result2 = ((SSAGetInstruction) pred).getDef();//result
										if(result2 == ref && result2 != -1 && ref != -1){
											//another field access before monitorenter, ignore
											//removed node in trace
											curTrace.removeLastNode();
										}
									}
								}
								continue;
							}
						}
					}
					//TODO: handling field access of external objects

					String classname = ((SSAFieldAccessInstruction)inst).getDeclaredField().getDeclaringClass().getName().toString();
					String fieldname = ((SSAFieldAccessInstruction)inst).getDeclaredField().getName().toString();
					String sig = classname.substring(1)+"."+fieldname;

					String typeclassname =  method.getDeclaringClass().getName().toString();
					String instSig =typeclassname.substring(1)+":"+sourceLineNum;

					if(((SSAFieldAccessInstruction)inst).isStatic()){
						//						logFieldAccess(inst, sig, sourceLineNum, instSig, curTrace);
						logFieldAccess3(inst, sourceLineNum, instSig, curTrace, n, null, null, sig, file);
					}else{

						int baseValueNumber = ((SSAFieldAccessInstruction)inst).getUse(0);
						if(baseValueNumber==1){//this.f
							PointerKey basePointer = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, baseValueNumber);//+
							OrdinalSet<InstanceKey> baseObjects = pointerAnalysis.getPointsToSet(basePointer);//+
							logFieldAccess3(inst, sourceLineNum, instSig, curTrace, n, basePointer, baseObjects, sig, file);
							//							if(curReceivers!=null){
							//								for(String receiver : curReceivers){
							//									String sig2 = sig+"."+receiver;
							//									logFieldAccess(inst, sig2, sourceLineNum, instSig, curTrace);
							////									System.out.println(sig2);
							//								}
							//							}
						}else{
							PointerKey basePointer = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, baseValueNumber);
							OrdinalSet<InstanceKey> baseObjects = pointerAnalysis.getPointsToSet(basePointer);
							//							//mark pointer
							//							ArrayList<Integer> traceidx = pointer_traceidx_rwmap.get(basePointer);
							//							if(traceidx == null){
							//								traceidx = new ArrayList<>();
							//								traceidx.add(trace.size());
							//								pointer_traceidx_rwmap.put(basePointer, traceidx);
							//							}else{
							//								pointer_traceidx_rwmap.get(basePointer).add(trace.size()-1);
							//							}

							logFieldAccess3(inst, sourceLineNum, instSig, curTrace, n, basePointer, baseObjects, sig, file);
							//							for (InstanceKey instanceKey : baseObjects) {
							//								if(curReceivers==null||curReceivers.isEmpty()){
							//									String sig2 = sig+"."+String.valueOf(instanceKey.hashCode());
							//									logFieldAccess(inst, sig2, sourceLineNum, instSig, curTrace);
							//									//									System.out.println(sig2);
							//								}else{
							//									for(String receiver : curReceivers){
							//										String sig2 = sig+"."+receiver+"Y"+String.valueOf(instanceKey.hashCode());
							//										logFieldAccess(inst, sig2, sourceLineNum, instSig, curTrace);
							//										//										System.out.println(sig2);
							//									}
							//								}
							//							}
							//							//mark pointer
							//							pointer_traceidx_rwmap.get(basePointer).add(trace.size()-1);
						}
					}
				}
				else if (inst instanceof SSAArrayReferenceInstruction)
				{
					SSAArrayReferenceInstruction arrayRefInst = (SSAArrayReferenceInstruction) inst;
					int	arrayRef = arrayRefInst.getArrayRef();
					String typeclassname =  method.getDeclaringClass().getName().toString();
					String instSig =typeclassname.substring(1)+":"+sourceLineNum;
					PointerKey key = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, arrayRef);
					OrdinalSet<InstanceKey> instances = pointerAnalysis.getPointsToSet(key);
					String field = handleArrayTypes(arrayRefInst, n, instances);
					logArrayAccess3(inst, sourceLineNum, instSig, curTrace, n, key, instances, file, field);

				}else if (inst instanceof SSAAbstractInvokeInstruction){

					CallSiteReference csr = ((SSAAbstractInvokeInstruction)inst).getCallSite();
					MethodReference mr = csr.getDeclaredTarget();
					//if (AnalysisUtils.implementsRunnableInterface(iclass) || AnalysisUtils.extendsThreadClass(iclass))
					{
						com.ibm.wala.classLoader.IMethod imethod = callGraph.getClassHierarchy().resolveMethod(mr);
						if(imethod!=null){
							String sig = imethod.getSignature();
							//System.out.println("Invoke Inst: "+sig);
							if(sig.equals("java.lang.Thread.start()V")){
								//Executors and ThreadPoolExecutor
								PointerKey key = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, ((SSAAbstractInvokeInstruction) inst).getReceiver());
								OrdinalSet<InstanceKey> instances = pointerAnalysis.getPointsToSet(key);
								for(InstanceKey ins: instances){
									TypeName name = ins.getConcreteType().getName();
									CGNode node = threadSigNodeMap.get(name);
//									HashSet<String> threadReceivers = new HashSet();
									//FIXME: BUG
									if(node==null){
										//TODO: find out which runnable object -- need data flow analysis
										int param = ((SSAAbstractInvokeInstruction)inst).getUse(0);
										if(sig.contains("java.util.concurrent") && sig.contains("execute")){
											param = ((SSAAbstractInvokeInstruction)inst).getUse(1);
										}
										node = handleRunnable(ins, param, n);
										if(node==null){
//											System.err.println("ERROR: starting new thread: "+ name);
											continue;
										}
										//threadreceiver?
									}else{//get threadReceivers
										//should be the hashcode of the instancekey
//										threadReceivers.add(String.valueOf(ins.hashCode()));//SHOULD BE "this thread/runnable object"
									}

//									System.out.println("Run : " + node.toString());

									boolean scheduled_this_thread = false;
									//duplicate graph node id
									if(stidpool.contains(node.getGraphNodeId())){
										if(threadNodes.contains(node) && scheduledAstNodes.contains(node)){
											//already scheduled to process twice, skip here.
											scheduled_this_thread = true;
										}else{
											scheduledAstNodes.add(node);
											AstCGNode2 threadNode = new AstCGNode2(imethod, node.getContext());
											int threadID = ++maxGraphNodeID;
											threadNode.setGraphNodeId(threadID);
											threadNode.setCGNode(node);
											threadNode.setIR(node.getIR());
											dupStartJoinTidMap.put(node.getGraphNodeId(), threadNode);
											node = threadNode;
										}
									}

									if(!scheduled_this_thread){
										threadNodes.add(node);
										int tid_child = node.getGraphNodeId();
										stidpool.add(tid_child);
										//add node to trace
										StartNode startNode = new StartNode(curTID, tid_child, n, node, sourceLineNum, file);//n
										curTrace.addS(startNode, inst, tid_child);
										shb.addEdge(startNode, node);
										//									inst_start_map.put(node, startNode);
										mapOfStartNode.put(tid_child, startNode);
//										mapOfStartNode.get(curTID).addChild(tid_child);
										StartNode pstartnode = mapOfStartNode.get(curTID);
										if(pstartnode == null){//?? should not be null, curtid is removed from map
											if(mainEntryNodes.contains(n)){
												pstartnode = new StartNode(-1, curTID, n, node, sourceLineNum, file);
												mapOfStartNode.put(curTID, pstartnode);
											}else{//thread/runnable
												pstartnode = new StartNode(curTID, tid_child, n, node,sourceLineNum, file);
												mapOfStartNode.put(tid_child, pstartnode);
											}
										}
										pstartnode.addChild(tid_child);

										//put to tid -> curreceivers map
//										tid2Receivers.put(node.getGraphNodeId(), threadReceivers);
										//TODO: check if it is in a simple loop
										boolean isInLoop = isInLoop(n,inst);

										if(isInLoop){
											AstCGNode2 node2 = new AstCGNode2(node.getMethod(),node.getContext());
											threadNodes.add(node2);
											//										newRunTargets.put(node2, node);
											int newID = ++maxGraphNodeID;
											astCGNode_ntid_map.put(node, newID);
											StartNode duplicate = new StartNode(curTID,newID, n, node2,sourceLineNum, file);
											curTrace.add2S(duplicate, inst, newID);//thread id +1
											shb.addEdge(duplicate, node2);
											mapOfStartNode.put(newID, duplicate);
											mapOfStartNode.get(curTID).addChild(newID);

											node2.setGraphNodeId(newID);
											node2.setIR(node.getIR());
											node2.setCGNode(node);
											n_loopn_map.put(node, node2);

											//need to change thread receiver id as well
//											Set<String> threadReceivers2 = new HashSet();
//											for(String id: threadReceivers){
//												threadReceivers2.add(id+"X");//"X" as the marker
//											}
											//put to tid -> curreceivers map
//											tid2Receivers.put(newID, threadReceivers2);
										}
									}
									//find loops in this method!!
									hasSyncBetween = true;
								}
							}
							else if(sig.equals("java.lang.Thread.join()V")){
								//Executors and ThreadPoolExecutor
								PointerKey key = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, ((SSAAbstractInvokeInstruction) inst).getReceiver());
								OrdinalSet<InstanceKey> instances = pointerAnalysis.getPointsToSet(key);
								for(InstanceKey ins: instances) {
									TypeName name = ins.getConcreteType().getName();
									CGNode node = threadSigNodeMap.get(name);
									//threadNodes.add(node);
//									HashSet<String> threadReceivers = new HashSet();
									if(node==null){//could be a runnable class
										int param = ((SSAAbstractInvokeInstruction)inst).getUse(0);
										node = handleRunnable(ins,param, n);
										if(node==null){
//											System.err.println("ERROR: joining parent thread: "+ name);
											continue;
										}
									}
//									System.out.println("Join : " + node.toString());

									//add node to trace
									int tid_child = node.getGraphNodeId();
									if(mapOfJoinNode.containsKey(tid_child)){
										CGNode threadNode = dupStartJoinTidMap.get(tid_child);
										tid_child = threadNode.getGraphNodeId();
										node = threadNode;
									}

									JoinNode jNode = new JoinNode(curTID, tid_child, n, node, sourceLineNum, file);
									curTrace.addJ(jNode, inst);
									shb.addBackEdge(node, jNode);
									mapOfJoinNode.put(tid_child, jNode);

									boolean isInLoop = isInLoop(n,inst);
									if(isInLoop){
										AstCGNode2 node2 = n_loopn_map.get(node);//should find created node2 during start
										//threadNodes.add(node2);
										if(node2 == null){
											node2 = dupStartJoinTidMap.get(tid_child);
											if(node2 == null){
												System.err.println("Null node obtain from n_loopn_map. ");
												continue;
											}
										}
										int newID = node2.getGraphNodeId();
										JoinNode jNode2 = new JoinNode(curTID, newID, n, node2, sourceLineNum, file);
										curTrace.addJ(jNode2, inst);//thread id +1
										shb.addBackEdge(node2, jNode2);
										mapOfJoinNode.put(newID, jNode2);
//										node2.setGraphNodeId(newID);
//										node2.setIR(node.getIR());
//										node2.setCGNode(node);
									}
								}
								hasSyncBetween = true;
							}else if(sig.equals("java.lang.Thread.<init>(Ljava/lang/Runnable;)V")){
								//for new Thread(new Runnable)
								int use0 = inst.getUse(0);
//								int use1 = inst.getUse(1);
								threadInits.put(use0, (SSAAbstractInvokeInstruction)inst);
							}else{
								//other method calls
								//save current curReceivers
//								Set<String> curReceivers_pre = curReceivers;
								//process NEW method call
								Set<CGNode> set = new HashSet<>();
								if(n instanceof AstCGNode2){
									set = callGraph.getPossibleTargets(((AstCGNode2)n).getCGNode(), csr);//newRunTargets.get(n)
								}else{
									set = callGraph.getPossibleTargets(n, csr);
								}
								for(CGNode node: set){
									if(AnalysisUtils.isApplicationClass(node.getMethod().getDeclaringClass())){
										//static method call
										if(node.getMethod().isStatic()){
											//omit the pointer-lock map
											//set current receivers to null
//											curReceivers = null;
											//use classname as lock obj
											String typeclassname =  n.getMethod().getDeclaringClass().getName().toString();
											String instSig =typeclassname.substring(1)+":"+sourceLineNum;
											String lock = node.getMethod().getDeclaringClass().getName().toString();
											//take out records
											HashSet<DLockNode> currentNodes = threadLockNodes.get(curTID);
											if(currentNodes==null){
												currentNodes = new HashSet<DLockNode>();
												threadLockNodes.put(curTID,currentNodes);
											}
											ArrayList<DLLockPair> dLLockPairs = threadDLLockPairs.get(curTID);
											if(dLLockPairs==null){
												dLLockPairs = new ArrayList<DLLockPair>();
												threadDLLockPairs.put(curTID, dLLockPairs);
											}
											DLockNode will = null;
											//if synchronized method, add lock/unlock
											if(node.getMethod().isSynchronized()){
												// for deadlock
												will = new DLockNode(curTID,instSig, sourceLineNum, null, null, n, inst, file);
												will.addLockSig(lock);
												for (DLockNode exist : currentNodes) {
													dLLockPairs.add(new DLLockPair(exist, will));
												}
												curTrace.add(will);
//												addToThreadSyncNodes(will);
												threadLockNodes.get(curTID).add(will);
												if(change){
													interested_l.add(will);
												}
											}
											MethodNode m = new MethodNode(n, node, curTID, sourceLineNum, file, (SSAAbstractInvokeInstruction) inst);
											curTrace.add(m);
											//
											//											idx.add(trace.indexOf(m));
											Trace subTrace0 = traverseNodePN(node);
//											subTrace0.includeTids(curTrace.getTraceTids());
//											if(thread){
												shb.includeTidForKidTraces(node,curTID);
//											}
											if(change){
												includeTraceToInterestL(node);
												includeTraceToInterestRW(node);
											}
											//											idx.add(trace.size());
											shb.addEdge(m, node);
											//
											if(node.getMethod().isSynchronized()){
												DUnlockNode unlock = new DUnlockNode(curTID, instSig, sourceLineNum, null, null, n, sourceLineNum);
												unlock.addLockSig(lock);
												//lock engine
												curTrace.addLockPair(new LockPair(will, unlock));
//												lockEngine.add(lock, curTID, new LockPair(will, unlock));
												//remove
												curTrace.add(unlock);
//												addToThreadSyncNodes(unlock);
												threadLockNodes.get(curTID).remove(will);
											}
										}else{
											//instance
											int objectValueNumber = inst.getUse(0);
											PointerKey objectPointer = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, objectValueNumber);
											OrdinalSet<InstanceKey> lockedObjects = pointerAnalysis.getPointsToSet(objectPointer);
											//											//mark pointer
											//											ArrayList<Integer> traceidx = pointer_traceidx_rwmap.get(objectPointer);
											//											if(traceidx == null){
											//												traceidx = new ArrayList<>();
											//												traceidx.add(trace.size());
											//												pointer_traceidx_lmap.put(objectPointer, traceidx);
											//											}else{
											//												pointer_traceidx_lmap.get(objectPointer).add(trace.size());
											//											}

											//											HashSet<DLockNode> wills = new HashSet<>();
											DLockNode will = null;
											if(lockedObjects.size()>0){//must be larger than 0
//												curReceivers = new HashSet<>();
												//take out records
												HashSet<DLockNode> currentNodes = threadLockNodes.get(curTID);
												if(currentNodes==null){
													currentNodes = new HashSet<DLockNode>();
													threadLockNodes.put(curTID,currentNodes);
												}
												ArrayList<DLLockPair> dLLockPairs = threadDLLockPairs.get(curTID);
												if(dLLockPairs==null){
													dLLockPairs = new ArrayList<DLLockPair>();
													threadDLLockPairs.put(curTID, dLLockPairs);
												}
												//start to record new locks
												if(node.getMethod().isSynchronized()){
													String typeclassname = n.getMethod().getDeclaringClass().getName().toString();
													String instSig = typeclassname.substring(1)+":"+sourceLineNum;
													will = new DLockNode(curTID,instSig, sourceLineNum, objectPointer, lockedObjects, n, inst, file);
													for (InstanceKey key : lockedObjects) {
														String lock = key.getConcreteType().getName()+"."+key.hashCode();
														//														SSAInstruction createinst = findInitialInst(n, instanceKey);//?
														will.addLockSig(lock);
													}
													// for deadlock
													for (DLockNode exist : currentNodes) {
														dLLockPairs.add(new DLLockPair(exist, will));
													}
													//													wills.add(will);
													//for race
													curTrace.add(will);
//													addToThreadSyncNodes(will);
													threadLockNodes.get(curTID).add(will);
													if(change){
														interested_l.add(will);
													}
													//for pointer-lock map
													HashSet<SyncNode> ls = pointer_lmap.get(objectPointer);
													if(ls == null){
														ls = new HashSet<>();
														ls.add(will);
														pointer_lmap.put(objectPointer, ls);
													}else{
														ls.add(will);
													}
												}

												//configuration
//												int K_obj_sensitive = 0;//0 means non-object sensitive
//												for (InstanceKey instanceKey : lockedObjects) {
//													//add receiver
//													if(K_obj_sensitive>0&&curReceivers_pre!=null){
//														for(String instance_pre: curReceivers_pre){
//															String temmStr = instance_pre;
//															String curObject = String.valueOf(instanceKey.hashCode());
//															//find the last Y or not
//															int indexY = instance_pre.lastIndexOf("Y");
//															if(indexY>-1)
//																temmStr = instance_pre.substring(indexY);
//															//object sensitivity is memory-demanding -- limit it to 2
//															//count number of Ys
//															int Kount = temmStr.length() - temmStr.replaceAll("Y", "").length();
//															if(Kount<=K_obj_sensitive
//																	&&!temmStr.equals(curObject))//-- limit it to 2
//																curReceivers.add(instance_pre+"Y"+curObject);
//														}
//													}else
//														curReceivers.add(String.valueOf(instanceKey.hashCode()));
//
//													//													if(node.getMethod().isSynchronized()){
//													//														isSync = true;
//													//														String typeclassname =  node.getMethod().getDeclaringClass().getName().toString();
//													//														String instSig = typeclassname.substring(1)+":"+sourceLineNum;
//													//														String lock = instanceKey.getConcreteType().getName()+"."+instanceKey.hashCode();
//													//														SSAInstruction createinst = findInitialInst(n, instanceKey);//?
//													//														// for deadlock
//													//														DLockNode will = new DLockNode(curTID,instSig, lock, sourceLineNum, createinst);
//													//														for (DLockNode exist : currentNodes) {
//													//															dLLockPairs.add(new DLLockPair(exist, will));
//													//														}
//													//														wills.add(will);
//													//														//for race
//													//														curTrace.add(will);
//													//													}
//												}
												//												addToThreadSyncNodes(wills);
												//												threadLockNodes.get(curTID).addAll(wills);
											}
											//
											MethodNode m = new MethodNode(n, node, curTID, sourceLineNum, file, (SSAAbstractInvokeInstruction) inst);
											curTrace.add(m);
											//
											//											idx.add(trace.indexOf(m));
											Trace subTrace1 = traverseNodePN(node);
//											subTrace1.includeTids(curTrace.getTraceTids());
//											if(thread){
												shb.includeTidForKidTraces(node,curTID);
//											}
											if(change){
												if(lockedObjects.size() > 0)
													includeTraceToInterestL(node);
												includeTraceToInterestRW(node);
											}
											//											idx.add(trace.size());
											shb.addEdge(m, node);
											//
											if(lockedObjects.size() > 0){
												if(node.getMethod().isSynchronized()){
													//												//mark pointer
													//												ArrayList<Integer> traceidx2 = pointer_traceidx_rwmap.get(objectPointer);
													//												if(traceidx2 == null){
													//													traceidx2 = new ArrayList<>();
													//													traceidx2.add(trace.size());
													//													pointer_traceidx_lmap.put(objectPointer, traceidx2);
													//												}else{
													//													pointer_traceidx_lmap.get(objectPointer).add(trace.size());
													//												}
													String typeclassname =  node.getMethod().getDeclaringClass().getName().toString();
													String instSig =typeclassname.substring(1)+":"+sourceLineNum;
													DUnlockNode unlock = new DUnlockNode(curTID, instSig, sourceLineNum, objectPointer, lockedObjects, n, sourceLineNum);
//													if(lockedObjects.size() == 1){//must alias
														LockPair lockPair = new LockPair(will, unlock);
														curTrace.addLockPair(lockPair);
//													}
													for (InstanceKey instanceKey : lockedObjects) {
														String lock = instanceKey.getConcreteType().getName()+"."+instanceKey.hashCode();
														unlock.addLockSig(lock);
														//													lockEngine.add(lock, curTID, lockPair);
													}
													//lock engine
													//												for (Iterator iterator = wills.iterator(); iterator.hasNext();) {
													//													DLockNode dLockNode = (DLockNode) iterator.next();
													//													lockEngine.add(lock, curTID, new LockPair(dLockNode, unlock));
													//												}
													//for race
													curTrace.add(unlock);
													//												addToThreadSyncNodes(unlock);
													// for deadlock
													threadLockNodes.get(curTID).remove(will);
													//for pointer-lock map
													//												HashSet<String> ls = pointer_lmap.get(objectPointer);
													//												if(ls == null){
													//													ls = unlock.getLockSig();
													//												}
													//												//mark pointer
													//												pointer_traceidx_lmap.get(objectPointer).add(trace.size() - 1);
												}
											}
										}
									}else{
										//array/list/map write invoke methods previous have been ignored.
									}
								}
//								curReceivers = curReceivers_pre;
							}
						}
					}
				}
				else if(inst instanceof SSAMonitorInstruction)
				{
					//lock node: GID, TID, LockID
					SSAMonitorInstruction monitorInstruction = ((SSAMonitorInstruction) inst);
					int lockValueNumber = monitorInstruction.getRef();

					PointerKey lockPointer = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, lockValueNumber);
					OrdinalSet<InstanceKey> lockObjects = pointerAnalysis.getPointsToSet(lockPointer);
					//lets use must alias analysis for race?????
					//					if(lockObjects.size()==1){
					//						for (InstanceKey instanceKey : lockObjects) {
					//							String lock = instanceKey.getConcreteType().getName()+"."+instanceKey.hashCode();
					//							if(((SSAMonitorInstruction) inst).isMonitorEnter()){
					//								trace.add(new LockNode(getIncrementGID(),curTID,lock));
					//							}else{
					//								trace.add(new UnlockNode(getIncrementGID(),curTID,lock));
					//							}
					//						}
					//					}
					//					//mark pointer
					//					ArrayList<Integer> traceidx2 = pointer_traceidx_rwmap.get(lockPointer);
					//					if(traceidx2 == null){
					//						traceidx2 = new ArrayList<>();
					//						traceidx2.add(trace.size());
					//						pointer_traceidx_lmap.put(lockPointer, traceidx2);
					//					}else{
					//						pointer_traceidx_lmap.get(lockPointer).add(trace.size());
					//					}
					// for deadlock
					String typeclassname =  n.getMethod().getDeclaringClass().getName().toString();
					String instSig =typeclassname.substring(1)+":"+sourceLineNum;
					DLockNode will = null;
					DUnlockNode next = null;
					//take our record
					HashSet<DLockNode> currentNodes = threadLockNodes.get(curTID);
					if(currentNodes==null){
						currentNodes = new HashSet<DLockNode>();
						threadLockNodes.put(curTID,currentNodes);
					}
					ArrayList<DLLockPair> dlpairs = threadDLLockPairs.get(curTID);
					if(dlpairs==null){
						dlpairs = new ArrayList<DLLockPair>();
						threadDLLockPairs.put(curTID, dlpairs);
					}
					for (InstanceKey instanceKey : lockObjects) {
						String lock = instanceKey.getConcreteType().getName()+"."+instanceKey.hashCode();
						//						SSAInstruction createinst = findInitialInst(n, instanceKey);
						if(((SSAMonitorInstruction) inst).isMonitorEnter()){
							will = new DLockNode(curTID, instSig, sourceLineNum, lockPointer, lockObjects, n, inst, file);
							will.addLockSig(lock);
						}else{
							next = new DUnlockNode(curTID, instSig, sourceLineNum, lockPointer, lockObjects, n, sourceLineNum);
							next.addLockSig(lock);
							for (Iterator iterator = currentNodes.iterator(); iterator.hasNext();) {
								DLockNode dLockNode = (DLockNode) iterator.next();
								if (dLockNode.getInstSig().equals(instSig)) {//maybe compare pointer?
									will = dLockNode;
									break;
								}
							}
						}
					}
					//					//mark pointer
					//					pointer_traceidx_lmap.get(lockPointer).add(trace.size() - 1);

					if(((SSAMonitorInstruction) inst).isMonitorEnter()){
						if(will != null){
							for (DLockNode exist : currentNodes) {
								dlpairs.add(new DLLockPair(exist, will));
							}
							curTrace.add(will);
							threadLockNodes.get(curTID).add(will);
							if(change){
								interested_l.add(will);
							}
							//for pointer-lock map
							HashSet<SyncNode> ls = pointer_lmap.get(lockPointer);
							if(ls == null){
								ls = new HashSet<>();
								ls.add(will);
								pointer_lmap.put(lockPointer, ls);
							}else{
								ls.add(will);
							}
						}
					}else {//monitor exit
						if(will != null){
							curTrace.add(next);
//							if(lockObjects.size() == 1){
								curTrace.addLockPair(new LockPair(will, next));
//							}
							threadLockNodes.get(curTID).remove(will);
						}
//						addToThreadSyncNodes(next);
						//for pointer-lock map
						//						HashSet<String> ls = pointer_lmap.get(lockPointer);
						//						if(ls == null){
						//							ls = next.getLockSig();
						//						}
					}
					hasSyncBetween = true;
				}
			}
		}

		//mark end of method_idx/gid
		//		method_gid_map.get(n).add(curTID);
		//		//mark method_idx_map
		//		idx.add(trace.size() - 1);
		//		HashSet<ArrayList<Integer>> idxOfNode = method_idx_map.get(n);
		//		if(idxOfNode == null){
		//			idxOfNode = new HashSet<ArrayList<Integer>>();
		//			idxOfNode.add(idx);
		//			method_idx_map.put(n, idxOfNode);
		//		}else{
		//			method_idx_map.get(n).add(idx);
		//		}
		//		System.out.println("TRACE IDX OF METHOD " + n.getMethod() + ": " + method_idx_map.get(n));

//		//add back to shb
//		shb.addTrace(n, curTrace, curTID);
//		System.out.println("  => trace is " + curTrace.getContent().toString());
		return curTrace;
	}





	private Trace traverseNodePN2(Trace curTrace, CGNode n) {
		//for recording locks; total twice
		if(alreadyProcessedNodes.contains(n)){
			//allow multiple entries of a method if there exist sync in between
			if(!hasSyncBetween)
				return curTrace;
			else
				hasSyncBetween = false;
		}
		alreadyProcessedNodes.add(n);

		//let curtrace edges include new tids
		boolean includeCurtid = !shb.includeTidForKidTraces(n, curTID);
//		HashSet<SHBEdge> edges = shb.getOutGoingEdgesOf(n);
//		for (SHBEdge edge : edges) {
//			edge.includeTid(curTID);
//		}

		//start traverse inst
		SSACFG cfg = n.getIR().getControlFlowGraph();
		//		System.out.println(ssacfg.toString());
		HashSet<SSAInstruction> catchinsts = InstInsideCatchBlock(cfg);

		SSAInstruction[] insts = n.getIR().getInstructions();
		for(int i=0; i<insts.length; i++){
			SSAInstruction inst = insts[i];

			if(inst!=null){
				if(catchinsts.contains(inst)){
					continue;
				}
				IMethod method = n.getMethod() ;
				int sourceLineNum = 0;
				IFile file = null;
				try{//get source code line number of this inst
					if(n.getIR().getMethod() instanceof IBytecodeMethod){
						int bytecodeindex = ((IBytecodeMethod) n.getIR().getMethod()).getBytecodeIndex(inst.iindex);
						sourceLineNum = (int)n.getIR().getMethod().getLineNumber(bytecodeindex);
					}else{
						SourcePosition position = n.getMethod().getSourcePosition(inst.iindex);
						sourceLineNum = position.getFirstLine();//.getLastLine();
						if(position instanceof JdtPosition){
							file = ((JdtPosition) position).getEclipseFile();
						}
					}
					//					System.out.println(inst.iindex);
				}catch(Exception e){
					e.printStackTrace();
				}

				//System.out.println(inst.toString());
				if (inst instanceof SSAAbstractInvokeInstruction){

					CallSiteReference csr = ((SSAAbstractInvokeInstruction)inst).getCallSite();
					MethodReference mr = csr.getDeclaredTarget();
					//if (AnalysisUtils.implementsRunnableInterface(iclass) || AnalysisUtils.extendsThreadClass(iclass))
					{
						com.ibm.wala.classLoader.IMethod imethod = callGraph.getClassHierarchy().resolveMethod(mr);
						if(imethod!=null){
							String sig = imethod.getSignature();
							//System.out.println("Invoke Inst: "+sig);
							if(sig.equals("java.lang.Thread.start()V")){
								PointerKey key = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, ((SSAAbstractInvokeInstruction) inst).getReceiver());
								OrdinalSet<InstanceKey> instances = pointerAnalysis.getPointsToSet(key);
								for(InstanceKey ins: instances){
									TypeName name = ins.getConcreteType().getName();
									CGNode node = threadSigNodeMap.get(name);
//									HashSet<String> threadReceivers = new HashSet();
									//FIXME: BUG
									if(node==null){
										//TODO: find out which runnable object -- need data flow analysis
										int param = ((SSAAbstractInvokeInstruction)inst).getUse(0);
										if(sig.contains("java.util.concurrent") && sig.contains("execute")){
											param = ((SSAAbstractInvokeInstruction)inst).getUse(1);
										}
										node = handleRunnable(ins, param, n);
										if(node==null){
//											System.err.println("ERROR: starting new thread: "+ name);
											continue;
										}
										//threadreceiver?
									}else{//get threadReceivers
										//should be the hashcode of the instancekey
//										threadReceivers.add(String.valueOf(ins.hashCode()));//SHOULD BE "this thread/runnable object"
									}

//									System.out.println("Run : " + node.toString());

									boolean scheduled_this_thread = false;
									//duplicate graph node id
									if(stidpool.contains(node.getGraphNodeId())){
										if(threadNodes.contains(node) && scheduledAstNodes.contains(node)){
											//already scheduled to process twice, skip here.
											scheduled_this_thread = true;
										}else{
											scheduledAstNodes.add(node);
											AstCGNode2 threadNode = new AstCGNode2(imethod, node.getContext());
											int threadID = ++maxGraphNodeID;
											threadNode.setGraphNodeId(threadID);
											threadNode.setCGNode(node);
											threadNode.setIR(node.getIR());
											dupStartJoinTidMap.put(node.getGraphNodeId(), threadNode);
											node = threadNode;
										}
									}

									if(!scheduled_this_thread){
										threadNodes.add(node);
										int tid_child = node.getGraphNodeId();
										stidpool.add(tid_child);
										//add node to trace
										StartNode startNode = new StartNode(curTID, tid_child, n, node, sourceLineNum, file);//n
										curTrace.add2S(startNode, inst, tid_child);
										shb.addEdge(startNode, node);
										//									inst_start_map.put(node, startNode);
										mapOfStartNode.put(tid_child, startNode);
										StartNode pstartnode = mapOfStartNode.get(curTID);
										if(pstartnode == null){
											if(mainEntryNodes.contains(n)){
												pstartnode = new StartNode(-1, curTID, n, node, sourceLineNum, file);
												mapOfStartNode.put(curTID, pstartnode);
											}else{//thread/runnable
												pstartnode = new StartNode(curTID, tid_child, n, node,sourceLineNum, file);
												mapOfStartNode.put(tid_child, pstartnode);
											}
										}
										pstartnode.addChild(tid_child);

										//put to tid -> curreceivers map
//										tid2Receivers.put(node.getGraphNodeId(), threadReceivers);
										//TODO: check if it is in a simple loop
										boolean isInLoop = isInLoop(n,inst);

										if(isInLoop){
											AstCGNode2 node2 = new AstCGNode2(node.getMethod(),node.getContext());
											threadNodes.add(node2);
											//										newRunTargets.put(node2, node);
											n_loopn_map.put(node, node2);
											int newID = ++maxGraphNodeID;
											astCGNode_ntid_map.put(node, newID);
											StartNode duplicate = new StartNode(curTID,newID, n, node2, sourceLineNum, file);
											curTrace.add2S(duplicate, inst, newID);//thread id +1
											shb.addEdge(duplicate, node2);
											mapOfStartNode.put(newID, duplicate);
											mapOfStartNode.get(curTID).addChild(newID);

											node2.setGraphNodeId(newID);
											node2.setIR(node.getIR());
											node2.setCGNode(node);

											//need to change thread receiver id as well
//											Set<String> threadReceivers2 = new HashSet();
//											for(String id: threadReceivers){
//												threadReceivers2.add(id+"X");//"X" as the marker
//											}
											//put to tid -> curreceivers map
//											tid2Receivers.put(newID, threadReceivers2);
										}
									}
									//find loops in this method!!
									hasSyncBetween = true;
								}
							}
							else if(sig.equals("java.lang.Thread.join()V")){
								PointerKey key = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, ((SSAAbstractInvokeInstruction) inst).getReceiver());
								OrdinalSet<InstanceKey> instances = pointerAnalysis.getPointsToSet(key);
								for(InstanceKey ins: instances){
									TypeName name = ins.getConcreteType().getName();
									CGNode node = threadSigNodeMap.get(name);
									//threadNodes.add(node);
//									HashSet<String> threadReceivers = new HashSet();
									if(node==null){//could be a runnable class
										int param = ((SSAAbstractInvokeInstruction)inst).getUse(0);
										node = handleRunnable(ins, param, n);
										if(node==null){
//											System.err.println("ERROR: joining parent thread: "+ name);
											continue;
										}
									}
									//add node to trace
									int tid_child = node.getGraphNodeId();
									if(mapOfJoinNode.containsKey(tid_child)){
										CGNode threadNode = dupStartJoinTidMap.get(tid_child);
										tid_child = threadNode.getGraphNodeId();
										node = threadNode;
									}

									JoinNode jNode = new JoinNode(curTID,tid_child, n, node, sourceLineNum, file);
//									curTrace.addSJLater(jNode);
									curTrace.add2J(jNode, inst, tid_child);
									shb.addBackEdge(node, jNode);
									mapOfJoinNode.put(tid_child, jNode);

									boolean isInLoop = isInLoop(n,inst);
									if(isInLoop){
										AstCGNode2 node2 = n_loopn_map.get(node);
										if(node2 == null){
											node2 = dupStartJoinTidMap.get(tid_child);
											if(node2 == null){
												System.err.println("Null node obtain from n_loopn_map. ");
												continue;
											}
										}
										//threadNodes.add(node2);
										int newID = node2.getGraphNodeId();
										JoinNode jNode2 = new JoinNode(curTID,newID, n, node2, sourceLineNum, file);
//										curTrace.addSJLater(jNode2);//thread id +1
										curTrace.add2J(jNode2, inst, newID);
										shb.addBackEdge(node2, jNode2);
										mapOfJoinNode.put(newID, jNode2);
//										node2.setGraphNodeId(newID);
//										node2.setIR(node.getIR());
//										node2.setCGNode(node);
									}
								}
								hasSyncBetween = true;
							}else if(sig.equals("java.lang.Thread.<init>(Ljava/lang/Runnable;)V")){
								//for new Thread(new Runnable)
								int use0 = inst.getUse(0);
//								int use1 = inst.getUse(1);
								threadInits.put(use0, (SSAAbstractInvokeInstruction)inst);
							}else{
								//other method calls
								//save current curReceivers
//								Set<String> curReceivers_pre = curReceivers;
								//process NEW method call
								Set<CGNode> set = new HashSet<>();
								if(n instanceof AstCGNode2){
									set = callGraph.getPossibleTargets(((AstCGNode2)n).getCGNode(), csr);//newRunTargets.get(n)
								}else{
									set = callGraph.getPossibleTargets(n, csr);
								}
								for(CGNode node: set){
									if(AnalysisUtils.isApplicationClass(node.getMethod().getDeclaringClass())
											//&&node.getMethod().getName().toString().equals(csr.getDeclaredTarget().getName().toString())
											){
										//static method call
										if(node.getMethod().isStatic()){
											//omit the pointer-lock map
											//set current receivers to null
//											curReceivers = null;
											//use classname as lock obj
											String typeclassname =  n.getMethod().getDeclaringClass().getName().toString();
											String instSig =typeclassname.substring(1)+":"+sourceLineNum;
											String lock = node.getMethod().getDeclaringClass().getName().toString();
											//take out records
											HashSet<DLockNode> currentNodes = threadLockNodes.get(curTID);
											if(currentNodes==null){
												currentNodes = new HashSet<DLockNode>();
												threadLockNodes.put(curTID,currentNodes);
											}
											ArrayList<DLLockPair> dLLockPairs = threadDLLockPairs.get(curTID);
											if(dLLockPairs==null){
												dLLockPairs = new ArrayList<DLLockPair>();
												threadDLLockPairs.put(curTID, dLLockPairs);
											}
											DLockNode will = null;
											//if synchronized method, add lock/unlock
											if(node.getMethod().isSynchronized()){
												// for deadlock
												will = new DLockNode(curTID, instSig, sourceLineNum, null, null, n, inst, file);
												will.addLockSig(lock);
												for (DLockNode exist : currentNodes) {
													dLLockPairs.add(new DLLockPair(exist, will));
												}
//												curTrace.add(will);
//												addToThreadSyncNodes(will);
												threadLockNodes.get(curTID).add(will);
											}
//											MethodNode m = new MethodNode(n, node, curTID);
//											curTrace.add(m);
											//
											//													idx.add(trace.indexOf(m));
											Trace subTrace0 = traverseNodePN(node);
//											subTrace0.includeTids(curTrace.getTraceTids());
											if(!includeCurtid){
												shb.includeTidForKidTraces(node, curTID);
											}
											//													idx.add(trace.size());
//											shb.addEdge(m, node);
											//
											if(node.getMethod().isSynchronized()){
//												DUnlockNode unlock = new DUnlockNode(curTID, instSig, sourceLineNum, null, null, n);
//												unlock.addLockSig(lock);
												//lock engine
//												lockEngine.add(lock, curTID, new LockPair(will, unlock));
												//remove
//												curTrace.add(unlock);
//												addToThreadSyncNodes(unlock);
												threadLockNodes.get(curTID).remove(will);
											}
										}else{
											//instance
											int objectValueNumber = inst.getUse(0);
											PointerKey objectPointer = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, objectValueNumber);
											OrdinalSet<InstanceKey> lockedObjects = pointerAnalysis.getPointsToSet(objectPointer);

											DLockNode will = null;
											if(lockedObjects.size()>0){//must be larger than 0
//												curReceivers = new HashSet<>();
												//take out records
												HashSet<DLockNode> currentNodes = threadLockNodes.get(curTID);
												if(currentNodes==null){
													currentNodes = new HashSet<DLockNode>();
													threadLockNodes.put(curTID,currentNodes);
												}
												ArrayList<DLLockPair> dLLockPairs = threadDLLockPairs.get(curTID);
												if(dLLockPairs==null){
													dLLockPairs = new ArrayList<DLLockPair>();
													threadDLLockPairs.put(curTID, dLLockPairs);
												}
												//start to record new locks
												if(node.getMethod().isSynchronized()){
													String typeclassname = n.getMethod().getDeclaringClass().getName().toString();
													String instSig = typeclassname.substring(1)+":"+sourceLineNum;
													will = new DLockNode(curTID,instSig, sourceLineNum, objectPointer, lockedObjects, n, inst, file);
													for (InstanceKey key : lockedObjects) {
														String lock = key.getConcreteType().getName()+"."+key.hashCode();
														will.addLockSig(lock);
													}
													// for deadlock
													for (DLockNode exist : currentNodes) {
														dLLockPairs.add(new DLLockPair(exist, will));
													}
													//															wills.add(will);
													//for race
//													curTrace.add(will);
//													addToThreadSyncNodes(will);
													threadLockNodes.get(curTID).add(will);
													//for pointer-lock map
													HashSet<SyncNode> ls = pointer_lmap.get(objectPointer);
													if(ls == null){
														ls = new HashSet<>();
														ls.add(will);
														pointer_lmap.put(objectPointer, ls);
													}else{
														ls.add(will);
													}
												}

												//configuration
//												int K_obj_sensitive = 0;//0 means non-object sensitive
//												for (InstanceKey instanceKey : lockedObjects) {
//													//add receiver
//													if(K_obj_sensitive>0&&curReceivers_pre!=null){
//														for(String instance_pre: curReceivers_pre){
//															String temmStr = instance_pre;
//															String curObject = String.valueOf(instanceKey.hashCode());
//															//find the last Y or not
//															int indexY = instance_pre.lastIndexOf("Y");
//															if(indexY>-1)
//																temmStr = instance_pre.substring(indexY);
//															//object sensitivity is memory-demanding -- limit it to 2
//															//count number of Ys
//															int Kount = temmStr.length() - temmStr.replaceAll("Y", "").length();
//															if(Kount<=K_obj_sensitive
//																	&&!temmStr.equals(curObject))//-- limit it to 2
//																curReceivers.add(instance_pre+"Y"+curObject);
//														}
//													}else
//														curReceivers.add(String.valueOf(instanceKey.hashCode()));

													//															if(node.getMethod().isSynchronized()){
													//																isSync = true;
													//																String typeclassname =  node.getMethod().getDeclaringClass().getName().toString();
													//																String instSig = typeclassname.substring(1)+":"+sourceLineNum;
													//																String lock = instanceKey.getConcreteType().getName()+"."+instanceKey.hashCode();
													//																SSAInstruction createinst = findInitialInst(n, instanceKey);//?
													//																// for deadlock
													//																DLockNode will = new DLockNode(curTID,instSig, lock, sourceLineNum, createinst);
													//																for (DLockNode exist : currentNodes) {
													//																	dLLockPairs.add(new DLLockPair(exist, will));
													//																}
													//																wills.add(will);
													//																//for race
													//																curTrace.add(will);
													//															}
//												}
												//														addToThreadSyncNodes(wills);
												//														threadLockNodes.get(curTID).addAll(wills);
											}
											//													//mark pointer
											//													pointer_traceidx_lmap.get(objectPointer).add(trace.size() -1);
											//
//											MethodNode m = new MethodNode(node, curTID);
//											curTrace.add(m);
											//
											//													idx.add(trace.indexOf(m));
											Trace subTrace1 = traverseNodePN(node);
//											subTrace1.includeTids(curTrace.getTraceTids());
											if(!includeCurtid){
												shb.includeTidForKidTraces(node, curTID);
											}
											//													idx.add(trace.size());
//											shb.addEdge(curTrace.getLast(), node);
											//
											if(lockedObjects.size() > 0){
												if(node.getMethod().isSynchronized()){
													//														//mark pointer
													//														ArrayList<Integer> traceidx2 = pointer_traceidx_rwmap.get(objectPointer);
													//														if(traceidx2 == null){
													//															traceidx2 = new ArrayList<>();
													//															traceidx2.add(trace.size());
													//															pointer_traceidx_lmap.put(objectPointer, traceidx2);
													//														}else{
													//															pointer_traceidx_lmap.get(objectPointer).add(trace.size());
													//														}
													String typeclassname =  node.getMethod().getDeclaringClass().getName().toString();
													String instSig =typeclassname.substring(1)+":"+sourceLineNum;
													//												DUnlockNode unlock = new DUnlockNode(curTID, instSig, sourceLineNum, objectPointer, lockedObjects, n);
													//												LockPair lockPair = new LockPair(will, unlock);
													//												for (InstanceKey instanceKey : lockedObjects) {
													//													String lock = instanceKey.getConcreteType().getName()+"."+instanceKey.hashCode();
													//													unlock.addLockSig(lock);
													//													lockEngine.add(lock, curTID, lockPair);
													//												}
													//lock engine
													//														for (Iterator iterator = wills.iterator(); iterator.hasNext();) {
													//															DLockNode dLockNode = (DLockNode) iterator.next();
													//															lockEngine.add(lock, curTID, new LockPair(dLockNode, unlock));
													//														}
													//for race
													//												curTrace.add(unlock);
													//												addToThreadSyncNodes(unlock);
													// for deadlock
													threadLockNodes.get(curTID).remove(will);
													//for pointer-lock map
													//														HashSet<String> ls = pointer_lmap.get(objectPointer);
													//														if(ls == null){
													//															ls = unlock.getLockSig();
													//														}
													//														//mark pointer
													//														pointer_traceidx_lmap.get(objectPointer).add(trace.size() - 1);
												}
											}
										}
									}
								}
//								curReceivers = curReceivers_pre;
							}
						}
					}
				}
				else if(inst instanceof SSAMonitorInstruction)
				{
					//lock node: GID, TID, LockID
					SSAMonitorInstruction monitorInstruction = ((SSAMonitorInstruction) inst);
					int lockValueNumber = monitorInstruction.getRef();

					PointerKey lockPointer =pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, lockValueNumber);
					OrdinalSet<InstanceKey> lockObjects = pointerAnalysis.getPointsToSet(lockPointer);
					//lets use must alias analysis for race?????
					//					if(lockObjects.size()==1){
					//						for (InstanceKey instanceKey : lockObjects) {
					//							String lock = instanceKey.getConcreteType().getName()+"."+instanceKey.hashCode();
					//							if(((SSAMonitorInstruction) inst).isMonitorEnter()){
					//								trace.add(new LockNode(getIncrementGID(),curTID,lock));
					//							}else{
					//								trace.add(new UnlockNode(getIncrementGID(),curTID,lock));
					//							}
					//						}
					//					}
					//							//mark pointer
					//							ArrayList<Integer> traceidx2 = pointer_traceidx_rwmap.get(lockPointer);
					//							if(traceidx2 == null){
					//								traceidx2 = new ArrayList<>();
					//								traceidx2.add(trace.size());
					//								pointer_traceidx_lmap.put(lockPointer, traceidx2);
					//							}else{
					//								pointer_traceidx_lmap.get(lockPointer).add(trace.size());
					//							}
					// for deadlock
					String typeclassname =  n.getMethod().getDeclaringClass().getName().toString();
					String instSig =typeclassname.substring(1)+":"+sourceLineNum;
					DLockNode will = null;
					DUnlockNode next = null;
					//take our record
					HashSet<DLockNode> currentNodes = threadLockNodes.get(curTID);
					if(currentNodes==null){
						currentNodes = new HashSet<DLockNode>();
						threadLockNodes.put(curTID,currentNodes);
					}
					ArrayList<DLLockPair> dlpairs = threadDLLockPairs.get(curTID);
					if(dlpairs==null){
						dlpairs = new ArrayList<DLLockPair>();
						threadDLLockPairs.put(curTID, dlpairs);
					}
					for (InstanceKey instanceKey : lockObjects) {
						String lock = instanceKey.getConcreteType().getName()+"."+instanceKey.hashCode();
						//								SSAInstruction createinst = findInitialInst(n, instanceKey);
						if(((SSAMonitorInstruction) inst).isMonitorEnter()){
							will = new DLockNode(curTID, instSig, sourceLineNum, lockPointer, lockObjects, n, inst, file);
							will.addLockSig(lock);
						}else{
							next = new DUnlockNode(curTID, instSig, sourceLineNum, lockPointer, lockObjects, n, sourceLineNum);
							next.addLockSig(lock);
							for (Iterator iterator = currentNodes.iterator(); iterator.hasNext();) {
								DLockNode dLockNode = (DLockNode) iterator.next();
								if (dLockNode.getInstSig().equals(instSig)) {//maybe compare pointer?
									will = dLockNode;
									break;
								}
							}
						}
					}
					//							//mark pointer
					//							pointer_traceidx_lmap.get(lockPointer).add(trace.size() - 1);

					if(((SSAMonitorInstruction) inst).isMonitorEnter()){
						if(will != null){
							for (DLockNode exist : currentNodes) {
								dlpairs.add(new DLLockPair(exist, will));
							}
							//						curTrace.add(will);
							threadLockNodes.get(curTID).add(will);
							//						addToThreadSyncNodes(will);
							//for pointer-lock map
							HashSet<SyncNode> ls = pointer_lmap.get(lockPointer);
							if(ls == null){
								ls = new HashSet<>();
								ls.add(will);
								pointer_lmap.put(lockPointer, ls);
							}else{
								ls.add(will);
							}
						}
					}else {//monitor exit
//						curTrace.add(next);
//						addToThreadSyncNodes(next);
						if(will != null){
							threadLockNodes.get(curTID).remove(will);
						}
						//for pointer-lock map
						//								HashSet<String> ls = pointer_lmap.get(lockPointer);
						//								if(ls == null){
						//									ls = next.getLockSig();
						//								}
					}
					hasSyncBetween = true;
				}
			}

		}
		return curTrace;
	}


	private static HashMap<CGNode,Collection<Loop>> nodeLoops = new HashMap<CGNode,Collection<Loop>>();

	private boolean isInLoop(CGNode n, SSAInstruction inst) {
		Collection<Loop> loops = nodeLoops.get(n);
		if(loops==null){
			IR ir = n.getIR();
			if(ir!=null)
				loops = findLoops(ir);
			else
				return false;
		}

		for(Loop loop: loops){
			List insts = loop.getLoopInstructions();
			if(insts.contains(inst))
				return true;
		}
		return false;
	}

	private static Collection<Loop> findLoops(IR ir){
		SSACFG cfg =  ir.getControlFlowGraph();

		BasicBlock root = cfg.entry();
		NumberedDominators dominator = new NumberedDominators(cfg,root);

		Iterator<ISSABasicBlock> bbs = cfg.iterator();
		HashSet domSet = new HashSet();
		HashMap<BasicBlock, List<BasicBlock>> loops = new HashMap<BasicBlock, List<BasicBlock>>();

		while(bbs.hasNext()){
			ISSABasicBlock bb = bbs.next();
			Iterator<ISSABasicBlock> succs = cfg.getSuccNodes(bb);
			Iterator<ISSABasicBlock> dominators = dominator.dominators(bb);


			while(dominators.hasNext())
				domSet.add(dominators.next());

			ArrayList<ISSABasicBlock> headers=null;

			while(succs.hasNext()){
				ISSABasicBlock succ = succs.next();

				if (domSet.contains(succ)){
					//header succeeds and dominates s, we have a loop
					if(headers==null)
						headers = new ArrayList<ISSABasicBlock>();
					headers.add(succ);
				}
			}
			domSet.clear();
			if(headers!=null){
				Iterator<ISSABasicBlock> headersIt = headers.iterator();
				while (headersIt.hasNext()){
					BasicBlock header = (BasicBlock) headersIt.next();
					List<BasicBlock> loopBody = getLoopBodyFor(cfg, header, bb);

					if (loops.containsKey(header)){
						// merge bodies
						List<BasicBlock> lb1 = loops.get(header);
						loops.put(header, union(lb1, loopBody));
					}else {
						loops.put(header, loopBody);
					}
				}
			}
		}

		Collection<Loop> result = new HashSet<Loop>();
		for (Map.Entry<BasicBlock,List<BasicBlock>> entry : loops.entrySet()) {
			result.add(new Loop(entry.getKey(),entry.getValue(),cfg));
		}
		return result;
	}

	private static List<BasicBlock> getLoopBodyFor(SSACFG cfg, BasicBlock header, ISSABasicBlock node){

		ArrayList<BasicBlock> loopBody = new ArrayList<BasicBlock>();
		Stack<ISSABasicBlock> stack = new Stack<ISSABasicBlock>();

		loopBody.add(header);
		stack.push(node);

		while (!stack.isEmpty()){
			BasicBlock next = (BasicBlock)stack.pop();
			if (!loopBody.contains(next)){
				// add next to loop body
				loopBody.add(0, next);
				// put all preds of next on stack
				Iterator<ISSABasicBlock> it = cfg.getPredNodes(next);
				while (it.hasNext()){
					stack.push(it.next());
				}
			}
		}

		assert (node==header && loopBody.size()==1) || loopBody.get(loopBody.size()-2)==node;
		assert loopBody.get(loopBody.size()-1)==header;

		return loopBody;
	}

	private static List<BasicBlock> union(List<BasicBlock> l1, List<BasicBlock> l2){
		Iterator<BasicBlock> it = l2.iterator();
		while (it.hasNext()){
			BasicBlock next = it.next();
			if (!l1.contains(next)){
				l1.add(next);
			}
		}
		return l1;
	}


	private String handleArrayTypes(SSAArrayReferenceInstruction inst, CGNode anode, OrdinalSet<InstanceKey> instances) {
		int def = inst.getArrayRef();
		String returnValue = "";
		for (InstanceKey instKey : instances) {//size? mutiple => assignment between arrays? TODO:
			if(instKey instanceof AllocationSiteInNode){
				SSAInstruction creation = anode.getDU().getDef(def);
				CGNode who = anode;
				if(creation == null){
					CGNode n = ((AllocationSiteInNode) instKey).getNode();
					creation = n.getDU().getDef(def);
					who = n;
					//if creation still == null; this def represents a local variable or assignment between local and global variables;
					// =>> only use the instance hashcode to check race
					// the comment code below can get the name of the local variable.
					if(creation == null){
						IMethod method = anode.getIR().getControlFlowGraph().getMethod();
						if(method instanceof ConcreteJavaMethod){
							ConcreteJavaMethod jMethod = (ConcreteJavaMethod) method;
							DebuggingInformation info = jMethod.debugInfo();
							String[][] names = info.getSourceNamesForValues();
							String[] name = names[def];
							return "local:" + Arrays.toString(name).replace("[", "").replace("]", "");
						}
					}
				}
				returnValue = classifyStmtTypes(creation, who);
			}
		}
		return returnValue;
	}

	private String classifyStmtTypes(SSAInstruction creation, CGNode who){
		if(creation instanceof SSAFieldAccessInstruction){
			String classname = ((SSAFieldAccessInstruction) creation).getDeclaredField().getDeclaringClass().getName().toString();
			String fieldname = ((SSAFieldAccessInstruction) creation).getDeclaredField().getName().toString();
			return classname.substring(1)+"."+fieldname;
		}else if(creation instanceof SSANewInstruction){
			String classname = ((SSANewInstruction) creation).getNewSite().getDeclaredType().getName().getClassName().toString();
			return classname;
		}else if(creation instanceof SSAArrayReferenceInstruction ){
			SSAArrayReferenceInstruction arrayRefInst = (SSAArrayReferenceInstruction) creation;
			int def0 = arrayRefInst.getArrayRef();
			PointerKey key0 = pointerAnalysis.getHeapModel().getPointerKeyForLocal(who, def0);
			OrdinalSet<InstanceKey> instances0 = pointerAnalysis.getPointsToSet(key0);
			return handleArrayTypes(arrayRefInst, who, instances0);
		}else if(creation instanceof SSAAbstractInvokeInstruction){
			String classname = ((SSAAbstractInvokeInstruction) creation).getCallSite().getDeclaredTarget().getReturnType().getName().getClassName().toString();
			return classname;
		}else if(creation instanceof SSACheckCastInstruction){
			SSACheckCastInstruction cast = ((SSACheckCastInstruction) creation);
			int def0 = cast.getVal();
			SSAInstruction creation0 = who.getDU().getDef(def0);
			return classifyStmtTypes(creation0, who);
		}else{
//			System.out.println(creation);
			return "";
		}
	}

	/**
	 * only works for simple runnable constructor.
	 * @param instKey
	 * @param param
	 * @param invokeCGNode
	 * @return
	 */
	private CGNode handleRunnable(InstanceKey instKey, int param, CGNode invokeCGNode) {
		if(instKey instanceof AllocationSiteInNode){//IR get NEW!!!
			CGNode keyCGNode = ((AllocationSiteInNode) instKey).getNode();
			CGNode node = null;//return
			TypeName name = null;
			SSAInstruction creation = invokeCGNode.getDU().getDef(param);
			CGNode useNode = invokeCGNode;
			if(creation == null){
				creation = keyCGNode.getDU().getDef(param);
				useNode = keyCGNode;
			}

			if(creation instanceof SSAGetInstruction){
				name = ((SSAGetInstruction) creation).getDeclaredField().getDeclaringClass().getName();
				node = threadSigNodeMap.get(name);
				if(node!=null)
					return  node;
			}else if(creation instanceof SSAPutInstruction){
				name = ((SSAPutInstruction) creation).getDeclaredField().getDeclaringClass().getName();
				node = threadSigNodeMap.get(name);
				if(node!=null)
					return  node;
			}else if(creation instanceof SSANewInstruction){
				name = ((SSANewInstruction) creation).getConcreteType().getName();
				if(name.toString().contains("Ljava/lang/Thread")){
					name = useNode.getMethod().getDeclaringClass().getName();
				}
				node = threadSigNodeMap.get(name);
				if(node!=null)
					return  node;
			}else if(creation instanceof SSAAbstractInvokeInstruction){
				name = ((SSAAbstractInvokeInstruction) creation).getCallSite().getDeclaredTarget().getDeclaringClass().getName();
				node = threadSigNodeMap.get(name);
				if (node != null) {
					return node;
				}else {
					name = useNode.getMethod().getDeclaringClass().getName();
					node = threadSigNodeMap.get(name);
					if(node!=null)
						return  node;
					else{
						//special case
						Iterator<TypeName> iterator = threadSigNodeMap.keySet().iterator();
						while(iterator.hasNext()){
							TypeName key = iterator.next();
							if(key.toString().contains(name.toString())){
								return threadSigNodeMap.get(key);
							}
						}
					}
				}
			}
			//example: Critical: Thread t = new Thread(Class implements Runnable)
			//example: raytracer:
			//find out the initial of this Ljava/lang/Thread
			SSAAbstractInvokeInstruction initial = threadInits.get(param);
			if(initial != null){
				param = initial.getUse(1);
				return handleRunnable(instKey, param, useNode);
			}else{
				//because: assignments + ssa; array references
//				int new_param = findDefsInDataFlowFor(useNode, param, creation.iindex);
//				if(new_param != -1){
//					node = handleRunnable(instKey, new_param, useNode);
//					if(node != null)
//						return node;
//				}
//				if(creation instanceof SSAArrayLoadInstruction){
//					new_param = ((SSAArrayLoadInstruction)creation).getArrayRef();
//				}
//				while (node == null){
//					new_param = findDefsInDataFlowFor(useNode, new_param, creation.iindex);
//					node = handleRunnable(instKey, new_param, useNode);
//				}
//				return node;
				return null;
			}
		}
		return null;
	}


	//	private void logArrayAccess(SSAInstruction inst, String sig,
	//			int sourceLineNum, String instSig, Trace curTrace) {
	//		if(inst instanceof SSAArrayLoadInstruction){//read
	//			HashMap<Integer, Integer> threadRInst = variableReadMap.get(sig);
	//			if(threadRInst==null){
	//				threadRInst = new HashMap<Integer, Integer>();
	//				threadRInst.put(curTID, 1);
	//				variableReadMap.put(sig, threadRInst);
	//			}else{
	//				int counter = threadRInst.get(curTID);
	//				variableReadMap.get(sig).put(curTID, counter++);
	//			}
	//			//add node to trace
	//			curTrace.add(new ReadNode(curTID,sig,instSig,sourceLineNum));
	//		}else {//write
	//			HashMap<Integer, Integer> threadRInst = variableWriteMap.get(sig);
	//			if(threadRInst==null){
	//				threadRInst = new HashMap<Integer, Integer>();
	//				threadRInst.put(curTID, 1);
	//				variableWriteMap.put(sig, threadRInst);
	//			}else{
	//				int counter = threadRInst.get(curTID);
	//				variableWriteMap.get(sig).put(curTID, counter++);
	//			}
	//
	//			//add node to trace
	//			curTrace.add(new WriteNode(curTID,sig,instSig,sourceLineNum));
	//		}
	//	}


	private int findDefsInDataFlowFor(CGNode node, int param, int idx) {
		int def = -1;
		Iterator<SSAInstruction> defInsts = node.getDU().getUses(param);
		while(defInsts.hasNext()){
			SSAInstruction defInst = defInsts.next();
			int didx = defInst.iindex;
			if(didx < idx){
				int temp = -1;
				if(defInst instanceof SSANewInstruction){
					SSANewInstruction tnew = (SSANewInstruction) defInst;
					temp = tnew.getDef();
				}else if(defInst instanceof SSAArrayStoreInstruction){
					SSAArrayStoreInstruction astore = (SSAArrayStoreInstruction) defInst;
					temp = astore.getValue();
				}else if(defInst instanceof SSAPutInstruction){
					SSAPutInstruction fput = (SSAPutInstruction) defInst;
					temp = fput.getVal();
				}
				if(temp != param && temp != -1){
					def = temp;
				}
			}
		}
		return def;
	}



	private void logArrayAccess3(SSAInstruction inst, int sourceLineNum, String instSig, Trace curTrace, CGNode n,
			PointerKey key, OrdinalSet<InstanceKey> instances, IFile file, String field) {
		String sig = "array.";
//		if(!field.contains("local:")){
//			sig = sig + field;
//		}
		if(inst instanceof SSAArrayLoadInstruction){//read
			ReadNode readNode = new ReadNode(curTID,instSig,sourceLineNum,key, sig, n, inst, file);
			for (InstanceKey instanceKey : instances) {
				String sig2 = sig + instanceKey.hashCode();
				readNode.addObjSig(sig2);
				curTrace.addRsigMapping(sig2, readNode);
				if(change){
					interested_rw.add(sig2);
				}
			}
//			if(field.contains("local:")){
				readNode.setLocalSig(field);
//			}
			//add node to trace
			curTrace.add(readNode);
			//pointer rw map
			HashSet<MemNode> rwlist = pointer_rwmap.get(key);
			if(rwlist == null){
				rwlist = new HashSet<>();
				rwlist.add(readNode);
				pointer_rwmap.put(key, rwlist);
			}else{
				rwlist.add(readNode);
			}
		}else {//write
			WriteNode writeNode = new WriteNode(curTID,instSig,sourceLineNum, key, sig, n, inst, file);
			for (InstanceKey instanceKey : instances) {
				String sig2 = sig+ instanceKey.hashCode();
				writeNode.addObjSig(sig2);
				curTrace.addWsigMapping(sig2, writeNode);
				if(change){
					interested_rw.add(sig2);
				}
			}
//			if(field.contains("local:")){
				writeNode.setLocalSig(field);
//			}
			//add node to trace
			curTrace.add(writeNode);
			//pointer rw map
			HashSet<MemNode> rwlist = pointer_rwmap.get(key);
			if(rwlist == null){
				rwlist = new HashSet<>();
				rwlist.add(writeNode);
				pointer_rwmap.put(key, rwlist);
			}else{
				rwlist.add(writeNode);
			}
		}
	}


//	private void logFieldAccess(SSAInstruction inst, String sig, int sourceLineNum, String instSig, Trace curTrace) {
//		//System.out.println("field access: "+sig);
//		if(inst instanceof SSAGetInstruction){//read
//			//			HashMap<Integer, String> threadRInst = variableReadMap.get(sig);
//			//			if(threadRInst==null){
//			//				threadRInst = new HashMap<Integer, String>();
//			//				variableReadMap.put(sig, threadRInst);
//			//			}
//			//			threadRInst.put(curTID,instSig);
//			//add node to trace
//			curTrace.add(new ReadNode(curTID,sig,instSig,sourceLineNum));
//		}else{//write
//			//			HashMap<Integer, String> threadWInst = variableWriteMap.get(sig);
//			//			if(threadWInst==null){
//			//				threadWInst = new HashMap<Integer, String>();
//			//				variableWriteMap.put(sig, threadWInst);
//			//			}
//			//			threadWInst.put(curTID, instSig);
//			//add node to trace
//			curTrace.add(new WriteNode(curTID,sig,instSig,sourceLineNum));
//		}
//	}

	/**
	 * flag for incremental changes
	 */
	public boolean change = false;
	public void setChange(boolean p){
		this.change = p;
	}


	private void logFieldAccess3(SSAInstruction inst, int sourceLineNum, String instSig, Trace curTrace, CGNode n,
			PointerKey key, OrdinalSet<InstanceKey> instances, String sig, IFile file) {
		boolean exclude = false;
		if(excludedSigForRace.contains(sig)){
			exclude = true;
		}
		HashSet<String> sigs = new HashSet<>();
		if(inst instanceof SSAGetInstruction){//read
			ReadNode readNode;
			if(key != null){
				for (InstanceKey instanceKey : instances) {
					String sig2 = sig+"."+String.valueOf(instanceKey.hashCode());
//					if(excludedSigForRace.contains(sig)){
//						return;
//					}
					sigs.add(sig2);
				}
				readNode = new ReadNode(curTID,instSig,sourceLineNum,key, sig, n, inst, file);
				readNode.setObjSigs(sigs);
				//excluded sigs
				if(exclude){
					HashSet<ReadNode> exReads = excludedReadSigMapping.get(sig);
					if(exReads == null){
						exReads = new HashSet<ReadNode>();
						excludedReadSigMapping.put(sig, exReads);
					}
					exReads.add(readNode);
					return;
				}
				if(change){
					interested_rw.addAll(sigs);
				}
				for (String sig2 : sigs) {
					curTrace.addRsigMapping(sig2, readNode);
				}
				//add node to trace
				curTrace.add(readNode);
				//pointer rw map
				HashSet<MemNode> rwlist = pointer_rwmap.get(key);
				if(rwlist == null){
					rwlist = new HashSet<>();
					rwlist.add(readNode);
					pointer_rwmap.put(key, rwlist);
				}else{
					rwlist.add(readNode);
				}
			}else{//static
				readNode = new ReadNode(curTID,instSig,sourceLineNum,key, sig, n, inst,file);
				readNode.addObjSig(sig);
				//excluded sigs
				if(exclude){
					HashSet<ReadNode> exReads = excludedReadSigMapping.get(sig);
					if(exReads == null){
						exReads = new HashSet<ReadNode>();
						excludedReadSigMapping.put(sig, exReads);
					}
					exReads.add(readNode);
					return;
				}
				//add node to trace
				curTrace.add(readNode);
				curTrace.addRsigMapping(sig, readNode);
				if(change){
					interested_rw.add(sig);
				}
			}
		}else{//write
			WriteNode writeNode;
			if(key != null){
				for (InstanceKey instanceKey : instances) {
					String sig2 = sig+"."+String.valueOf(instanceKey.hashCode());
//					if(excludedSigForRace.contains(sig)){
//						return;
//					}
					sigs.add(sig2);
				}
				writeNode = new WriteNode(curTID,instSig,sourceLineNum,key, sig, n, inst, file);
				writeNode.setObjSigs(sigs);;
				//excluded sigs
				if(exclude){
					HashSet<WriteNode> exWrites = excludedWriteSigMapping.get(sig);
					if(exWrites == null){
						exWrites = new HashSet<WriteNode>();
						excludedWriteSigMapping.put(sig, exWrites);
					}
					exWrites.add(writeNode);
					return;
				}
				if(change){
					interested_rw.addAll(sigs);
				}
				for (String sig2 : sigs) {
					curTrace.addWsigMapping(sig2, writeNode);
				}
				//add node to trace
				curTrace.add(writeNode);
				//pointer rw map
				HashSet<MemNode> rwlist = pointer_rwmap.get(key);
				if(rwlist == null){
					rwlist = new HashSet<>();
					rwlist.add(writeNode);
					pointer_rwmap.put(key, rwlist);
				}else{
					rwlist.add(writeNode);
				}
			}else{//static
				writeNode = new WriteNode(curTID,instSig,sourceLineNum,key, sig, n, inst, file);
				writeNode.addObjSig(sig);
				//excluded sigs
				if(exclude){
					HashSet<WriteNode> exWrites = excludedWriteSigMapping.get(sig);
					if(exWrites == null){
						exWrites = new HashSet<WriteNode>();
						excludedWriteSigMapping.put(sig, exWrites);
					}
					exWrites.add(writeNode);
					return;
				}
				//add node to trace
				curTrace.add(writeNode);
				curTrace.addWsigMapping(sig, writeNode);
				if(change){
					interested_rw.add(sig);
				}
			}
		}
	}


//	private MemNode logArrayAccess2(SSAInstruction inst, String sig,
//			int sourceLineNum, String instSig, Trace curTrace) {
//		if(inst instanceof SSAArrayLoadInstruction){//read
//			//			HashMap<Integer, Integer> threadRInst = variableReadMap.get(sig);
//			//			if(threadRInst==null){
//			//				threadRInst = new HashMap<Integer, Integer>();
//			//				threadRInst.put(curTID, 1);
//			//				variableReadMap.put(sig, threadRInst);
//			//			}else{
//			//				int counter = threadRInst.get(curTID);
//			//				variableReadMap.get(sig).put(curTID, counter++);
//			//			}
//			//add node to trace
//			return new ReadNode(curTID,sig,instSig,sourceLineNum);
//		}else {//write
//			//			HashMap<Integer, Integer> threadRInst = variableWriteMap.get(sig);
//			//			if(threadRInst==null){
//			//				threadRInst = new HashMap<Integer, Integer>();
//			//				threadRInst.put(curTID, 1);
//			//				variableWriteMap.put(sig, threadRInst);
//			//			}else{
//			//				int counter = threadRInst.get(curTID);
//			//				variableWriteMap.get(sig).put(curTID, counter++);
//			//			}
//			//add node to trace
//			return new WriteNode(curTID,sig,instSig,sourceLineNum);
//		}
//	}


//	private MemNode logFieldAccess2(SSAInstruction inst, String sig, int sourceLineNum, String instSig) {
//		//System.out.println("field access: "+sig);
//		if(inst instanceof SSAGetInstruction){//read
//			//			HashMap<Integer, Integer> threadRInst = variableReadMap.get(sig);
//			//			if(threadRInst==null){
//			//				threadRInst = new HashMap<Integer, Integer>();
//			//				threadRInst.put(curTID, 1);
//			//				variableReadMap.put(sig, threadRInst);
//			//			}else{
//			//				int counter = threadRInst.get(curTID);
//			//				variableReadMap.get(sig).put(curTID, counter++);
//			//			}
//
//			//add node to trace
//			return new ReadNode(curTID,sig,instSig,sourceLineNum);
//		}else{//write
//			//			HashMap<Integer, Integer> threadRInst = variableWriteMap.get(sig);
//			//			if(threadRInst==null){
//			//				threadRInst = new HashMap<Integer, Integer>();
//			//				threadRInst.put(curTID, 1);
//			//				variableWriteMap.put(sig, threadRInst);
//			//			}else{
//			//				int counter = threadRInst.get(curTID);
//			//				variableWriteMap.get(sig).put(curTID, counter++);
//			//			}
//
//			//add node to trace
//			return new WriteNode(curTID,sig,instSig,sourceLineNum);
//		}
//	}



	public synchronized void addSharedVars(HashSet<String> sf) {
		sharedFields.addAll(sf);
	}

	public synchronized void addSigReadNodes(HashMap<String, HashSet<ReadNode>> sigReadNodes2) {
		for(String key : sigReadNodes2.keySet()){
			HashSet<ReadNode> readNodes = sigReadNodes2.get(key);
			if(sigReadNodes.containsKey(key)){
				sigReadNodes.get(key).addAll(readNodes);
			}else{
				sigReadNodes.put(key, readNodes);
			}
		}
	}

	public synchronized void addSigWriteNodes(HashMap<String, HashSet<WriteNode>> sigWriteNodes2) {
		for(String key : sigWriteNodes2.keySet()){
			HashSet<WriteNode> writeNodes = sigWriteNodes2.get(key);
			if(sigWriteNodes.containsKey(key)){
				sigWriteNodes.get(key).addAll(writeNodes);
			}else{
				sigWriteNodes.put(key, writeNodes);
			}
		}
	}

	public synchronized void removeBugs(HashSet<TIDERace> removes) {
		this.removedbugs.addAll(removes);
	}

	/**
	 * for processIncreRecheckCommonLocks
	 */
	public HashSet<TIDERace> recheckRaces = new HashSet<>();
	public synchronized void addRecheckBugs(MemNode wnode, MemNode xnode) {
		TIDERace recheck = new TIDERace(wnode, xnode);
		if(!recheckRaces.contains(recheck)){
			this.recheckRaces.add(recheck);
		}
	}


	public synchronized void addBugsBack(HashSet<ITIDEBug> bs) {
		Iterator<ITIDEBug> iterator = bs.iterator();
		while(iterator.hasNext()){
			ITIDEBug _bug = iterator.next();
			if (_bug instanceof TIDEDeadlock) {
				TIDEDeadlock dl = (TIDEDeadlock) _bug;
				boolean iscontain = false;
				Iterator<ITIDEBug> iter = bugs.iterator();
				while(iter.hasNext()) {
					ITIDEBug exist = (ITIDEBug) iter.next();
					if(exist instanceof TIDEDeadlock){
						TIDEDeadlock bug = (TIDEDeadlock) exist;
						if(bug.lp1.lock1.getLine() == dl.lp1.lock1.getLine()
								&& bug.lp1.lock2.getLine() == dl.lp1.lock2.getLine()
								&& bug.lp2.lock1.getLine() == dl.lp2.lock1.getLine()
								&& bug.lp2.lock2.getLine() == dl.lp2.lock2.getLine()){
							iscontain = true;
						}else if(bug.lp1.lock1.getLine() == dl.lp2.lock1.getLine()
								&& bug.lp1.lock2.getLine() == dl.lp2.lock2.getLine()
								&& bug.lp2.lock1.getLine() == dl.lp1.lock1.getLine()
								&& bug.lp2.lock2.getLine() == dl.lp1.lock2.getLine()){
							iscontain = true;
						}
					}
				}
				if(!iscontain){
//					bugs.add(_bug);
					addedbugs.add(_bug);
				}
			}else if(_bug instanceof TIDERace){//race bug:
				boolean iscontain = false;
				Iterator<ITIDEBug> iter = bugs.iterator();
				while(iter.hasNext()) {
					ITIDEBug exist = (ITIDEBug) iter.next();
					if(exist instanceof TIDERace){
						TIDERace race = (TIDERace) exist;
						if(_bug.equals(race)){
							iscontain = true;
						}
					}
				}
				if(!iscontain){
//					bugs.add(_bug);
					addedbugs.add(_bug);
				}
			}
		}
	}



	/*
	 * incremental detection part
	 */

	HashMap<CGNode, Boolean> hasLocks = new HashMap<>();
	HashMap<CGNode, Boolean> hasThreads = new HashMap<>();
	//0 -> not change; 1 -> new added; -1 -> new del; 2 -> objchange
	public HashSet<ITIDEBug> updateEngine(HashSet<CGNode> changedNodes, HashSet<CGNode> changedModifiers,
			boolean ptachanges, PrintStream ps) {
		long start_time = System.currentTimeMillis();
		alreadyProcessedNodes.clear();
		twiceProcessedNodes.clear();
		thirdProcessedNodes.clear();
		scheduledAstNodes.clear();
		hasLocks.clear();
		hasThreads.clear();
		interested_l.clear();
		interested_rw.clear();
		removed_l.clear();
		removed_rw.clear();
		addedbugs.clear();
		removedbugs.clear();
//		System.out.println("+++++++++++ changed nodes +++++++++++" + changedNodes.size());
//		for (CGNode cgNode : changedNodes) {
//			System.out.println(cgNode.getMethod().toString());
//		}
		for (CGNode node : changedModifiers) {
			HashSet<SHBEdge> incomings = shb.getIncomingEdgesOf(node);
			if(incomings == null)
				continue;
			for (SHBEdge edge : incomings) {
				MethodNode methodcall = (MethodNode) edge.getSource();
				CGNode parent = methodcall.getBelonging();
				changedNodes.add(parent);
			}
		}
		HashMap<CGNode, HashSet<CGNode>> mayIsolates = new HashMap<>();
		//start to modify engine
		for (CGNode node : changedNodes) {
			int id = node.getGraphNodeId();
			Trace old_trace = shb.getTrace(node);
			if(old_trace == null){//shoud not be??
				continue;
			}
//			System.out.println("  => old trace is " + old_trace.print());

			//remove newruntarget mapofstartnode inst_start_map threadnodes
			if(old_trace.ifHasStart() || old_trace.ifHasJoin()){
				removeKidThreadRelations(old_trace);
				hasThreads.put(node, true);
			}
			//compute currentLockednodes, and remove
			computeAndRemoveCurrentLockedNodes(node, old_trace);
			//also remove pointer_lmap and pointer_rwmap
			removeRelatedFromPointerMaps(old_trace);
			//remove from other maps
			removeRelatedFromRWMaps(node);
			//for update shb later
			HashSet<CGNode> mays= shb.getOutGoingSinksOf(node);
			mayIsolates.put(node, mays);
			//remove related lock info/ rw mapping
			shb.clearOutgoingEdgesFor(node);
			//keep tids, incoming edges
			old_trace.clearContent();
			//replace with new trace and new relation
			traverseNodePNInc(node, old_trace);
//			System.out.println("  => new trace is " + old_trace.print());
		}
//		System.out.println("shb after change ====================================");
//		shb.print();

//		System.out.println("mapofstartnodes after change ====================================");
//		for (Integer tid : mapOfStartNode.keySet()) {
//			System.out.println(mapOfStartNode.get(tid).toString());
//		}
//		System.out.println("mapOfJoinNode =========================");
//		for (Integer tid : mapOfJoinNode.keySet()) {
//			System.out.println(mapOfJoinNode.get(tid).toString());
//		}
//		System.out.println();

		//update shb, remove those nodes have no incoming/outgoing edges
//		for (CGNode node : mayIsolates.keySet()) {
//			HashSet<CGNode> mays = mayIsolates.get(node);
//			HashSet<CGNode> removes = shb.removeNotUsedTrace(removed_rw, node, mays);//?
//			for(CGNode removed : removes){
//				System.out.println(" ===== removed node : " + removed.getMethod().toString());
//				Trace rTrace = shb.getTrace(removed);
//				if(rTrace.ifHasStart() || rTrace.ifHasJoin()){
//					removeKidThreadRelations(rTrace);
//					hasThreads.put(removed, true);
//				}
//				//remove from maps
//				computeAndRemoveCurrentLockedNodes(node, rTrace);
//				removeRelatedFromRWMaps(removed);
//				//remove from pointer map
//				removeRelatedFromPointerMaps(shb.getTrace(removed));
//				//next round of update shb??
//				shb.delTrace(removed);
//			}
//			removeBugsRelatedToInterests(removes, null);
//		}

		if(ptachanges){
			updatePTA(changedNodes);
		}

		//redo detection
		detectBothBugsAgain(changedNodes, changedModifiers, start_time, ps);
		return bugs;
	}

	//for expr
	public HashSet<ITIDEBug> updateEngine2(HashSet<CGNode> changedNodes, boolean ptachanges, SSAInstruction removeInst, PrintStream ps) {
		long start_time = System.currentTimeMillis();
		alreadyProcessedNodes.clear();
		twiceProcessedNodes.clear();
		thirdProcessedNodes.clear();
		scheduledAstNodes.clear();
		hasLocks.clear();
		hasThreads.clear();
		interested_l.clear();
		interested_rw.clear();
		removed_l.clear();
		removed_rw.clear();
		addedbugs.clear();
		removedbugs.clear();
		this.removeInst = removeInst;
//		System.out.println("removed inst is : " + removeInst.toString());
//		System.out.println("+++++++++++ changed nodes +++++++++++");
//		for (CGNode cgNode : changedNodes) {
//			System.out.println(cgNode.getMethod().toString());
//		}
		//start to modify engine
		for (CGNode node : changedNodes) {
		  int id = node.getGraphNodeId();
		  Trace old_trace = shb.getTrace(node);
		  if(old_trace == null){//shoud not be??
		    continue;
		  }
//					System.out.println("  => old trace is " + old_trace.print());

		  //remove newruntarget mapofstartnode inst_start_map threadnodes
		  if(old_trace.ifHasStart() || old_trace.ifHasJoin()){
		    removeKidThreadRelations(old_trace);
		    hasThreads.put(node, true);
		  }
		  //compute currentLockednodes, and remove
		  computeAndRemoveCurrentLockedNodes(node, old_trace);
		  //also remove pointer_lmap and pointer_rwmap
		  removeRelatedFromPointerMaps(old_trace);
		  //remove from other maps
		  removeRelatedFromRWMaps(node);
		  //for update shb later
		  HashSet<CGNode> mays= shb.getOutGoingSinksOf(node);
		  //remove related lock info/ rw mapping
		  shb.clearOutgoingEdgesFor(node);
		  //keep tids, incoming edges
		  old_trace.clearContent();
		  //replace with new trace and new relation
		  traverseNodePNInc(node, old_trace);
//					System.out.println("  => new trace is " + old_trace.print());
		}
//				System.out.println("shb after change ====================================");
//				shb.print();

//		System.out.println("mapofstartnodes after change ====================================");
//		for (Integer tid : mapOfStartNode.keySet()) {
//		  System.out.println(mapOfStartNode.get(tid).toString());
//		}
//		System.out.println("mapOfJoinNode =========================");
//		for (Integer tid : mapOfJoinNode.keySet()) {
//		  System.out.println(mapOfJoinNode.get(tid).toString());
//		}
//		System.out.println();

		if(ptachanges){
			updatePTA(changedNodes);
		}

		//redo detection
		detectBothBugsAgain(changedNodes, new HashSet<>(),start_time, ps);
		removeInst = null;
		return bugs;
	}



//	HashSet<TIDERace> ignoredRaces = new HashSet<>();

	// ignore method/cgnode
	public HashSet<ITIDEBug> ignoreCGNodes(HashSet<CGNode> ignoreNodes) {
		hasLocks.clear();
		hasThreads.clear();
		interested_l.clear();
		interested_rw.clear();
		removed_l.clear();
		removed_rw.clear();
		addedbugs.clear();
		removedbugs.clear();
		System.out.println("+++++++++++ Ignore nodes +++++++++++");
		for (CGNode cgNode : ignoreNodes) {
			System.out.println(cgNode.getMethod().toString());
		}
		HashSet<CGNode> relates = new HashSet<>();
		relates.addAll(ignoreNodes);
		//update shb and related objects in engine
		for(CGNode ignore : ignoreNodes){
			//remove trace for ignore
			int id = ignore.getGraphNodeId();
			Trace iTrace = shb.getTrace(ignore);
			if(iTrace == null){
				continue;//shoud not be??
			}
//			System.out.println("  => Ignore trace is " + iTrace.print());
//			System.out.println("    tids: " + iTrace.getTraceTids());
//			System.out.println("    kids: " + iTrace.getTraceKids());

			//remove mapofjoinnode mapofstartnode threadnodes
			if(iTrace.ifHasStart() || iTrace.ifHasJoin()){
				removeKidThreadRelations(iTrace);
				hasThreads.put(ignore, true);
			}
			if(iTrace.doesIncludeTid(id)){
				//this is the thread node trace
				removeThisThreadRelations(iTrace);
//				hasThreads.put(ignore, true);
			}
			//compute currentLockednodes, and remove
			computeAndRemoveCurrentLockedNodes(ignore, iTrace);
			//normal method node => remove related r/w/l
			//remove related lock info/ rw mapping
			//also remove pointer_lmap and pointer_rwmap
			removeRelatedFromPointerMaps(iTrace);
			//remove from other maps
			removeRelatedFromRWMaps(ignore);
			HashSet<CGNode> mayIsolates = shb.getOutGoingSinksOf(ignore);
			shb.delTrace(ignore);

			//update shb, remove those nodes have no incoming/outgoing edges
//			CGNode removed = shb.removeNotUsedTrace(removed_rw);//?
			HashSet<CGNode> removes = shb.removeNotUsedTrace(removed_rw, ignore, mayIsolates);//?
			excludedMethodIsolatedCGNodes.put(ignore, removes);
			for(CGNode removed : removes){
				System.out.println(" ===== removed node : " + removed.getMethod().toString());
				Trace rTrace = shb.getTrace(removed);
				if(rTrace.ifHasStart() || rTrace.ifHasJoin()){
					removeKidThreadRelations(rTrace);
					hasThreads.put(removed, true);
				}
				//remove from maps
				computeAndRemoveCurrentLockedNodes(ignore, rTrace);
				removeRelatedFromRWMaps(removed);
				//remove from pointer map
				removeRelatedFromPointerMaps(shb.getTrace(removed));
				//next round of update shb??
				shb.delTrace(removed);
			}
			relates.addAll(removes);
		}
		//remove related bugs
		removeBugsRelatedToInterests(relates, null);
		return removedbugs;
	}


	public HashSet<ITIDEBug> considerCGNodes(HashSet<CGNode> considerNodes) {
 		alreadyProcessedNodes.clear();
		twiceProcessedNodes.clear();
		thirdProcessedNodes.clear();
		scheduledAstNodes.clear();

		hasLocks.clear();
		hasThreads.clear();
		interested_l.clear();
		interested_rw.clear();
		removed_l.clear();
		removed_rw.clear();
		addedbugs.clear();
		removedbugs.clear();
		System.out.println("+++++++++++ Consider nodes +++++++++++");
		for (CGNode cgNode : considerNodes) {
			System.out.println(cgNode.getMethod().toString());
		}
		for (CGNode consider : considerNodes) {
			int id = consider.getGraphNodeId();
			Trace cTrace = shb.getTrace(consider);
			if(cTrace == null){
				//find curTid
				HashSet<Integer> mayTids = new HashSet<>();//potential curtids for consider
				HashSet<Trace> callerTraces = new HashSet<>();//for reconnect
				Iterator<CGNode> iter = callGraph.getPredNodes(consider);
				while(iter.hasNext()){
					CGNode caller = iter.next();
					Trace callerTrace = shb.getTrace(caller);
					if(callerTrace != null){
						ArrayList<Integer> tids = callerTrace.getTraceTids();
						mayTids.addAll(tids);
						callerTraces.add(callerTrace);
					}
				}
				//recreate trace
				for (Integer may : mayTids) {
					curTID = may;
					cTrace = traverseNodePN(consider);
				}
				singleOrganizeRWMaps(cTrace);
				//reconnect incoming edges
				for (Trace callerTrace : callerTraces) {
					shb.reconnectIncomingSHBEdgesFor(callerTrace, cTrace, consider);
				}
				//the isolated nodes for previous ignores should be traversed during the process
//				System.out.println("  => consider trace is " + cTrace.print());
			}else{
				System.err.println("THIS NODE HAS NOT BEEN IGNORED. " + consider.getMethod().getName());
			}
		}
		//check
		System.out.println("mapofstartnodes after change ====================================");
		for (Integer tid : mapOfStartNode.keySet()) {
			System.out.println(mapOfStartNode.get(tid).toString());
		}
		System.out.println("mapOfJoinNode =========================");
		for (Integer tid : mapOfJoinNode.keySet()) {
			System.out.println(mapOfJoinNode.get(tid).toString());
		}
		System.out.println();

		//check isolates
		for (CGNode consider : considerNodes) {
			HashSet<CGNode> isolates = excludedMethodIsolatedCGNodes.get(consider);
			for (CGNode isolate : isolates) {
				Trace iTrace = shb.getTrace(isolate);
				if(iTrace == null)
					System.err.println("Previous isolated cgnode has not been traversed: " + isolate.getMethod().getName());
			}
		}
		//redo checking
		detectBothBugsAgain(considerNodes, null, System.currentTimeMillis(), null);

		return addedbugs;
	}




	//not used
	public HashSet<ITIDEBug> updateEngineCompare(HashSet<CGNode> changedNodes, boolean ptachanges) {
		long start_time = System.currentTimeMillis();
		alreadyProcessedNodes.clear();
		twiceProcessedNodes.clear();
		thirdProcessedNodes.clear();
		scheduledAstNodes.clear();
		hasLocks.clear();
		hasThreads.clear();
		interested_l.clear();
		interested_rw.clear();
		removed_l.clear();
		removed_rw.clear();
		addedbugs.clear();
		removedbugs.clear();
//		System.out.println("+++++++++++ changed nodes +++++++++++");
//		for (CGNode cgNode : changedNodes) {
//			System.out.println(cgNode.getMethod().toString());
//		}
		for (CGNode node : changedNodes) {
			Trace old_trace = shb.getTrace(node);
			if(old_trace == null){//shoud not be??
				continue;
			}
			System.out.println("  => old trace is " + old_trace.getContent().toString());
			//compute currentLockednodes, and remove
			computeAndRemoveCurrentLockedNodes(node, old_trace);
//			Trace new_trace = traverseNodePNIncCompare(node, old_trace);
			System.out.println("  => new trace is " + old_trace.getContent().toString());

//			if(compareNewOldTraces(old_trace, new_trace)){
//				continue;//trace not changed
//			}
			//remove related lock info/ rw mapping
			shb.clearOutgoingEdgesFor(node);
			//remove newruntarget mapofstartnode inst_start_map threadnodes
			if(old_trace.ifHasStart() || old_trace.ifHasJoin()){
				removeKidThreadRelations(old_trace);
				hasThreads.put(node, true);
			}
			//also remove pointer_lmap and pointer_rwmap
			removeRelatedFromPointerMaps(old_trace);
			old_trace.clearContent();//keep tids, incoming edges

			//replace with new trace and new relation and add back to shb
			shb.replaceTrace(node, old_trace);
			//organize wtid num map/rwsigmap
			singleOrganizeRWMaps(old_trace);
		}
//		System.out.println("shb after change ====================================");
//		shb.print();
//		System.out.println("mapofstartnodes after change ====================================");
//		System.out.println(mapOfStartNode.toString());

		//update shb
		CGNode removed = shb.removeNotUsedTrace(removed_rw);
		if(removed != null){
			System.out.println(" ===== removed node : " + removed.getMethod().toString());
			//remove from maps
			removeRelatedFromRWMaps(removed);
			removeRelatedFromPointerMaps(shb.getTrace(removed));
			//remove from pointer map??
			shb.delTrace(removed);
		}

		if(ptachanges){
			updatePTA(changedNodes);
		}

		//redo detection
		detectBothBugsAgain(changedNodes, new HashSet<>(), start_time, null);
		return bugs;
	}



	private boolean compareNewOldTraces(Trace old_trace, Trace new_trace) {
		ArrayList<INode> oldlist = old_trace.getContent();
		ArrayList<INode> newlist = new_trace.getContent();
		if(oldlist.size() != newlist.size()){
			return false;
		}
		int size = oldlist.size();
		for(int i=0; i<size; i++){
			INode oldnode = oldlist.get(i);
			INode newnode = newlist.get(i);
			if(oldnode instanceof ReadNode && newnode instanceof ReadNode){
				ReadNode oldread = (ReadNode) oldnode;
				ReadNode newread = (ReadNode) newnode;
				if(!oldread.equals(newread))
					return false;
			}else if(oldnode instanceof WriteNode && newnode instanceof WriteNode){
				WriteNode oldwrite = (WriteNode) oldnode;
				WriteNode newwrite = (WriteNode) newnode;
				if(!oldwrite.equals(newwrite))
					return false;
			}else if(oldnode instanceof DLockNode && newnode instanceof DLockNode){
				DLockNode oldlock = (DLockNode) oldnode;
				DLockNode newlock = (DLockNode) newnode;
				if(!oldnode.equals(newlock))
					return false;
			}else if(oldnode instanceof DUnlockNode && newnode instanceof DUnlockNode){
				DUnlockNode oldunlock = (DUnlockNode) oldnode;
				DUnlockNode newunlock = (DUnlockNode) newnode;
				if(!oldunlock.equals(newunlock))
					return false;
			}else if(oldnode instanceof MethodNode && newnode instanceof MethodNode){

			}else if(oldnode instanceof StartNode && newnode instanceof StartNode){

			}else if(oldnode instanceof JoinNode && newnode instanceof JoinNode){

			}else{
				return false;
			}
		}
		return true;
	}



	public void removeRelatedFromPointerMaps(Trace old_trace){
		ArrayList<INode> list = old_trace.getContent();
		for (INode inode : list) {
			if(inode instanceof MemNode){
				MemNode mem = (MemNode) inode;
				PointerKey pointer = mem.getPointer();
				HashSet<MemNode> map = pointer_rwmap.get(pointer);
				if(map != null){//map should not be null?? exist some pointer <=> memnode relation not added
					map.remove(mem);
					if(map.size() == 0){
						pointer_rwmap.remove(pointer);
					}
				}
			}else if(inode instanceof DLockNode){
				DLockNode lock = (DLockNode) inode;
				PointerKey pointer = lock.getPointer();
				HashSet<SyncNode> map = pointer_lmap.get(pointer);
				if(map != null){
					map.remove(lock);
					if(map.size() == 0){
						pointer_lmap.remove(pointer);
					}
				}
			}
		}
	}

//	for experiment
	private boolean isdeleting = false;
	public void setDelete(boolean b) {
		isdeleting = b;
	}
	SSAInstruction removeInst = null;
	/**
	 * for experiement of del inst
	 * @param changedNodes
	 * @param ptachanges
	 * @param ps
	 * @return
	 */

/**
 * remove related rw from rwsig_tid_num_maps and sigRead/WriteNodes maps
 * @param removednode
 */
	private void removeRelatedFromRWMaps(CGNode removednode) {
		Trace removed = shb.getTrace(removednode);
		HashMap<String, ArrayList<ReadNode>> rsigMapping = removed.getRsigMapping();
		HashMap<String, ArrayList<WriteNode>> wsigMapping = removed.getWsigMapping();
		ArrayList<Integer> tids = removed.getTraceTids();
		//read
//		System.out.println("Removed sigs : ");
		for (String rsig : rsigMapping.keySet()) {
//			HashSet<ReadNode> readNodes = sigReadNodes.get(rsig);
//			if(readNodes != null)
//			System.out.println(rsig + ": R " + readNodes.size());
			HashMap<Integer, Integer> tidnummap = rsig_tid_num_map.get(rsig);
			if(tidnummap == null){
				continue;
			}else{
				HashSet<Integer> removedTids = new HashSet<>();
				for (Integer tid : tidnummap.keySet()) {
					if(tids.contains(tid)){
						int num = tidnummap.get(tid);
						num--;
						if(num == 0){
							removedTids.add(tid);
						}else{
							tidnummap.put(tid, num);
						}
					}
				}
				for (Integer tid : removedTids) {
					tidnummap.remove(tid);
				}
				if(tidnummap.keySet().size() == 0){
					rsig_tid_num_map.remove(rsig);
				}
			}
			HashSet<ReadNode> reads = sigReadNodes.get(rsig);
			if(reads != null){
				reads.removeAll(rsigMapping.get(rsig));
				if(reads.size() == 0){
					sigReadNodes.remove(rsig);
				}
			}
		}
		//write
		for (String wsig : wsigMapping.keySet()) {
//			HashSet<WriteNode> writeNodes = sigWriteNodes.get(wsig);
//			if(writeNodes != null)
//			System.out.println(wsig + " W: " + writeNodes.size());
			HashMap<Integer, Integer> tidnummap = wsig_tid_num_map.get(wsig);
			if(tidnummap == null){
				continue;
			}else{
				HashSet<Integer> removedTids = new HashSet<>();
				for (Integer tid : tidnummap.keySet()) {
					if(tids.contains(tid)){
						int num = tidnummap.get(tid);
						num--;
						if(num == 0){
							removedTids.add(tid);
						}else{
							tidnummap.put(tid, num);
						}
					}
				}
				for (Integer tid : removedTids) {
					tidnummap.remove(tid);
				}
				if(tidnummap.keySet().size() == 0){
					wsig_tid_num_map.remove(wsig);
				}
			}
			HashSet<WriteNode> writes = sigWriteNodes.get(wsig);
			if(writes != null){
				writes.removeAll(wsigMapping.get(wsig));
				if(writes.size() == 0){
					sigWriteNodes.remove(wsig);
				}
			}
		}
	}

	private void removeRelatedFromRWMaps2(CGNode removednode) {//exact rw number
		Trace removed = shb.getTrace(removednode);
		ArrayList<INode> list = removed.getContent();
		ArrayList<Integer> tids = removed.getTraceTids();
		for (INode node : list) {
			if(node instanceof ReadNode){
				for (String old : ((ReadNode) node).getObjSig()) {
//					if(removed_rw.contains(old)){
						HashMap<Integer, Integer> map = rsig_tid_num_map.get(old);
						if(map == null)//should not be null??
							continue;
						HashSet<Integer> removedTids = new HashSet<>();
						for (Integer tid : map.keySet()) {
							if(tids.contains(tid)){
								int num = map.get(tid);
								num--;
								if(num == 0){
									removedTids.add(tid);
								}else{
									map.put(tid, num);
								}
							}
						}
						for (Integer tid : removedTids) {
							map.remove(tid);
						}
						if(map.keySet().size() == 0){
							sigReadNodes.remove(old);
							rsig_tid_num_map.remove(old);
						}
						HashSet<ReadNode> reads = sigReadNodes.get(old);
						if(reads != null){
							reads.remove(node);
						}
//					}
				}
			}else if(node instanceof WriteNode){//write node
				for (String old : ((WriteNode) node).getObjSig()) {
//					if(removed_rw.contains(old)){
						HashMap<Integer, Integer> map = wsig_tid_num_map.get(old);
						if(map == null)//should not be null??
							continue;
						HashSet<Integer> removedTids = new HashSet<>();
						for (Integer tid : map.keySet()) {
							if(tids.contains(tid)){
								int num = map.get(tid);
								num--;
								if(num == 0){
									removedTids.add(tid);//?
								}else{
									map.put(tid, num);
								}
							}
						}
						for (Integer tid : removedTids) {
							map.remove(tid);
						}
						if(map.keySet().size() == 0){
							sigWriteNodes.remove(old);
							wsig_tid_num_map.remove(old);
						}
						HashSet<WriteNode> writes = sigWriteNodes.get(old);
						if(writes != null){
							writes.remove(node);
						}
//					}
				}
			}
		}
	}





	private void removeBugsRelatedToInterests(Set<CGNode> keys, HashSet<CGNode> changedModifiers) {
		if(changedModifiers != null){
			keys.addAll(changedModifiers);
		}
		HashSet<ITIDEBug> removed = new HashSet<>();
		for (ITIDEBug bug : bugs) {
			if(bug instanceof TIDERace){
				TIDERace race = (TIDERace) bug;
				String racesig = race.initsig;
				if(removed_rw.contains(racesig)){
					removed.add(bug);
				}else{
					CGNode node1 = race.node1.getBelonging();
					CGNode node2 = race.node2.getBelonging();
					if(keys.contains(node1) || keys.contains(node2)){
						removed.add(bug);
					}
				}
			}else{//tide deadlock
				TIDEDeadlock dl = (TIDEDeadlock) bug;
				HashSet<String> dlsigs = dl.getInvolvedSig();
				for (String dlsig : dlsigs) {
					if(removed_l.contains(dlsig)){
						removed.add(bug);
					}else{
						CGNode dl11 = dl.lp1.lock1.getBelonging();
						CGNode dl12 = dl.lp1.lock2.getBelonging();
						CGNode dl21 = dl.lp2.lock1.getBelonging();
						CGNode dl22 = dl.lp2.lock2.getBelonging();
						if(keys.contains(dl11) || keys.contains(dl12) || keys.contains(dl21) || keys.contains(dl22)){
							removed.add(bug);
						}
					}
					if(bugs.size() == removed.size()){
						break;
					}
				}
			}
		}
//		System.out.println("SIZE of Removed Bugs (in removeBugsRelatedToInterests): " + removed.size());
//		for (ITIDEBug bug : removed) {
//			if(bug instanceof TIDERace){
//				MemNode exist1 = ((TIDERace) bug).node1;
//				MemNode exist2 = ((TIDERace) bug).node2;
//				System.out.println("Remove ======================================================================");
//				System.out.println("1: " + exist1.getPrefix() + exist1.getLocalSig() + "  with tid: " + ((TIDERace) bug).tid1);
//				System.out.println("2: " + exist2.getPrefix() + exist2.getLocalSig() + "  with tid: " + ((TIDERace) bug).tid2);
//			}
//		}
		bugs.removeAll(removed);
		removedbugs.addAll(removed);
//		ignoredRaces.addAll((Collection<? extends TIDERace>) removed);
	}


	private void removeKidThreadRelations(Trace old_trace) {
		ArrayList<Integer> ptids = old_trace.getTraceTids();
		for (Integer pid : ptids) {//parent id
			//start map
			HashSet<Integer> kids = old_trace.getKidTidFor(pid);//kid id of parent id
			if(kids == null)//no such relation, deleted in previous round
				continue;
			StartNode parentnode = mapOfStartNode.get(pid);
			if(parentnode != null){//should not be null?? must foget to del pid in oldtrace.tids
				for (Integer kid : kids) {
					parentnode.removeChild(kid);
					mapOfStartNode.remove(kid);
					stidpool.remove(kid);
					//join map
					mapOfJoinNode.remove(kid);//?
				}
			}
		}
	}

	private void removeThisThreadRelations(Trace trace) {
		// TODO Auto-generated method stub
		ArrayList<Integer> tids = trace.getTraceTids();
		for (Integer tid : tids) {//kid
			StartNode snode = mapOfStartNode.get(tid);
			if(snode != null){
				int pid = snode.getParentTID();
				StartNode pnode = mapOfStartNode.get(pid);
				if(pnode != null){
				  pnode.removeChild(tid);
//					CGNode pCgNode = pnode.getBelonging();
//					if(pCgNode != null){//pCgNode == null => pCgNode is main
//						Trace pTrace = shb.getTrace(pCgNode);
//						pTrace.removeKidTidFor(pid, tid);
//					}
				}
			}
			mapOfStartNode.remove(tid);
			mapOfJoinNode.remove(tid);
			stidpool.remove(tid);
		}
	}


	private void computeAndRemoveCurrentLockedNodes(CGNode node, Trace old_trace) {
		//discover 1st locknode
		ArrayList<INode> list = old_trace.getContent();
		DLockNode lock1st = null;
		for(int i=0; i<list.size(); i++){
			INode inode = list.get(i);
			if(inode instanceof DLockNode){
				lock1st = (DLockNode) inode;
				hasLocks.put(node, true);
				break;
			}
 		}
		if(lock1st == null){
			return;
		}
		//collect the locknode that before -> 1st locknode
		ArrayList<Integer> tids = old_trace.getTraceTids();
		for (Integer tid : tids) {
			ArrayList<DLLockPair> lockorders = threadDLLockPairs.get(tid);
			if(lockorders == null)
				continue;
			for (DLLockPair order : lockorders) {
				if(order.lock2.equals(lock1st)){
					DLockNode lockbefore = order.lock1;
					HashSet<DLockNode> currentNodes = threadLockNodes.get(tid);
					if(currentNodes == null){
						currentNodes = new HashSet<>();
					}
					currentNodes.add(lockbefore);
				}
			}
		}
		//remove the dlpair that related to trace
		ArrayList<DLLockPair> removed = new ArrayList<>();
		for (int tid : old_trace.getTraceTids()) {
			ArrayList<DLLockPair> threadlocks = threadDLLockPairs.get(tid);
			if(threadlocks == null)//should not??
				continue;
			for (DLLockPair pair : threadlocks) {
				DLockNode lockNode = pair.lock1;
				DLockNode lockNode2 = pair.lock2;
				CGNode cgNode = lockNode.getBelonging();
				CGNode cgNode2 = lockNode2.getBelonging();
				if(cgNode.equals(node) || cgNode2.equals(node)){
					removed.add(pair);
					removed_l.add(lockNode);
					removed_l.add(lockNode2);
				}
			}
			threadlocks.removeAll(removed);
			removed.clear();
		}
	}


/**
 * assume cgnode id will not change
 * @param n
 * @param curTrace
 */
	private void traverseNodePNInc(CGNode n, Trace curTrace) {
		//		System.out.println("Traverse Node: "+ n.toString());
		if(n.getIR() == null)
			return;
		ArrayList<Integer> tids = curTrace.getTraceTids();
		ArrayList<Integer> oldkids = new ArrayList<>();
		oldkids.addAll(curTrace.getOldKids());
		HashMap<Integer, Integer> oldkid_line_map = new HashMap<>();
		oldkid_line_map.putAll(curTrace.getOldkidsMap());
		ArrayList<Integer> dupkids = new ArrayList<>();

//		curTID = n.getGraphNodeId();
//		if(!tids.contains(curTID)){
//			System.out.println("Old tids does not contain this cgnode. ");
//		}
		curTID = tids.get(0);
		stidpool.add(curTID);

		//start traverse inst
		SSACFG cfg = n.getIR().getControlFlowGraph();
		//		System.out.println(ssacfg.toString());
		HashSet<SSAInstruction> catchinsts = InstInsideCatchBlock(cfg);

		SSAInstruction[] insts = n.getIR().getInstructions();

		for(int i=0; i<insts.length; i++){
			SSAInstruction inst = insts[i];
			if(inst!=null){
				if(isdeleting){
					if(removeInst.equals(inst))
						continue;
				}
				if(catchinsts.contains(inst)){
					continue;
				}
				IMethod method = n.getMethod() ;
				int sourceLineNum = 0;
				IFile file = null;
				try{//get source code line number of this inst
					if(n.getIR().getMethod() instanceof IBytecodeMethod){
						int bytecodeindex = ((IBytecodeMethod) n.getIR().getMethod()).getBytecodeIndex(inst.iindex);
						sourceLineNum = (int)n.getIR().getMethod().getLineNumber(bytecodeindex);
					}else{
						SourcePosition position = n.getMethod().getSourcePosition(inst.iindex);
						sourceLineNum = position.getFirstLine();//.getLastLine();
						if(position instanceof JdtPosition){
							file = ((JdtPosition) position).getEclipseFile();
						}
					}
					//					System.out.println(inst.iindex);
				}catch(Exception e){
					e.printStackTrace();
				}

				//System.out.println(inst.toString());
				if(inst instanceof SSAFieldAccessInstruction){

					//not in constructor
					if(n.getMethod().isClinit()||n.getMethod().isInit())
						continue;
					//field access before monitorenter, check
					if(i+1 < insts.length){
						SSAInstruction next = insts[i+1];
						if(next instanceof SSAMonitorInstruction){
							SSAFieldAccessInstruction access = (SSAFieldAccessInstruction)inst;
							int result = access.getDef();//result
							int locked = ((SSAMonitorInstruction) next).getRef();
							if(result == locked){
								//pre-read of lock/monitor enter, do not record
								//check previous read
								if(i-1 >= 0){
									SSAInstruction pred = insts[i-1];
									int ref = access.getRef();
									if(pred instanceof SSAGetInstruction){
										int result2 = ((SSAGetInstruction) pred).getDef();//result
										if(result2 == ref && result2 != -1 && ref != -1){
											//another field access before monitorenter, ignore
											//removed node in trace
											curTrace.removeLastNode();
										}
									}
								}
								continue;
							}
						}
					}
					//TODO: handling field access of external objects

					String classname = ((SSAFieldAccessInstruction)inst).getDeclaredField().getDeclaringClass().getName().toString();
					String fieldname = ((SSAFieldAccessInstruction)inst).getDeclaredField().getName().toString();
					String sig = classname.substring(1)+"."+fieldname;

					String typeclassname =  method.getDeclaringClass().getName().toString();
					String instSig =typeclassname.substring(1)+":"+sourceLineNum;

					if(((SSAFieldAccessInstruction)inst).isStatic()){
						logFieldAccess3(inst, sourceLineNum, instSig, curTrace, n, null, null, sig, file);
					}else{
						int baseValueNumber = ((SSAFieldAccessInstruction)inst).getUse(0);
						if(baseValueNumber==1){//this.f
							PointerKey basePointer = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, baseValueNumber);//+
							OrdinalSet<InstanceKey> baseObjects = pointerAnalysis.getPointsToSet(basePointer);//+
							logFieldAccess3(inst, sourceLineNum, instSig, curTrace, n, basePointer, baseObjects, sig, file);
							//								if(curReceivers!=null){
							//									for(String receiver : curReceivers){
							//										String sig2 = sig+"."+receiver;
							//										logFieldAccess(inst, sig2, sourceLineNum, instSig, curTrace);
							////										System.out.println(sig2);
							//									}
							//								}
						}else{
							PointerKey basePointer = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, baseValueNumber);
							OrdinalSet<InstanceKey> baseObjects = pointerAnalysis.getPointsToSet(basePointer);
							logFieldAccess3(inst, sourceLineNum, instSig, curTrace, n, basePointer, baseObjects, sig, file);
							//								for (InstanceKey instanceKey : baseObjects) {
							//									if(curReceivers==null||curReceivers.isEmpty()){
							//										String sig2 = sig+"."+String.valueOf(instanceKey.hashCode());
							//										logFieldAccess(inst, sig2, sourceLineNum, instSig, curTrace);
							//										//									System.out.println(sig2);
							//									}else{
							//										for(String receiver : curReceivers){
							//											String sig2 = sig+"."+receiver+"Y"+String.valueOf(instanceKey.hashCode());
							//											logFieldAccess(inst, sig2, sourceLineNum, instSig, curTrace);
							//											//										System.out.println(sig2);
							//										}
							//									}
							//								}

						}
					}
				}
				else if (inst instanceof SSAArrayReferenceInstruction)
				{
					SSAArrayReferenceInstruction arrayRefInst = (SSAArrayReferenceInstruction) inst;
					int arrayRef = arrayRefInst.getArrayRef();
					String typeclassname =  method.getDeclaringClass().getName().toString();
					String instSig =typeclassname.substring(1)+":"+sourceLineNum;

					PointerKey key = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, arrayRef);
					OrdinalSet<InstanceKey> instances = pointerAnalysis.getPointsToSet(key);
					String field = handleArrayTypes(arrayRefInst, n, instances);
					logArrayAccess3(inst, sourceLineNum, instSig, curTrace, n, key, instances, file, field);

				}else if (inst instanceof SSAAbstractInvokeInstruction){

					CallSiteReference csr = ((SSAAbstractInvokeInstruction)inst).getCallSite();
					MethodReference mr = csr.getDeclaredTarget();
					//if (AnalysisUtils.implementsRunnableInterface(iclass) || AnalysisUtils.extendsThreadClass(iclass))
					{
						com.ibm.wala.classLoader.IMethod imethod = callGraph.getClassHierarchy().resolveMethod(mr);
						if(imethod!=null){
							String sig = imethod.getSignature();
							//System.out.println("Invoke Inst: "+sig);
							if(sig.equals("java.lang.Thread.start()V")){
								PointerKey key = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, ((SSAAbstractInvokeInstruction) inst).getReceiver());
								OrdinalSet<InstanceKey> instances = pointerAnalysis.getPointsToSet(key);
								for(InstanceKey ins: instances){
									TypeName name = ins.getConcreteType().getName();
									CGNode node = threadSigNodeMap.get(name);
//									HashSet<String> threadReceivers = new HashSet<>();
									//FIXME: BUG
									if(node==null){
										//TODO: find out which runnable object -- need data flow analysis
										int param = ((SSAAbstractInvokeInstruction)inst).getUse(0);
										if(sig.contains("java.util.concurrent") && sig.contains("execute")){
											param = ((SSAAbstractInvokeInstruction)inst).getUse(1);
										}
										node = handleRunnable(ins, param, n);
										if(node==null){
//											System.err.println("ERROR: starting new thread: "+ name);
											continue;
										}
										//threadreceiver?
									}else{//get threadReceivers
										//should be the hashcode of the instancekey
//										threadReceivers.add(String.valueOf(ins.hashCode()));//SHOULD BE "this thread/runnable object"
									}

									//duplicate graph node id or existing node with trace?
									Trace exist;
									int tempid = node.getGraphNodeId();
									if(stidpool.contains(tempid)){
										if(oldkids.contains(tempid)){
											int linenum = oldkid_line_map.get(tempid);
											if(linenum != sourceLineNum){//changed => new thread
												exist = null;
												AstCGNode2 threadNode = new AstCGNode2(imethod, node.getContext());
												int threadID = ++maxGraphNodeID;
												threadNode.setGraphNodeId(threadID);
												threadNode.setCGNode(node);
												threadNode.setIR(node.getIR());
												node = threadNode;
												dupkids.add(tempid);
											}else{
												exist = shb.getTrace(node);
											}
											oldkids.remove(oldkids.indexOf(tempid));
										}else{//new thread
											exist = null;
											AstCGNode2 threadNode = new AstCGNode2(imethod, node.getContext());
											int threadID = ++maxGraphNodeID;
											threadNode.setGraphNodeId(threadID);
											threadNode.setCGNode(node);
											threadNode.setIR(node.getIR());
//												newRunTargets.put(threadNode, node);
											node = threadNode;
										}
									}else{//new thread, may exist
										exist = shb.getTrace(node);
									}
									if(exist == null){//new threadnode
										threadNodes.add(node);
									}else if(oldkids.contains(tempid)){
										oldkids.remove(oldkids.indexOf(tempid));
									}
									int tid_child = node.getGraphNodeId();
									stidpool.add(tid_child);
									//add node to trace
									StartNode startNode = new StartNode(curTID, tid_child, n, node, sourceLineNum, file);
									curTrace.addS(startNode, inst, tid_child);
									mapOfStartNode.put(tid_child, startNode);
									StartNode pstartnode = mapOfStartNode.get(curTID);
									if(pstartnode == null){
										if(mainEntryNodes.contains(n)){
											pstartnode = new StartNode(-1, curTID, n, node, sourceLineNum, file);
											mapOfStartNode.put(curTID, pstartnode);
										}else{//thread/runnable
											pstartnode = new StartNode(curTID, tid_child, n, node,sourceLineNum, file);
											mapOfStartNode.put(tid_child, pstartnode);
										}
									}
									pstartnode.addChild(tid_child);
									shb.addEdge(startNode, node);

									//put to tid -> curreceivers map
//									tid2Receivers.put(node.getGraphNodeId(), threadReceivers);
									//TODO: check if it is in a simple loop
									boolean isInLoop = isInLoop(n,inst);

									if(isInLoop){
										AstCGNode2 node2 = n_loopn_map.get(node);
										int newID;
										if(node2 == null){
											node2 = new AstCGNode2(node.getMethod(),node.getContext());
											newID = ++maxGraphNodeID;
											node2.setGraphNodeId(newID);
											node2.setIR(node.getIR());
											node2.setCGNode(node);
											threadNodes.add(node2);
										}else{
											newID = node2.getGraphNodeId();//astCGNode_ntid_map.get(node);
											if(oldkids.contains(newID)){
												oldkids.remove(oldkids.indexOf(newID));
											}
										}
										stidpool.add(newID);
										StartNode duplicate = new StartNode(curTID, newID, n, node2, sourceLineNum, file);
										curTrace.add2S(duplicate, inst, newID);//thread id +1
										mapOfStartNode.put(newID, duplicate);
										mapOfStartNode.get(curTID).addChild(newID);
										shb.addEdge(duplicate, node2);

//										node2.setGraphNodeId(newID);
//										node2.setIR(node.getIR());
//										node2.setCGNode(node);

										//need to change thread receiver id as well
//										Set<String> threadReceivers2 = new HashSet();
//										for(String id: threadReceivers){
//											threadReceivers2.add(id+"X");//"X" as the marker
//										}
//										//put to tid -> curreceivers map
//										tid2Receivers.put(newID, threadReceivers2);
									}
								}
								hasSyncBetween = true;
							}
							else if(sig.equals("java.lang.Thread.join()V")){
								PointerKey key = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, ((SSAAbstractInvokeInstruction) inst).getReceiver());
								OrdinalSet<InstanceKey> instances = pointerAnalysis.getPointsToSet(key);
								for(InstanceKey ins: instances){
									TypeName name = ins.getConcreteType().getName();
									CGNode node = threadSigNodeMap.get(name);
//									HashSet<String> threadReceivers = new HashSet();
									if(node==null){//could be a runnable class
										int param = ((SSAAbstractInvokeInstruction)inst).getUse(0);
										//Executors and ThreadPoolExecutor
										node = handleRunnable(ins,param, n);
										if(node==null){
//											System.err.println("ERROR: joining parent thread: "+ name);
											continue;
										}
									}

									//add node to trace
									int tid_child =node.getGraphNodeId();
									if(mapOfJoinNode.containsKey(tid_child)){
										//dup run nodes
										CGNode threadNode = dupStartJoinTidMap.get(tid_child);
										if(threadNode != null){
											tid_child = threadNode.getGraphNodeId();
											node = threadNode;
										}
									}
									JoinNode jNode = new JoinNode(curTID, tid_child, n, node, sourceLineNum, file);
									curTrace.addJ(jNode, inst);
									mapOfJoinNode.put(tid_child, jNode);
									shb.addBackEdge(node, jNode);

									boolean isInLoop = isInLoop(n,inst);
									if(isInLoop){
										AstCGNode2 node2 = n_loopn_map.get(node);
										if(node2 == null){
											node2 = dupStartJoinTidMap.get(tid_child);
											if(node2 == null){
												System.err.println("Null node obtain from n_loopn_map. ");
												continue;
											}
										}
										//threadNodes.add(node2);
										int newID = node2.getGraphNodeId();
										JoinNode jNode2 = new JoinNode(curTID,newID,n,node2, sourceLineNum, file);
										curTrace.addJ(jNode2, inst);//thread id +1
										mapOfJoinNode.put(newID, jNode2);
										shb.addBackEdge(node2, jNode2);
//										node2.setGraphNodeId(newID);
//										node2.setIR(node.getIR());
//										node2.setCGNode(node);
									}
								}
								hasSyncBetween = true;
							}else if(sig.equals("java.lang.Thread.<init>(Ljava/lang/Runnable;)V")){
								//for new Thread(new Runnable)
								int use0 = inst.getUse(0);
//								int use1 = inst.getUse(1);
								threadInits.put(use0, (SSAAbstractInvokeInstruction)inst);
							}else{
								//other method calls
								//save current curReceivers
//								Set<String> curReceivers_pre = curReceivers;
								//process NEW method call
								Set<CGNode> set = new HashSet<>();
								if(n instanceof AstCGNode2){
									set = callGraph.getPossibleTargets(((AstCGNode2)n).getCGNode(), csr);//newRunTargets.get(n)
								}else{
									set = callGraph.getPossibleTargets(n, csr);
								}
								for(CGNode node: set){
									if(AnalysisUtils.isApplicationClass(node.getMethod().getDeclaringClass())){
										//static method call
										if(node.getMethod().isStatic()){
											//omit the pointer-lock map
											//set current receivers to null
//											curReceivers = null;
											//use classname as lock obj
											String typeclassname =  n.getMethod().getDeclaringClass().getName().toString();
											String instSig =typeclassname.substring(1)+":"+sourceLineNum;
											String lock = node.getMethod().getDeclaringClass().getName().toString();
											//take out records
											HashSet<DLockNode> currentNodes = threadLockNodes.get(curTID);
											if(currentNodes==null){
												currentNodes = new HashSet<DLockNode>();
												threadLockNodes.put(curTID,currentNodes);
											}
											ArrayList<DLLockPair> dLLockPairs = threadDLLockPairs.get(curTID);
											if(dLLockPairs==null){
												dLLockPairs = new ArrayList<DLLockPair>();
												threadDLLockPairs.put(curTID, dLLockPairs);
											}
											DLockNode will = null;
											//if synchronized method, add lock/unlock
											if(node.getMethod().isSynchronized()){
												// for deadlock
												will = new DLockNode(curTID,instSig, sourceLineNum, null, null, n, inst,file);
												will.addLockSig(lock);
												for (DLockNode exist : currentNodes) {
													dLLockPairs.add(new DLLockPair(exist, will));
												}
												curTrace.add(will);
//												addToThreadSyncNodes(will);
												threadLockNodes.get(curTID).add(will);
												interested_l.add(will);
											}
											MethodNode m = new MethodNode(n, node, curTID, sourceLineNum, file, (SSAAbstractInvokeInstruction) inst);
											curTrace.add(m);
											//												idx.add(trace.indexOf(m));
											Trace subTrace0 = shb.getTrace(node);
											if(subTrace0 == null){
												subTrace0 = traverseNodePN(node);
											}else{
												//let curtrace edges include new tids
												shb.includeTidForKidTraces(node, curTID);
											}
//											subTrace0.includeTids(curTrace.getTraceTids());
											shb.addEdge(m, node);
											//
											if(node.getMethod().isSynchronized()){
												DUnlockNode unlock = new DUnlockNode(curTID, instSig, sourceLineNum, null, null, n, sourceLineNum);
												unlock.addLockSig(lock);
												//lock engine
												curTrace.addLockPair(new LockPair(will, unlock));
												//remove
												curTrace.add(unlock);
//												addToThreadSyncNodes(unlock);
												threadLockNodes.get(curTID).remove(will);
											}
										}else{
											//instance
											int objectValueNumber = inst.getUse(0);
											PointerKey objectPointer = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, objectValueNumber);
											OrdinalSet<InstanceKey> lockedObjects = pointerAnalysis.getPointsToSet(objectPointer);

											//												HashSet<DLockNode> wills = new HashSet<>();
											DLockNode will = null;
											if(lockedObjects.size()>0){//must be larger than 0
//												curReceivers = new HashSet<>();
												//take out records
												HashSet<DLockNode> currentNodes = threadLockNodes.get(curTID);
												if(currentNodes==null){
													currentNodes = new HashSet<DLockNode>();
													threadLockNodes.put(curTID,currentNodes);
												}
												ArrayList<DLLockPair> dLLockPairs = threadDLLockPairs.get(curTID);
												if(dLLockPairs==null){
													dLLockPairs = new ArrayList<DLLockPair>();
													threadDLLockPairs.put(curTID, dLLockPairs);
												}
												//start to record new locks
												if(node.getMethod().isSynchronized()){
													String typeclassname = n.getMethod().getDeclaringClass().getName().toString();
													String instSig = typeclassname.substring(1)+":"+sourceLineNum;
													will = new DLockNode(curTID,instSig, sourceLineNum, objectPointer, lockedObjects, n, inst, file);
													for (InstanceKey key : lockedObjects) {
														String lock = key.getConcreteType().getName()+"."+key.hashCode();
														will.addLockSig(lock);
													}
													// for deadlock
													for (DLockNode exist : currentNodes) {
														dLLockPairs.add(new DLLockPair(exist, will));
													}
													//														wills.add(will);
													//for race
													curTrace.add(will);
//													addToThreadSyncNodes(will);
													threadLockNodes.get(curTID).add(will);
													interested_l.add(will);
													//for pointer-lock map
													HashSet<SyncNode> ls = pointer_lmap.get(objectPointer);
													if(ls == null){
														ls = new HashSet<>();
														ls.add(will);
														pointer_lmap.put(objectPointer, ls);
													}else{
														ls.add(will);
													}
												}

												//configuration
//												int K_obj_sensitive = 0;//0 means non-object sensitive
//												for (InstanceKey instanceKey : lockedObjects) {
//													//add receiver
//													if(K_obj_sensitive>0&&curReceivers_pre!=null){
//														for(String instance_pre: curReceivers_pre){
//															String temmStr = instance_pre;
//															String curObject = String.valueOf(instanceKey.hashCode());
//															//find the last Y or not
//															int indexY = instance_pre.lastIndexOf("Y");
//															if(indexY>-1)
//																temmStr = instance_pre.substring(indexY);
//															//object sensitivity is memory-demanding -- limit it to 2
//															//count number of Ys
//															int Kount = temmStr.length() - temmStr.replaceAll("Y", "").length();
//															if(Kount<=K_obj_sensitive
//																	&&!temmStr.equals(curObject))//-- limit it to 2
//																curReceivers.add(instance_pre+"Y"+curObject);
//														}
//													}else
//														curReceivers.add(String.valueOf(instanceKey.hashCode()));
//												}
											}
											//
											MethodNode m = new MethodNode(n, node, curTID, sourceLineNum, file, (SSAAbstractInvokeInstruction) inst);
											curTrace.add(m);
											//
											Trace subTrace1 = shb.getTrace(node);
											if(subTrace1 == null){
												subTrace1 = traverseNodePN(node);
											}else{
												//let curtrace edges include new tids
												shb.includeTidForKidTraces(node, curTID);
											}
//											subTrace1.includeTids(curTrace.getTraceTids());
											shb.addEdge(m, node);
											//
											if(lockedObjects.size() > 0){
												if(node.getMethod().isSynchronized()){
													String typeclassname =  node.getMethod().getDeclaringClass().getName().toString();
													String instSig =typeclassname.substring(1)+":"+sourceLineNum;
													DUnlockNode unlock = new DUnlockNode(curTID, instSig, sourceLineNum, objectPointer, lockedObjects, n, sourceLineNum);
//													if(lockedObjects.size() == 1){
														LockPair lockPair = new LockPair(will, unlock);
														curTrace.addLockPair(lockPair);
//													}
													for (InstanceKey instanceKey : lockedObjects) {
														String lock = instanceKey.getConcreteType().getName()+"."+instanceKey.hashCode();
														unlock.addLockSig(lock);
														//													lockEngine.add(lock, curTID, lockPair);
													}

													//for race
													curTrace.add(unlock);
													//												addToThreadSyncNodes(unlock);
													// for deadlock
													threadLockNodes.get(curTID).remove(will);
												}
											}
										}
									}
//									curReceivers = curReceivers_pre;
								}
							}
						}
					}
				}
				else if(inst instanceof SSAMonitorInstruction)
				{
					//lock node: GID, TID, LockID
					SSAMonitorInstruction monitorInstruction = ((SSAMonitorInstruction) inst);
					int lockValueNumber = monitorInstruction.getRef();

					PointerKey lockPointer = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, lockValueNumber);
					OrdinalSet<InstanceKey> lockObjects = pointerAnalysis.getPointsToSet(lockPointer);
					// for deadlock
					String typeclassname =  n.getMethod().getDeclaringClass().getName().toString();
					String instSig =typeclassname.substring(1)+":"+sourceLineNum;
					DLockNode will = null;
					DUnlockNode next = null;
					//take out record
					HashSet<DLockNode> currentNodes = threadLockNodes.get(curTID);
					if(currentNodes==null){
						currentNodes = new HashSet<DLockNode>();
						threadLockNodes.put(curTID,currentNodes);
					}
					ArrayList<DLLockPair> dlpairs = threadDLLockPairs.get(curTID);
					if(dlpairs==null){
						dlpairs = new ArrayList<DLLockPair>();
						threadDLLockPairs.put(curTID, dlpairs);
					}
					for (InstanceKey instanceKey : lockObjects) {
						String lock = instanceKey.getConcreteType().getName()+"."+instanceKey.hashCode();
						if(((SSAMonitorInstruction) inst).isMonitorEnter()){
							will = new DLockNode(curTID, instSig, sourceLineNum, lockPointer, lockObjects, n, inst, file);
							will.addLockSig(lock);
						}else{
							next = new DUnlockNode(curTID, instSig, sourceLineNum, lockPointer, lockObjects, n, sourceLineNum);
							next.addLockSig(lock);
							//lock engine
							for (Iterator iterator = currentNodes.iterator(); iterator.hasNext();) {
								DLockNode dLockNode = (DLockNode) iterator.next();
//								lockEngine.add(lock, curTID, new LockPair(dLockNode, next));
								if (dLockNode.getInstSig().equals(instSig)) {//maybe compare pointer?
									will = dLockNode;
									break;
								}
							}
						}
					}

					if(((SSAMonitorInstruction) inst).isMonitorEnter()){
						if(will != null){
							for (DLockNode exist : currentNodes) {
								dlpairs.add(new DLLockPair(exist, will));
							}
							curTrace.add(will);
							threadLockNodes.get(curTID).add(will);
							interested_l.add(will);
							//						addToThreadSyncNodes(will);
							//for pointer-lock map
							HashSet<SyncNode> ls = pointer_lmap.get(lockPointer);
							if(ls == null){
								ls = new HashSet<>();
								ls.add(will);
								pointer_lmap.put(lockPointer, ls);
							}else{
								ls.add(will);
							}
						}
					}else {//monitor exit
						if(will != null){
							curTrace.add(next);
//							if(lockObjects.size() == 1){
								curTrace.addLockPair(new LockPair(will, next));
//							}
//						addToThreadSyncNodes(next);
							threadLockNodes.get(curTID).remove(will);
						}
					}
					hasSyncBetween = true;
				}
			}
		}

		for (int tid : tids) {
			//threads with same instructions, adding nodes to curTrace with other tids
			if(tid != curTID){
				traverseNodePNInc2(n, curTrace, tid);
			}
		}

		//if has new threadnode; traverse all its instructions as the first time
		while(!threadNodes.isEmpty()){
			CGNode newnode = threadNodes.removeFirst();
			int newID = newnode.getGraphNodeId();
			if(mapOfStartNode.get(newID) == null){
				//no such node, should be created and added before, should not be??
				System.err.println("thread " + newID + " is newly discovered");
			}
			curTID = newID;
			hasSyncBetween = false;
			traverseNodePN(newnode);
		}
		//if old kids has left => the thread should have been removed earlier
//		if(oldkids.size() > 0){
//			for (int oldkid : oldkids) {
//				System.err.println("thread " + oldkid + " should have been removed earlier");
//				mapOfStartNode.remove(oldkid);
//				tidpool.remove(oldkid);
//				shb.removeTidFromALlTraces(n, oldkid);
//				threadDLLockPairs.remove(oldkid);
//			}
//		}
//		if(dupkids.size() > 0){
//			for (int dupkid : dupkids) {
//				System.err.println("thread " + dupkid + " should not have duplicate tids");
//				mapOfStartNode.remove(dupkid);
//				tidpool.remove(dupkid);
//				shb.removeTidFromALlTraces(n, dupkid);
//				threadDLLockPairs.remove(dupkid);
//			}
//		}
		//add back to shb
		shb.replaceTrace(n, curTrace);
		//organize wtid num map/rwsigmap
		singleOrganizeRWMaps(curTrace);
	}


//	private Trace traverseNodePNIncCompare(CGNode n, Trace old_trace) {
//		//		System.out.println("Traverse Node: "+ n.toString());
//		if(n.getIR() == null)
//			return null;
//		ArrayList<Integer> tids = old_trace.getTraceTids();
//		ArrayList<Integer> oldkids = new ArrayList<>();
//		oldkids.addAll(old_trace.getOldKids());
//		HashMap<Integer, Integer> oldkid_line_map = new HashMap<>();
//		oldkid_line_map.putAll(old_trace.getOldkidsMap());
//		ArrayList<Integer> dupkids = new ArrayList<>();
//
////		int idxOfTid = 0;
//		curTID = tids.get(0);
//		tidpool.add(curTID);
//		Trace new_trace = new Trace(curTID);
//
////		idxOfTid++;
//		//start traverse inst
//		SSACFG cfg = n.getIR().getControlFlowGraph();
//		//		System.out.println(ssacfg.toString());
//		HashSet<SSAInstruction> catchinsts = InstInsideCatchBlock(cfg);
//
//		SSAInstruction[] insts = n.getIR().getInstructions();
//		for(int i=0; i<insts.length; i++){
//			SSAInstruction inst = insts[i];
//
//			if(inst!=null){
//				if(isdeleting){
//					if(removeInst.equals(inst))
//						continue;
//				}
//				if(catchinsts.contains(inst)){
//					continue;
//				}
//				IMethod method = n.getMethod() ;
//				int sourceLineNum = 0;
//				try{//get source code line number of this inst
//					if(n.getIR().getMethod() instanceof IBytecodeMethod){
//						int bytecodeindex = ((IBytecodeMethod) n.getIR().getMethod()).getBytecodeIndex(inst.iindex);
//						sourceLineNum = (int)n.getIR().getMethod().getLineNumber(bytecodeindex);
//					}else{
//						sourceLineNum = n.getMethod().getSourcePosition(inst.iindex).getFirstLine();//.getLastLine();
//					}
//					//					System.out.println(inst.iindex);
//				}catch(Exception e){
//					e.printStackTrace();
//				}
//
//				//System.out.println(inst.toString());
//				if(inst instanceof SSAFieldAccessInstruction){
//
//					//not in constructor
//					if(n.getMethod().isClinit()||n.getMethod().isInit())
//						continue;
//					//TODO: handling field access of external objects
//
//					String classname = ((SSAFieldAccessInstruction)inst).getDeclaredField().getDeclaringClass().getName().toString();
//					String fieldname = ((SSAFieldAccessInstruction)inst).getDeclaredField().getName().toString();
//					String sig = classname.substring(1)+"."+fieldname;
//
//					String typeclassname =  method.getDeclaringClass().getName().toString();
//					String instSig =typeclassname.substring(1)+":"+sourceLineNum;
//
//					if(((SSAFieldAccessInstruction)inst).isStatic()){
//						logFieldAccess3(inst, sourceLineNum, instSig, new_trace, n, null, null, sig);
//					}else{
//						int baseValueNumber = ((SSAFieldAccessInstruction)inst).getUse(0);
//						if(baseValueNumber==1){//this.f
//							PointerKey basePointer = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, baseValueNumber);//+
//							OrdinalSet<InstanceKey> baseObjects = pointerAnalysis.getPointsToSet(basePointer);//+
//							logFieldAccess3(inst, sourceLineNum, instSig, new_trace, n, basePointer, baseObjects, sig);
//							//								if(curReceivers!=null){
//							//									for(String receiver : curReceivers){
//							//										String sig2 = sig+"."+receiver;
//							//										logFieldAccess(inst, sig2, sourceLineNum, instSig, curTrace);
//							////										System.out.println(sig2);
//							//									}
//							//								}
//						}else{
//							PointerKey basePointer = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, baseValueNumber);
//							OrdinalSet<InstanceKey> baseObjects = pointerAnalysis.getPointsToSet(basePointer);
//							logFieldAccess3(inst, sourceLineNum, instSig, new_trace, n, basePointer, baseObjects, sig);
//							//								for (InstanceKey instanceKey : baseObjects) {
//							//									if(curReceivers==null||curReceivers.isEmpty()){
//							//										String sig2 = sig+"."+String.valueOf(instanceKey.hashCode());
//							//										logFieldAccess(inst, sig2, sourceLineNum, instSig, curTrace);
//							//										//									System.out.println(sig2);
//							//									}else{
//							//										for(String receiver : curReceivers){
//							//											String sig2 = sig+"."+receiver+"Y"+String.valueOf(instanceKey.hashCode());
//							//											logFieldAccess(inst, sig2, sourceLineNum, instSig, curTrace);
//							//											//										System.out.println(sig2);
//							//										}
//							//									}
//							//								}
//
//						}
//					}
//				}
//				else if (inst instanceof SSAArrayReferenceInstruction)
//				{
//					SSAArrayReferenceInstruction arrayRefInst = (SSAArrayReferenceInstruction) inst;
//					int arrayRef = arrayRefInst.getArrayRef();
//					String typeclassname =  method.getDeclaringClass().getName().toString();
//					String instSig =typeclassname.substring(1)+":"+sourceLineNum;
//
//					PointerKey key = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, arrayRef);
//					OrdinalSet<InstanceKey> instances = pointerAnalysis.getPointsToSet(key);
//					logArrayAccess3(inst, sourceLineNum, instSig, new_trace, n, key, instances);
//
//				}else if (inst instanceof SSAAbstractInvokeInstruction){
//
//					CallSiteReference csr = ((SSAAbstractInvokeInstruction)inst).getCallSite();
//					MethodReference mr = csr.getDeclaredTarget();
//					//if (AnalysisUtils.implementsRunnableInterface(iclass) || AnalysisUtils.extendsThreadClass(iclass))
//					{
//						com.ibm.wala.classLoader.IMethod imethod = callGraph.getClassHierarchy().resolveMethod(mr);
//						if(imethod!=null){
//							String sig = imethod.getSignature();
//							//System.out.println("Invoke Inst: "+sig);
//							if(sig.equals("java.lang.Thread.start()V")){
//
//								PointerKey key = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, ((SSAAbstractInvokeInstruction) inst).getReceiver());
//								OrdinalSet<InstanceKey> instances = pointerAnalysis.getPointsToSet(key);
//								for(InstanceKey ins: instances){
//									TypeName name = ins.getConcreteType().getName();
//									CGNode node = threadSigNodeMap.get(name);
//									HashSet<String> threadReceivers = new HashSet<>();
//									//FIXME: BUG
//									if(node==null){
//										//TODO: find out which runnable object -- need data flow analysis
//										int param = ((SSAAbstractInvokeInstruction)inst).getUse(0);
//										node = handleRunnable(ins,threadReceivers, param, n);
//										if(node==null){
//											System.err.println("ERROR: starting new thread: "+ name);
//											continue;
//										}
//										//threadreceiver?
//									}else{//get threadReceivers
//										//should be the hashcode of the instancekey
//										threadReceivers.add(String.valueOf(ins.hashCode()));//SHOULD BE "this thread/runnable object"
//									}
//
//									//duplicate graph node id or existing node with trace?
//									Trace exist;
//									int tempid = node.getGraphNodeId();
//									if(tidpool.contains(tempid)){
//										if(oldkids.contains(tempid)){
//											int linenum = oldkid_line_map.get(tempid);
//											if(linenum != sourceLineNum){//changed => new thread
//												exist = null;
//												AstCGNode2 threadNode = new AstCGNode2(imethod, node.getContext());
//												int threadID = ++maxGraphNodeID;
//												threadNode.setGraphNodeId(threadID);
//												threadNode.setCGNode(node);
//												threadNode.setIR(node.getIR());
//												node = threadNode;
//												dupkids.add(tempid);
//											}else{
//												exist = shb.getTrace(node);
//											}
//											oldkids.remove(oldkids.indexOf(tempid));
//										}else{//new thread
//											exist = null;
//											AstCGNode2 threadNode = new AstCGNode2(imethod, node.getContext());
//											int threadID = ++maxGraphNodeID;
//											threadNode.setGraphNodeId(threadID);
//											threadNode.setCGNode(node);
//											threadNode.setIR(node.getIR());
////												newRunTargets.put(threadNode, node);
//											node = threadNode;
//										}
//									}else{//new thread
//										exist = shb.getTrace(node);
//									}
//									if(exist == null){//new threadnode
//										threadNodes.add(node);
//									}
//									int tid_child = node.getGraphNodeId();
//									tidpool.add(tid_child);
//									//add node to trace
//									StartNode startNode = new StartNode(curTID, tid_child, n, node, sourceLineNum);
//									new_trace.addS(startNode, inst, tid_child);
////									inst_start_map.put(node, startNode);
//									mapOfStartNode.put(tid_child, startNode);
//									StartNode pstartnode = mapOfStartNode.get(curTID);
//									if(pstartnode == null){
//										if(mainEntryNodes.contains(n)){
//											pstartnode = new StartNode(-1, curTID, n, node, sourceLineNum);
//											mapOfStartNode.put(curTID, pstartnode);
//										}else{//thread/runnable
//											pstartnode = new StartNode(curTID, tid_child, n, node,sourceLineNum);
//											mapOfStartNode.put(tid_child, pstartnode);
//										}
//									}
//									pstartnode.addChild(tid_child);
//									shb.addEdge(startNode, node);
//
//									//put to tid -> curreceivers map
//									tid2Receivers.put(node.getGraphNodeId(), threadReceivers);
//									//TODO: check if it is in a simple loop
//									boolean isInLoop = isInLoop(n,inst);
//
//									if(isInLoop){
////										AstCGNode2 node2 = new AstCGNode2(node.getMethod(),node.getContext());
////										threadNodes.add(node2);
////										newRunTargets.put(node2, node);
////										inst_start_map.put(node2, startNode);
//										AstCGNode2 node2 = n_loopn_map.get(node);
//										int newID;
//										if(node2 == null){
//											node2 = new AstCGNode2(node.getMethod(),node.getContext());
//											newID = ++maxGraphNodeID;
//											node2.setGraphNodeId(newID);
//											node2.setIR(node.getIR());
//											node2.setCGNode(node);
//											threadNodes.add(node2);
//										}else{
//											newID = node2.getGraphNodeId();//astCGNode_ntid_map.get(node);
//											if(oldkids.contains(newID)){
//												oldkids.remove(oldkids.indexOf(newID));
//											}
//										}
////										int curTID2 = tids.get(idxOfTid);
////										idxOfTid++;
//										tidpool.add(newID);
//										StartNode duplicate = new StartNode(curTID, newID, n, node2, sourceLineNum);
//										new_trace.addS(duplicate, inst, newID);//thread id +1
//										mapOfStartNode.put(newID, duplicate);
//										mapOfStartNode.get(curTID).addChild(newID);
//										shb.addEdge(duplicate, node2);
//
////										node2.setGraphNodeId(newID);
////										node2.setIR(node.getIR());
////										node2.setCGNode(node);
//
//										//need to change thread receiver id as well
//										Set<String> threadReceivers2 = new HashSet();
//										for(String id: threadReceivers){
//											threadReceivers2.add(id+"X");//"X" as the marker
//										}
//										//put to tid -> curreceivers map
//										tid2Receivers.put(newID, threadReceivers2);
//									}
//								}
//								//find loops in this method!!
//								//node.getIR().getControlFlowGraph();
//								hasSyncBetween = true;
//							}else if(sig.equals("java.lang.Thread.join()V")){
//								PointerKey key = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, ((SSAAbstractInvokeInstruction) inst).getReceiver());
//								OrdinalSet<InstanceKey> instances = pointerAnalysis.getPointsToSet(key);
//								for(InstanceKey ins: instances){
//									TypeName name = ins.getConcreteType().getName();
//									CGNode node = threadSigNodeMap.get(name);
//									//threadNodes.add(node);
//
//									HashSet<String> threadReceivers = new HashSet();
//									if(node==null){//could be a runnable class
//										int param = ((SSAAbstractInvokeInstruction)inst).getUse(0);
//										node = handleRunnable(ins,threadReceivers,param, n);
//										if(node==null){
//											System.err.println("ERROR: joining parent thread: "+ name);
//											continue;
//										}
//									}
//
//									//add node to trace
//									int tid_child =node.getGraphNodeId();
//									JoinNode jNode = new JoinNode(curTID, tid_child, n, node, sourceLineNum);
//									new_trace.addJ(jNode, inst);
//									JoinNode pjJoinNode = mapOfJoinNode.get(curTID);
//									if(pjJoinNode == null){//??
//										if(mainEntryNodes.contains(n)){
//											pjJoinNode = new JoinNode(-1, curTID, n, node, sourceLineNum);
//											mapOfJoinNode.put(curTID, pjJoinNode);
//										}else{
//											pjJoinNode = new JoinNode(curTID, tid_child, n, node, sourceLineNum);
//											mapOfJoinNode.put(tid_child, pjJoinNode);
//										}
//									}
//									shb.addBackEdge(node, jNode);
//
//									boolean isInLoop = isInLoop(n,inst);
//									if(isInLoop){
//										AstCGNode2 node2 = n_loopn_map.get(node);
//										//threadNodes.add(node2);
//										int newID = node2.getGraphNodeId();
////										idxOfTid--;
////										int curTID2 = tids.get(idxOfTid);
//										JoinNode jNode2 = new JoinNode(curTID,newID,n,node2, sourceLineNum);
//										new_trace.addJ(jNode2, inst);//thread id +1
//										mapOfJoinNode.put(newID, jNode2);
//										shb.addBackEdge(node2, jNode2);
////										node2.setGraphNodeId(newID);
////										node2.setIR(node.getIR());
////										node2.setCGNode(node);
//									}
//								}
//								hasSyncBetween = true;
//							}
//							else
//							{
//								//other method calls
//								//save current curReceivers
//								Set<String> curReceivers_pre = curReceivers;
//								//process NEW method call
//								Set<CGNode> set = new HashSet<>();
//								if(n instanceof AstCGNode2){
//									set = callGraph.getPossibleTargets(((AstCGNode2)n).getCGNode(), csr);//newRunTargets.get(n)
//								}else{
//									set = callGraph.getPossibleTargets(n, csr);
//								}
//								for(CGNode node: set){
//									if(AnalysisUtils.isApplicationClass(node.getMethod().getDeclaringClass())){
//										//static method call
//										if(node.getMethod().isStatic()){
//											//omit the pointer-lock map
//											//set current receivers to null
//											curReceivers = null;
//											//use classname as lock obj
//											String typeclassname =  n.getMethod().getDeclaringClass().getName().toString();
//											String instSig =typeclassname.substring(1)+":"+sourceLineNum;
//											String lock = node.getMethod().getDeclaringClass().getName().toString();
//											//take out records
//											HashSet<DLockNode> currentNodes = threadLockNodes.get(curTID);
//											if(currentNodes==null){
//												currentNodes = new HashSet<DLockNode>();
//												threadLockNodes.put(curTID,currentNodes);
//											}
//											ArrayList<DLLockPair> dLLockPairs = threadDLLockPairs.get(curTID);
//											if(dLLockPairs==null){
//												dLLockPairs = new ArrayList<DLLockPair>();
//												threadDLLockPairs.put(curTID, dLLockPairs);
//											}
//											DLockNode will = null;
//											//if synchronized method, add lock/unlock
//											if(node.getMethod().isSynchronized()){
//												// for deadlock
//												will = new DLockNode(curTID,instSig, sourceLineNum, null, null, n, inst, file);
//												will.addLockSig(lock);
//												for (DLockNode exist : currentNodes) {
//													dLLockPairs.add(new DLLockPair(exist, will));
//												}
//												new_trace.add(will);
////												addToThreadSyncNodes(will);
//												threadLockNodes.get(curTID).add(will);
//												interested_l.add(will);
//											}
//											MethodNode m = new MethodNode(n, node, curTID, sourceLineNum);
//											new_trace.add(m);
//											//												idx.add(trace.indexOf(m));
//											Trace subTrace0 = shb.getTrace(node);
//											if(subTrace0 == null){
//												subTrace0 = traverseNodePN(node);
//											}else{
//												//let curtrace edges include new tids
//												shb.includeTidForKidTraces(node, curTID);
//											}
////											subTrace0.includeTids(curTrace.getTraceTids());
//											shb.addEdge(m, node);
//											//
//											if(node.getMethod().isSynchronized()){
//												DUnlockNode unlock = new DUnlockNode(curTID, instSig, sourceLineNum, null, null, n, sourceLineNum);
//												unlock.addLockSig(lock);
//												//lock engine
//												new_trace.addLockPair(new LockPair(will, unlock));
//												//remove
//												new_trace.add(unlock);
////												addToThreadSyncNodes(unlock);
//												threadLockNodes.get(curTID).remove(will);
//											}
//										}else{
//											//instance
//											int objectValueNumber = inst.getUse(0);
//											PointerKey objectPointer = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, objectValueNumber);
//											OrdinalSet<InstanceKey> lockedObjects = pointerAnalysis.getPointsToSet(objectPointer);
//
//											//												HashSet<DLockNode> wills = new HashSet<>();
//											DLockNode will = null;
//											if(lockedObjects.size()>0){//must be larger than 0
//												curReceivers = new HashSet<>();
//												//take out records
//												HashSet<DLockNode> currentNodes = threadLockNodes.get(curTID);
//												if(currentNodes==null){
//													currentNodes = new HashSet<DLockNode>();
//													threadLockNodes.put(curTID,currentNodes);
//												}
//												ArrayList<DLLockPair> dLLockPairs = threadDLLockPairs.get(curTID);
//												if(dLLockPairs==null){
//													dLLockPairs = new ArrayList<DLLockPair>();
//													threadDLLockPairs.put(curTID, dLLockPairs);
//												}
//												//start to record new locks
//												if(node.getMethod().isSynchronized()){
//													String typeclassname = n.getMethod().getDeclaringClass().getName().toString();
//													String instSig = typeclassname.substring(1)+":"+sourceLineNum;
//													will = new DLockNode(curTID,instSig, sourceLineNum, objectPointer, lockedObjects, n, inst);
//													for (InstanceKey key : lockedObjects) {
//														String lock = key.getConcreteType().getName()+"."+key.hashCode();
//														will.addLockSig(lock);
//													}
//													// for deadlock
//													for (DLockNode exist : currentNodes) {
//														dLLockPairs.add(new DLLockPair(exist, will));
//													}
//													//														wills.add(will);
//													//for race
//													new_trace.add(will);
////													addToThreadSyncNodes(will);
//													threadLockNodes.get(curTID).add(will);
//													interested_l.add(will);
//													//for pointer-lock map
//													HashSet<SyncNode> ls = pointer_lmap.get(objectPointer);
//													if(ls == null){
//														ls = new HashSet<>();
//														ls.add(will);
//														pointer_lmap.put(objectPointer, ls);
//													}else{
//														ls.add(will);
//													}
//												}
//
//												//configuration
////												int K_obj_sensitive = 0;//0 means non-object sensitive
////												for (InstanceKey instanceKey : lockedObjects) {
////													//add receiver
////													if(K_obj_sensitive>0&&curReceivers_pre!=null){
////														for(String instance_pre: curReceivers_pre){
////															String temmStr = instance_pre;
////															String curObject = String.valueOf(instanceKey.hashCode());
////															//find the last Y or not
////															int indexY = instance_pre.lastIndexOf("Y");
////															if(indexY>-1)
////																temmStr = instance_pre.substring(indexY);
////															//object sensitivity is memory-demanding -- limit it to 2
////															//count number of Ys
////															int Kount = temmStr.length() - temmStr.replaceAll("Y", "").length();
////															if(Kount<=K_obj_sensitive
////																	&&!temmStr.equals(curObject))//-- limit it to 2
////																curReceivers.add(instance_pre+"Y"+curObject);
////														}
////													}else
////														curReceivers.add(String.valueOf(instanceKey.hashCode()));
////												}
//											}
//											//
//											MethodNode m = new MethodNode(n, node, curTID, sourceLineNum);
//											new_trace.add(m);
//											//
//											Trace subTrace1 = shb.getTrace(node);
//											if(subTrace1 == null){
//												subTrace1 = traverseNodePN(node);
//											}else{
//												//let curtrace edges include new tids
//												shb.includeTidForKidTraces(node, curTID);
//											}
////											subTrace1.includeTids(curTrace.getTraceTids());
//											shb.addEdge(m, node);
//											//
//											if(lockedObjects.size() > 0){
//												if(node.getMethod().isSynchronized()){
//													String typeclassname =  node.getMethod().getDeclaringClass().getName().toString();
//													String instSig =typeclassname.substring(1)+":"+sourceLineNum;
//													DUnlockNode unlock = new DUnlockNode(curTID, instSig, sourceLineNum, objectPointer, lockedObjects, n, sourceLineNum);
//													if(lockedObjects.size() == 1){
//														LockPair lockPair = new LockPair(will, unlock);
//														new_trace.addLockPair(lockPair);
//													}
//													for (InstanceKey instanceKey : lockedObjects) {
//														String lock = instanceKey.getConcreteType().getName()+"."+instanceKey.hashCode();
//														unlock.addLockSig(lock);
//														//													lockEngine.add(lock, curTID, lockPair);
//													}
//
//													//for race
//													new_trace.add(unlock);
//													//												addToThreadSyncNodes(unlock);
//													// for deadlock
//													threadLockNodes.get(curTID).remove(will);
//												}
//											}
//										}
//									}
//									curReceivers = curReceivers_pre;
//								}
//							}
//						}
//					}
//				}
//				else if(inst instanceof SSAMonitorInstruction)
//				{
//					//lock node: GID, TID, LockID
//					SSAMonitorInstruction monitorInstruction = ((SSAMonitorInstruction) inst);
//					int lockValueNumber = monitorInstruction.getRef();
//
//					PointerKey lockPointer = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, lockValueNumber);
//					OrdinalSet<InstanceKey> lockObjects = pointerAnalysis.getPointsToSet(lockPointer);
//					// for deadlock
//					String typeclassname =  n.getMethod().getDeclaringClass().getName().toString();
//					String instSig =typeclassname.substring(1)+":"+sourceLineNum;
//					DLockNode will = null;
//					DUnlockNode next = null;
//					//take out record
//					HashSet<DLockNode> currentNodes = threadLockNodes.get(curTID);
//					if(currentNodes==null){
//						currentNodes = new HashSet<DLockNode>();
//						threadLockNodes.put(curTID,currentNodes);
//					}
//					ArrayList<DLLockPair> dlpairs = threadDLLockPairs.get(curTID);
//					if(dlpairs==null){
//						dlpairs = new ArrayList<DLLockPair>();
//						threadDLLockPairs.put(curTID, dlpairs);
//					}
//					for (InstanceKey instanceKey : lockObjects) {
//						String lock = instanceKey.getConcreteType().getName()+"."+instanceKey.hashCode();
//						if(((SSAMonitorInstruction) inst).isMonitorEnter()){
//							will = new DLockNode(curTID, instSig, sourceLineNum, lockPointer, lockObjects, n, inst);
//							will.addLockSig(lock);
//						}else{
//							next = new DUnlockNode(curTID, instSig, sourceLineNum, lockPointer, lockObjects, n, sourceLineNum);
//							next.addLockSig(lock);
//							//lock engine
//							for (Iterator iterator = currentNodes.iterator(); iterator.hasNext();) {
//								DLockNode dLockNode = (DLockNode) iterator.next();
////								lockEngine.add(lock, curTID, new LockPair(dLockNode, next));
//								if (dLockNode.getInstSig().equals(instSig)) {//maybe compare pointer?
//									will = dLockNode;
//									break;
//								}
//							}
//						}
//					}
//
//					if(((SSAMonitorInstruction) inst).isMonitorEnter()){
//						if(will != null){
//							for (DLockNode exist : currentNodes) {
//								dlpairs.add(new DLLockPair(exist, will));
//							}
//							new_trace.add(will);
//							threadLockNodes.get(curTID).add(will);
//							interested_l.add(will);
//							//						addToThreadSyncNodes(will);
//							//for pointer-lock map
//							HashSet<SyncNode> ls = pointer_lmap.get(lockPointer);
//							if(ls == null){
//								ls = new HashSet<>();
//								ls.add(will);
//								pointer_lmap.put(lockPointer, ls);
//							}else{
//								ls.add(will);
//							}
//						}
//					}else {//monitor exit
//						if(will != null){
//							new_trace.add(next);
//							if(lockObjects.size() == 1){
//								new_trace.addLockPair(new LockPair(will, next));
//							}
////						addToThreadSyncNodes(next);
//							threadLockNodes.get(curTID).remove(will);
//						}
//					}
//					hasSyncBetween = true;
//				}
//			}
//		}
//
//		for (int tid : tids) {
//			if(tid != curTID){
//				traverseNodePNInc2(n, new_trace, tid);
//			}
//		}
//
//		//if has new threadnode; traverse
//		while(!threadNodes.isEmpty()){
//			CGNode newnode = threadNodes.removeFirst();
//			int newID = newnode.getGraphNodeId();
//			if(mapOfStartNode.get(newID) == null){
//				//no such node, should be created and added before, should not be??
//			}
//			curTID = newID;
//			hasSyncBetween = false;
//			traverseNodePN(newnode); //path insensitive
//		}
//		//if old kids has left => the thread has been removed
//		if(oldkids.size() > 0){
//			for (int oldkid : oldkids) {
//				mapOfStartNode.remove(oldkid);
//				tidpool.remove(oldkid);
//				shb.removeTidFromALlTraces(n, oldkid);
//				threadDLLockPairs.remove(oldkid);
//			}
//		}
//		if(dupkids.size() > 0){
//			for (int dupkid : dupkids) {
//				mapOfStartNode.remove(dupkid);
//				tidpool.remove(dupkid);
//				shb.removeTidFromALlTraces(n, dupkid);
//				threadDLLockPairs.remove(dupkid);
//			}
//		}
//		return new_trace;
//	}



	private HashSet<SSAInstruction> InstInsideCatchBlock(SSACFG cfg) {
		HashSet<SSAInstruction> catchinsts = new HashSet<>();
		for(int i=0; i<=cfg.getMaxNumber(); i++){
			BasicBlock block = cfg.getBasicBlock(i);
			if(block.isCatchBlock()){
				List<SSAInstruction> insts = block.getAllInstructions();
				catchinsts.addAll(insts);
				Iterator<ISSABasicBlock> succss = cfg.getSuccNodes(block);
				while(succss.hasNext()){
					BasicBlock succ = (BasicBlock) succss.next();
					List<SSAInstruction> insts2 = succ.getAllInstructions();
					for (SSAInstruction inst2 : insts2) {
						if(inst2.toString().contains("start()V")
								||inst2.toString().contains("join()V")){
							continue;
						}
						catchinsts.add(inst2);
					}
				}
			}
		}
		return catchinsts;
	}



	private void traverseNodePNInc2(CGNode n, Trace curTrace, int localtid) {
		// for lockpairs
		if(n.getIR() == null)
			return;
		stidpool.add(localtid);
		ArrayList<Integer> oldkids = new ArrayList<>();
		oldkids.addAll(curTrace.getOldKids());
		HashMap<Integer, Integer> oldkid_line_map = new HashMap<>();
		oldkid_line_map.putAll(curTrace.getOldkidsMap());
		ArrayList<Integer> dupkids = new ArrayList<>();

		//let curtrace edges include new tids
		HashSet<SHBEdge> edges = shb.getOutGoingEdgesOf(n);
		for (SHBEdge edge : edges) {
			edge.includeTid(localtid);
		}
		//start traverse inst
		SSACFG cfg = n.getIR().getControlFlowGraph();
		//		System.out.println(ssacfg.toString());
		HashSet<SSAInstruction> catchinsts = InstInsideCatchBlock(cfg);

		SSAInstruction[] insts = n.getIR().getInstructions();
		for(int i=0; i<insts.length; i++){
			SSAInstruction inst = insts[i];
			if(inst!=null){
				if(isdeleting){
					if(removeInst.equals(inst))
						continue;
				}
				if(catchinsts.contains(inst)){
					continue;
				}
				IMethod method = n.getMethod() ;
				int sourceLineNum = 0;
				IFile file = null;
				try{//get source code line number of this inst
					if(n.getIR().getMethod() instanceof IBytecodeMethod){
						int bytecodeindex = ((IBytecodeMethod) n.getIR().getMethod()).getBytecodeIndex(inst.iindex);
						sourceLineNum = (int)n.getIR().getMethod().getLineNumber(bytecodeindex);
					}else{
						SourcePosition position = n.getMethod().getSourcePosition(inst.iindex);
						sourceLineNum = position.getFirstLine();//.getLastLine();
						if(position instanceof JdtPosition){
							file = ((JdtPosition) position).getEclipseFile();
						}
					}
				}catch(Exception e){
					e.printStackTrace();
				}

				if(inst instanceof SSAAbstractInvokeInstruction){
					CallSiteReference csr = ((SSAAbstractInvokeInstruction)inst).getCallSite();
					MethodReference mr = csr.getDeclaredTarget();
					com.ibm.wala.classLoader.IMethod imethod = callGraph.getClassHierarchy().resolveMethod(mr);
					if(imethod!=null){
						String sig = imethod.getSignature();
						//System.out.println("Invoke Inst: "+sig);
						if(sig.equals("java.lang.Thread.start()V")){
							PointerKey key = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, ((SSAAbstractInvokeInstruction) inst).getReceiver());
							OrdinalSet<InstanceKey> instances = pointerAnalysis.getPointsToSet(key);
							for(InstanceKey ins: instances){
								TypeName name = ins.getConcreteType().getName();
								CGNode node = threadSigNodeMap.get(name);
//								HashSet<String> threadReceivers = new HashSet<>();
								if(node==null){
									//TODO: find out which runnable object -- need data flow analysis
									int param = ((SSAAbstractInvokeInstruction)inst).getUse(0);
									if(sig.contains("java.util.concurrent") && sig.contains("execute")){
										param = ((SSAAbstractInvokeInstruction)inst).getUse(1);
									}
									node = handleRunnable(ins, param, n);
									if(node==null){
//										System.err.println("ERROR: starting new thread: "+ name);
										continue;
									}
									//threadreceiver?
								}else{//get threadReceivers
									//should be the hashcode of the instancekey
//									threadReceivers.add(String.valueOf(ins.hashCode()));//SHOULD BE "this thread/runnable object"
								}

								//duplicate graph node id or existing node with trace?
								Trace exist;
								int tempid = node.getGraphNodeId();
								if(stidpool.contains(tempid)){
									if(oldkids.contains(tempid)){
										int linenum = oldkid_line_map.get(tempid);
										if(linenum != sourceLineNum){//new thread
											exist = null;
											AstCGNode2 threadNode = new AstCGNode2(imethod, node.getContext());
											int threadID = ++maxGraphNodeID;
											threadNode.setGraphNodeId(threadID);
											threadNode.setCGNode(node);
											threadNode.setIR(node.getIR());
											node = threadNode;
											dupkids.add(tempid);
										}else{
											exist = shb.getTrace(node);
										}
										oldkids.remove(oldkids.indexOf(tempid));
									}else{//new thread
										exist = null;
										AstCGNode2 threadNode = new AstCGNode2(imethod, node.getContext());
										int threadID = ++maxGraphNodeID;
										threadNode.setGraphNodeId(threadID);
										threadNode.setCGNode(node);
										threadNode.setIR(node.getIR());
//											newRunTargets.put(threadNode, node);
										node = threadNode;
									}
								}else{
									exist = shb.getTrace(node);
								}
								if(exist == null){//new threadnode
									threadNodes.add(node);
								}else if(oldkids.contains(tempid)){
									oldkids.remove(oldkids.indexOf(tempid));
								}
								int tid_child = node.getGraphNodeId();
								stidpool.add(tid_child);
								//add node to trace
								StartNode startNode = new StartNode(localtid, tid_child, n, node, sourceLineNum, file);
								curTrace.add2S(startNode, inst, tid_child);
//								inst_start_map.put(node, startNode);
								mapOfStartNode.put(tid_child, startNode);
								StartNode pstartnode = mapOfStartNode.get(localtid);
								if(pstartnode == null){
									if(mainEntryNodes.contains(n)){
										pstartnode = new StartNode(-1, localtid, n, node, sourceLineNum, file);
										mapOfStartNode.put(localtid, pstartnode);
									}else{//thread/runnable
										pstartnode = new StartNode(localtid, tid_child, n, node, sourceLineNum, file);
										mapOfStartNode.put(tid_child, pstartnode);
									}
								}
								pstartnode.addChild(tid_child);
								shb.addEdge(startNode, node);

								//put to tid -> curreceivers map
//								tid2Receivers.put(node.getGraphNodeId(), threadReceivers);
								//TODO: check if it is in a simple loop
								boolean isInLoop = isInLoop(n,inst);

								if(isInLoop){
//									AstCGNode2 node2 = new AstCGNode2(node.getMethod(),node.getContext());
//									threadNodes.add(node2);
//									newRunTargets.put(node2, node);
//									inst_start_map.put(node2, startNode);
									AstCGNode2 node2 = n_loopn_map.get(node);
									int newID;
									if(node2 == null){
										node2 = new AstCGNode2(node.getMethod(), node.getContext());
										newID = ++maxGraphNodeID;
										node2.setGraphNodeId(newID);
										node2.setIR(node.getIR());
										node2.setCGNode(node);
										threadNodes.add(node2);
									}else{
										newID = node2.getGraphNodeId();//astCGNode_ntid_map.get(node);
										if(oldkids.contains(newID)){
											oldkids.remove(oldkids.indexOf(newID));
										}
									}
//									int curTID2 = tids.get(idxOfTid);
//									idxOfTid++;
									stidpool.add(newID);
									StartNode duplicate = new StartNode(localtid, newID, n, node2, sourceLineNum, file);
									curTrace.add2S(duplicate, inst, newID);//thread id +1
									mapOfStartNode.put(newID, duplicate);
									mapOfStartNode.get(localtid).addChild(newID);
									shb.addEdge(duplicate, node2);

//									node2.setGraphNodeId(newID);
//									node2.setIR(node.getIR());
//									node2.setCGNode(node);

									//need to change thread receiver id as well
//									Set<String> threadReceivers2 = new HashSet();
//									for(String id: threadReceivers){
//										threadReceivers2.add(id+"X");//"X" as the marker
//									}
//									//put to tid -> curreceivers map
//									tid2Receivers.put(newID, threadReceivers2);
								}
							}
							//find loops in this method!!
							//node.getIR().getControlFlowGraph();
							hasSyncBetween = true;
						}
						else if(sig.equals("java.lang.Thread.join()V")){
							PointerKey key = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, ((SSAAbstractInvokeInstruction) inst).getReceiver());
							OrdinalSet<InstanceKey> instances = pointerAnalysis.getPointsToSet(key);
							for(InstanceKey ins: instances){
								TypeName name = ins.getConcreteType().getName();
								CGNode node = threadSigNodeMap.get(name);
//								HashSet<String> threadReceivers = new HashSet();
								if(node==null){//could be a runnable class
									int param = ((SSAAbstractInvokeInstruction)inst).getUse(0);
									//Executors and ThreadPoolExecutor
									node = handleRunnable(ins,param, n);
									if(node==null){
//										System.err.println("ERROR: joining parent thread: "+ name);
										continue;
									}
								}
								//add joinnode to trace
								int tid_child =node.getGraphNodeId();
								if(mapOfJoinNode.containsKey(tid_child)){
									//dup run nodes
									CGNode threadNode = dupStartJoinTidMap.get(tid_child);
									if(threadNode != null){
										tid_child = threadNode.getGraphNodeId();
										node = threadNode;
									}
								}
								JoinNode jNode = new JoinNode(curTID, tid_child, n, node, sourceLineNum, file);
								curTrace.add2J(jNode, inst, tid_child);
								mapOfJoinNode.put(tid_child, jNode);
								shb.addBackEdge(node, jNode);

								boolean isInLoop = isInLoop(n,inst);
								if(isInLoop){
									AstCGNode2 node2 = n_loopn_map.get(node);
									if(node2 == null){
										node2 = dupStartJoinTidMap.get(tid_child);
										if(node2 == null){
											System.err.println("Null node obtain from n_loopn_map. ");
											continue;
										}
									}
									//threadNodes.add(node2);
									int newID = node2.getGraphNodeId();
//									int curTID2 = tids.get(idxOfTid);
									JoinNode jNode2 = new JoinNode(curTID,newID,n,node2, sourceLineNum, file);
									curTrace.addJ(jNode2, inst);//thread id +1
									mapOfJoinNode.put(newID, jNode2);
									shb.addBackEdge(node2, jNode2);
								}
								hasSyncBetween = true;
							}
						}else if(sig.equals("java.lang.Thread.<init>(Ljava/lang/Runnable;)V")){
							//for new Thread(new Runnable)
							int use0 = inst.getUse(0);
//							int use1 = inst.getUse(1);
							threadInits.put(use0, (SSAAbstractInvokeInstruction)inst);
						}else{//other syncs
//							Set<String> curReceivers_pre = curReceivers;
							//process NEW method call
							Set<CGNode> set = new HashSet<>();
							if(n instanceof AstCGNode2){
								set = callGraph.getPossibleTargets(((AstCGNode2)n).getCGNode(), csr);//newRunTargets.get(n)
							}else{
								set = callGraph.getPossibleTargets(n, csr);
							}
							for(CGNode node: set){
								if(AnalysisUtils.isApplicationClass(node.getMethod().getDeclaringClass())){
									//static method call
									if(node.getMethod().isStatic()){
										//omit the pointer-lock map
										//set current receivers to null
//										curReceivers = null;
										//use classname as lock obj
										String typeclassname =  n.getMethod().getDeclaringClass().getName().toString();
										String instSig =typeclassname.substring(1)+":"+sourceLineNum;
										String lock = node.getMethod().getDeclaringClass().getName().toString();
										//take out records
										HashSet<DLockNode> currentNodes = threadLockNodes.get(localtid);
										if(currentNodes==null){
											currentNodes = new HashSet<DLockNode>();
											threadLockNodes.put(localtid,currentNodes);
										}
										ArrayList<DLLockPair> dLLockPairs = threadDLLockPairs.get(localtid);
										if(dLLockPairs==null){
											dLLockPairs = new ArrayList<DLLockPair>();
											threadDLLockPairs.put(localtid, dLLockPairs);
										}
										DLockNode will = null;
										//if synchronized method, add lock/unlock
										if(node.getMethod().isSynchronized()){
											// for deadlock
											will = new DLockNode(localtid, instSig, sourceLineNum, null, null, n, inst, file);
											will.addLockSig(lock);
											for (DLockNode exist : currentNodes) {
												dLLockPairs.add(new DLLockPair(exist, will));
											}
											//												curTrace.add(will);
											//												addToThreadSyncNodes(will);
											threadLockNodes.get(localtid).add(will);
											interested_l.add(will);
										}
										//											MethodNode m = new MethodNode(n, node, curTID);
										//											curTrace.add(m);
										Trace subTrace0 = shb.getTrace(node);
										if(subTrace0 == null){
											//should not be
											subTrace0 = traverseNodePN(node);
										}
										subTrace0.includeTid(localtid);
										//											shb.addEdge(m, node);
										//
										if(node.getMethod().isSynchronized()){
											//												DUnlockNode unlock = new DUnlockNode(curTID, instSig, sourceLineNum, null, null, n);
											//												unlock.addLockSig(lock);
											//remove
											//												curTrace.add(unlock);
											threadLockNodes.get(localtid).remove(will);
										}
									}else{
										//instance
										int objectValueNumber = inst.getUse(0);
										PointerKey objectPointer = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, objectValueNumber);
										OrdinalSet<InstanceKey> lockedObjects = pointerAnalysis.getPointsToSet(objectPointer);

										DLockNode will = null;
										if(lockedObjects.size()>0){//must be larger than 0
//											curReceivers = new HashSet<>();
											//take out records
											HashSet<DLockNode> currentNodes = threadLockNodes.get(localtid);
											if(currentNodes==null){
												currentNodes = new HashSet<DLockNode>();
												threadLockNodes.put(localtid,currentNodes);
											}
											ArrayList<DLLockPair> dLLockPairs = threadDLLockPairs.get(localtid);
											if(dLLockPairs==null){
												dLLockPairs = new ArrayList<DLLockPair>();
												threadDLLockPairs.put(localtid, dLLockPairs);
											}
											//start to record new locks
											if(node.getMethod().isSynchronized()){
												String typeclassname = n.getMethod().getDeclaringClass().getName().toString();
												String instSig = typeclassname.substring(1)+":"+sourceLineNum;
												will = new DLockNode(localtid,instSig, sourceLineNum, objectPointer, lockedObjects, n, inst, file);
												for (InstanceKey key : lockedObjects) {
													String lock = key.getConcreteType().getName()+"."+key.hashCode();
													will.addLockSig(lock);
												}
												// for deadlock
												for (DLockNode exist : currentNodes) {
													dLLockPairs.add(new DLLockPair(exist, will));
												}
												//for race
												//													curTrace.add(will);
												threadLockNodes.get(localtid).add(will);
												interested_l.add(will);
												//for pointer-lock map
												HashSet<SyncNode> ls = pointer_lmap.get(objectPointer);
												if(ls == null){
													ls = new HashSet<>();
													ls.add(will);
													pointer_lmap.put(objectPointer, ls);
												}else{
													ls.add(will);
												}
											}
												//															if(node.getMethod().isSynchronized()){
												//																isSync = true;
												//																String typeclassname =  node.getMethod().getDeclaringClass().getName().toString();
												//																String instSig = typeclassname.substring(1)+":"+sourceLineNum;
												//																String lock = instanceKey.getConcreteType().getName()+"."+instanceKey.hashCode();
												//																SSAInstruction createinst = findInitialInst(n, instanceKey);//?
												//																// for deadlock
												//																DLockNode will = new DLockNode(curTID,instSig, lock, sourceLineNum, createinst);
												//																for (DLockNode exist : currentNodes) {
												//																	dLLockPairs.add(new DLLockPair(exist, will));
												//																}
												//																wills.add(will);
												//																//for race
												//																curTrace.add(will);
												//															}
												//														addToThreadSyncNodes(wills);
												//														threadLockNodes.get(curTID).addAll(wills);
											//													//mark pointer
											//													pointer_traceidx_lmap.get(objectPointer).add(trace.size() -1);
											//
											//											MethodNode m = new MethodNode(node, curTID);
											//											curTrace.add(m);
											//
											//													idx.add(trace.indexOf(m));
											Trace subTrace1 = shb.getTrace(node);
											if(subTrace1 == null){
												//should not be
												subTrace1 = traverseNodePN(node);
											}
											subTrace1.includeTid(localtid);
											//											shb.addEdge(m, node);
											if(lockedObjects.size() > 0){
												if(node.getMethod().isSynchronized()){
													//														//mark pointer
													//														ArrayList<Integer> traceidx2 = pointer_traceidx_rwmap.get(objectPointer);
													//														if(traceidx2 == null){
													//															traceidx2 = new ArrayList<>();
													//															traceidx2.add(trace.size());
													//															pointer_traceidx_lmap.put(objectPointer, traceidx2);
													//														}else{
													//															pointer_traceidx_lmap.get(objectPointer).add(trace.size());
													//														}
													String typeclassname =  node.getMethod().getDeclaringClass().getName().toString();
													String instSig =typeclassname.substring(1)+":"+sourceLineNum;
													//												DUnlockNode unlock = new DUnlockNode(curTID, instSig, sourceLineNum, objectPointer, lockedObjects, n);
													//												LockPair lockPair = new LockPair(will, unlock);
													//												for (InstanceKey instanceKey : lockedObjects) {
													//													String lock = instanceKey.getConcreteType().getName()+"."+instanceKey.hashCode();
													//													unlock.addLockSig(lock);
													//													lockEngine.add(lock, curTID, lockPair);
													//												}
													//lock engine
													//														for (Iterator iterator = wills.iterator(); iterator.hasNext();) {
													//															DLockNode dLockNode = (DLockNode) iterator.next();
													//															lockEngine.add(lock, curTID, new LockPair(dLockNode, unlock));
													//														}
													//for race
													//												curTrace.add(unlock);
													//												addToThreadSyncNodes(unlock);
													// for deadlock
													threadLockNodes.get(localtid).remove(will);
													//for pointer-lock map
													//														HashSet<String> ls = pointer_lmap.get(objectPointer);
													//														if(ls == null){
													//															ls = unlock.getLockSig();
													//														}
													//														//mark pointer
													//														pointer_traceidx_lmap.get(objectPointer).add(trace.size() - 1);
												}
											}
										}
									}
								}
							}
						}
					}
				}else if(inst instanceof SSAMonitorInstruction){
					SSAMonitorInstruction monitorInstruction = ((SSAMonitorInstruction) inst);
					int lockValueNumber = monitorInstruction.getRef();

					PointerKey lockPointer =pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, lockValueNumber);
					OrdinalSet<InstanceKey> lockObjects = pointerAnalysis.getPointsToSet(lockPointer);
					//lockset solved in traversenodepninc
					// for deadlock
					String typeclassname =  n.getMethod().getDeclaringClass().getName().toString();
					String instSig =typeclassname.substring(1)+":"+sourceLineNum;
					DLockNode will = null;
					DUnlockNode next = null;
					//take our record
					HashSet<DLockNode> currentNodes = threadLockNodes.get(localtid);
					if(currentNodes==null){
						currentNodes = new HashSet<DLockNode>();
						threadLockNodes.put(localtid,currentNodes);
					}
					ArrayList<DLLockPair> dlpairs = threadDLLockPairs.get(localtid);
					if(dlpairs==null){
						dlpairs = new ArrayList<DLLockPair>();
						threadDLLockPairs.put(localtid, dlpairs);
					}
					for (InstanceKey instanceKey : lockObjects) {
						String lock = instanceKey.getConcreteType().getName()+"."+instanceKey.hashCode();
						//								SSAInstruction createinst = findInitialInst(n, instanceKey);
						if(((SSAMonitorInstruction) inst).isMonitorEnter()){
							will = new DLockNode(localtid, instSig, sourceLineNum, lockPointer, lockObjects, n, inst, file);
							will.addLockSig(lock);
						}else{
							next = new DUnlockNode(localtid, instSig, sourceLineNum, lockPointer, lockObjects, n, sourceLineNum);
							next.addLockSig(lock);
							for (Iterator iterator = currentNodes.iterator(); iterator.hasNext();) {
								DLockNode dLockNode = (DLockNode) iterator.next();
								if (dLockNode.getInstSig().equals(instSig)) {//maybe compare pointer?
									will = dLockNode;
									break;
								}
							}
						}
					}

					if(((SSAMonitorInstruction) inst).isMonitorEnter()){
						if(will != null){
							for (DLockNode exist : currentNodes) {
								dlpairs.add(new DLLockPair(exist, will));
							}
							//						curTrace.add(will);
							threadLockNodes.get(localtid).add(will);
							interested_l.add(will);
							//for pointer-lock map
							HashSet<SyncNode> ls = pointer_lmap.get(lockPointer);
							if(ls == null){
								ls = new HashSet<>();
								ls.add(will);
								pointer_lmap.put(lockPointer, ls);
							}else{
								ls.add(will);
							}
						}
					}else {//monitor exit
//						curTrace.add(next);
						if(will != null){
							threadLockNodes.get(localtid).remove(will);
						}
						//for pointer-lock map
						//								HashSet<String> ls = pointer_lmap.get(lockPointer);
						//								if(ls == null){
						//									ls = next.getLockSig();
						//								}
					}
				}
			}
		}
//		if(dupkids.size() > 0){
//			for (int dupkid : dupkids) {
//				mapOfStartNode.remove(dupkid);
//				stidpool.remove(dupkid);
//				shb.removeTidFromALlTraces(n, dupkid);
//			}
//		}
	}




	//store changed objsig from pta
	HashSet<String> interested_rw = new HashSet<String>();
	HashSet<DLockNode> interested_l = new HashSet<DLockNode>();
	//old objsig
	HashSet<String> removed_rw = new HashSet<String>();
	HashSet<DLockNode> removed_l = new HashSet<DLockNode>();

	private void updatePTA2(Set<CGNode> keys) {
		////// did not replace siglist in nodes
		HashSet<IVariable> changes = AbstractFixedPointSolver.changes;
		//find node from instSig
		for (IVariable v : changes) {
			PointsToSetVariable var = (PointsToSetVariable) v;
			PointerKey key = var.getPointerKey();
			OrdinalSet<InstanceKey> newobjects = pointerAnalysis.getPointsToSet(key);//new
			//interested wr
			HashSet<MemNode> rwnodes = pointer_rwmap.get(key);//old
			if(rwnodes == null){
				//should be new created pointer, update in traversal
			}else{
				for (MemNode node : rwnodes) {
					CGNode belonging = node.getBelonging();
					if(keys.contains(belonging)){
						continue;
					}
					//old sigs
					HashSet<String> old_sigs = node.getObjSig();
					String prefix = node.getPrefix();
					//new sigs
					HashSet<String> new_sigs = new HashSet<>();
					for (InstanceKey newkey : newobjects) {
						String new_sig = prefix + "." + String.valueOf(newkey.hashCode());
						new_sigs.add(new_sig);
					}
					//related tids
					Trace trace = shb.getTrace(belonging);
					if(trace == null){//the rwnode should be already deleted from pointer_rwmap??
						continue;
					}
					ArrayList<Integer> tids = trace.getTraceTids();
					//replace the sigs in its trace
					if (node instanceof ReadNode) {
						trace.replaceRSigMap(old_sigs, new_sigs, (ReadNode)node);
					}else{//write node
						trace.replaceWSigMap(old_sigs, new_sigs, (WriteNode)node);
					}
					//old remove rw sig tid num map (global) 					//sig rw map
					removed_rw.addAll(old_sigs);
					for (String old : old_sigs) {
						if(node instanceof ReadNode){
							HashMap<Integer, Integer> map = rsig_tid_num_map.get(old);
							if(map == null)//should not be null??
								continue;
							HashSet<Integer> removedTids = new HashSet<>();
							for (Integer tid : map.keySet()) {
								if(tids.contains(tid)){
									int num = map.get(tid);
									num--;
									if(num == 0){
										removedTids.add(tid);
									}else{
										map.put(tid, num);
									}
								}
							}
							for (Integer tid : removedTids) {
								map.remove(tid);
							}
							if(map.keySet().size() == 0){//maybe too conservartive
								sigReadNodes.remove(old);
								rsig_tid_num_map.remove(old);
							}
							HashSet<ReadNode> reads = sigReadNodes.get(old);
							if(reads != null){
								reads.remove(node);
							}
						}else{//write node
							HashMap<Integer, Integer> map = wsig_tid_num_map.get(old);
							if(map == null)//should not be null??
								continue;
							HashSet<Integer> removedTids = new HashSet<>();
							for (Integer tid : map.keySet()) {
								if(tids.contains(tid)){
									int num = map.get(tid);
									num--;
									if(num == 0){
										removedTids.add(tid);
									}else{
										map.put(tid, num);
									}
								}
							}
							for (Integer tid : removedTids) {
								map.remove(tid);
							}
							if(map.keySet().size() == 0){
								sigWriteNodes.remove(old);
								wsig_tid_num_map.remove(old);
							}
							HashSet<WriteNode> writes = sigWriteNodes.get(old);
							if(writes != null){
								writes.remove(node);
							}
						}
					}
					//new add rw sig tid num map (global) 					//sig rw map
					for (String new_sig : new_sigs) {
						if(node instanceof ReadNode){
							HashMap<Integer, Integer> map = rsig_tid_num_map.get(new_sig);
							if(map == null){
								map = new HashMap<Integer, Integer>();
								rsig_tid_num_map.put(new_sig, map);
							}
							for (Integer tid : tids) {
								if(map.keySet().contains(tid)){
									int num = map.get(tid);
									num++;
									map.put(tid, num);
								}else{
									map.put(tid, 1);
								}
								if(map.keySet().size() > 1){
									HashSet<ReadNode> reads = sigReadNodes.get(new_sig);
									if(reads == null){
										reads = new HashSet<>();
										sigReadNodes.put(new_sig, reads);
									}
									reads.add((ReadNode) node);
								}
							}
						}else{//write node
							HashMap<Integer, Integer> map = wsig_tid_num_map.get(new_sig);
							if(map == null){
								map = new HashMap<Integer, Integer>();
								wsig_tid_num_map.put(new_sig, map);
							}
							for (Integer tid : tids) {
								if(map.keySet().contains(tid)){
									int num = map.get(tid);
									num++;
									map.put(tid, num);
								}else{
									map.put(tid, 1);
								}
								if(map.keySet().size() > 1){
									HashSet<WriteNode> writes = sigWriteNodes.get(new_sig);
									if(writes == null){
										writes = new HashSet<>();
										sigWriteNodes.put(new_sig, writes);
									}
									writes.add((WriteNode) node);
								}
							}
						}
					}
					//traces??
					node.replaceObjSig(new_sigs);
					interested_rw.addAll(new_sigs);
				}
			}
			//interested locks
			HashSet<SyncNode> locknodes = pointer_lmap.get(key);//may need to add unlocknode
			if(locknodes == null){
				//should be new created pointer
			}else{
				for (SyncNode locknode : locknodes) {
					DLockNode lock = (DLockNode) locknode;
					//old sigs
					HashSet<String> old_sigs = lock.getLockSig();
//					removed_l.add(lock);
					String prefix = lock.getPrefix();
					//new sigs
					HashSet<String> new_sigs = new HashSet<>();
					for (InstanceKey instanceKey : newobjects) {
						String new_sig = prefix + "." + String.valueOf(instanceKey.hashCode());
						new_sigs.add(new_sig);
					}
					//replace
					lock.replaceLockSig(new_sigs);
					interested_l.add(lock);
					//threaddllockpair...no need
					//traces??
				}
			}
		}
	}

	private void updatePTA(Set<CGNode> keys) {
		////// did not replace siglist in nodes
		HashSet<IVariable> changes = AbstractFixedPointSolver.changes;
		//find node from instSig
		for (IVariable v : changes) {
			PointsToSetVariable var = (PointsToSetVariable) v;
			PointerKey key = var.getPointerKey();
			OrdinalSet<InstanceKey> newobjects = pointerAnalysis.getPointsToSet(key);//new
			//interested wr
			HashSet<MemNode> rwnodes = pointer_rwmap.get(key);//old
			if(rwnodes == null){
				//should be new created pointer, update in traversal
			}else{
				for (MemNode node : rwnodes) {
					CGNode belonging = node.getBelonging();
					if(keys.contains(belonging)){
						continue;
					}
					//old sigs
					HashSet<String> old_sigs = node.getObjSig();
					String prefix = node.getPrefix();
					//new sigs
					HashSet<String> new_sigs = new HashSet<>();
					for (InstanceKey newkey : newobjects) {
						String new_sig = prefix + "." + String.valueOf(newkey.hashCode());
						new_sigs.add(new_sig);
					}
					//related tids
					Trace trace = shb.getTrace(belonging);
					if(trace == null){//the rwnode should be already deleted from pointer_rwmap??
						continue;
					}
					ArrayList<Integer> tids = trace.getTraceTids();
					//replace the sigs in its trace
					HashSet<String> newAddedSig = null;
					if (node instanceof ReadNode) {
						newAddedSig = trace.replaceRSigMap(old_sigs, new_sigs, (ReadNode)node);
					}else{//write node
						newAddedSig = trace.replaceWSigMap(old_sigs, new_sigs, (WriteNode)node);
					}
					//old remove rw sig tid num map (global) 					//sig rw map
					removed_rw.addAll(old_sigs);
					for (String old : old_sigs) {
						if(node instanceof ReadNode){
							HashMap<String, ArrayList<ReadNode>> rsigMapping = trace.getRsigMapping();
							ArrayList<ReadNode> relatedReads = rsigMapping.get(old);
							if(relatedReads != null){
								if(relatedReads.size() == 0){
									//need to update in rsig_tid_num_map
									HashMap<Integer, Integer> map = rsig_tid_num_map.get(old);
									if(map == null)//should not be null??
										continue;
									HashSet<Integer> removedTids = new HashSet<>();
									for (Integer tid : map.keySet()) {
										if(tids.contains(tid)){
											int num = map.get(tid);
											num--;
											if(num == 0){
												removedTids.add(tid);
											}else{
												map.put(tid, num);
											}
										}
									}
									for (Integer tid : removedTids) {
										map.remove(tid);
									}
									if(map.keySet().size() == 0){//maybe too conservartive
										rsig_tid_num_map.remove(old);
									}
								}
							}
							HashSet<ReadNode> reads = sigReadNodes.get(old);
							if(reads != null){
								reads.remove((ReadNode)node);
								if(reads.size() == 0){
									sigReadNodes.remove(old);
								}
							}
						}else{//write node
							HashMap<String, ArrayList<WriteNode>> wsigMapping = trace.getWsigMapping();
							ArrayList<WriteNode> relatedWrites = wsigMapping.get(old);
							if(relatedWrites != null){
								if(relatedWrites.size() == 0){
									HashMap<Integer, Integer> map = wsig_tid_num_map.get(old);
									if(map == null)//should not be null??
										continue;
									HashSet<Integer> removedTids = new HashSet<>();
									for (Integer tid : map.keySet()) {
										if(tids.contains(tid)){
											int num = map.get(tid);
											num--;
											if(num == 0){
												removedTids.add(tid);
											}else{
												map.put(tid, num);
											}
										}
									}
									for (Integer tid : removedTids) {
										map.remove(tid);
									}
									if(map.keySet().size() == 0){
										wsig_tid_num_map.remove(old);
									}
								}
							}
							HashSet<WriteNode> writes = sigWriteNodes.get(old);
							if(writes != null){
								writes.remove((WriteNode) node);
								if(writes.size() == 0){
									sigWriteNodes.remove(old);
								}
							}
						}
					}
					//new add rw sig tid num map (global) 					//sig rw map
					for (String new_sig : newAddedSig) {
						if(node instanceof ReadNode){
							HashMap<Integer, Integer> map = rsig_tid_num_map.get(new_sig);
							if(map == null){
								map = new HashMap<Integer, Integer>();
								rsig_tid_num_map.put(new_sig, map);
							}
							for (Integer tid : tids) {
								if(map.keySet().contains(tid)){
									int num = map.get(tid);
									num++;
									map.put(tid, num);
								}else{
									map.put(tid, 1);
								}
								if(map.keySet().size() > 1){
									HashSet<ReadNode> reads = sigReadNodes.get(new_sig);
									if(reads == null){
										reads = new HashSet<>();
										sigReadNodes.put(new_sig, reads);
									}
									reads.add((ReadNode) node);
								}
							}
						}else{//write node
							HashMap<Integer, Integer> map = wsig_tid_num_map.get(new_sig);
							if(map == null){
								map = new HashMap<Integer, Integer>();
								wsig_tid_num_map.put(new_sig, map);
							}
							for (Integer tid : tids) {
								if(map.keySet().contains(tid)){
									int num = map.get(tid);
									num++;
									map.put(tid, num);
								}else{
									map.put(tid, 1);
								}
								if(map.keySet().size() > 1){
									HashSet<WriteNode> writes = sigWriteNodes.get(new_sig);
									if(writes == null){
										writes = new HashSet<>();
										sigWriteNodes.put(new_sig, writes);
									}
									writes.add((WriteNode) node);
								}
							}
						}
					}

					//traces??
					node.replaceObjSig(new_sigs);
					interested_rw.addAll(new_sigs);
				}
			}
			//interested locks
			HashSet<SyncNode> locknodes = pointer_lmap.get(key);//may need to add unlocknode
			if(locknodes == null){
				//should be new created pointer
			}else{
				for (SyncNode locknode : locknodes) {
					DLockNode lock = (DLockNode) locknode;
					//old sigs
					HashSet<String> old_sigs = lock.getLockSig();
//					removed_l.add(lock);
					String prefix = lock.getPrefix();
					//new sigs
					HashSet<String> new_sigs = new HashSet<>();
					for (InstanceKey instanceKey : newobjects) {
						String new_sig = prefix + "." + String.valueOf(instanceKey.hashCode());
						new_sigs.add(new_sig);
					}
					//replace
					lock.replaceLockSig(new_sigs);
					interested_l.add(lock);
					//threaddllockpair...no need
					//traces??
				}
			}
		}
	}

	public void detectBothBugsAgain(HashSet<CGNode> changedNodes, HashSet<CGNode> changedModifiers, long start_time, PrintStream ps) {
		//indicate if we need to check race/lock/both
		boolean lock = false;
		boolean race = false;
		//filter nodes
		for (CGNode node : changedNodes) {
//			DetailChanges details = changedNodes.node_change_mapping.get(node);
			if(hasThreads.get(node) != null){
				if(hasThreads.get(node) == true){
					lock = true;
					race = true;
					//look for the startnode
					Trace trace = shb.getTrace(node);
					for (INode inode : trace.getContent()) {
						if(inode instanceof StartNode){
							StartNode start = (StartNode) inode;
							CGNode target = start.getTarget();
							includeTraceToInterestRW(target);
							includeTraceToInterestL(target);
						}
					}

				}
			}
			if(hasLocks.get(node) != null){
				if(hasLocks.get(node) == true){
					lock = true;
					includeTraceToInterestRW(node);
				}
			}
		}
		if(interested_l.size() != 0){
			lock = true;
		}
		if(interested_rw.size() != 0){
			race = true;
		}
		//remove bugs related to interests
		removeBugsRelatedToInterests(changedNodes, changedModifiers);
		//start
		organizeThreadsRelations();
		//race
		if(race){
			recheckRace();
		}
		long race_end_time = System.currentTimeMillis();
		long incre_race_time = race_end_time - start_time;
		//lock
		if(lock){
			recheckLock();
		}
		long incre_dl_time = System.currentTimeMillis() - race_end_time;
		if(!PLUGIN){
			ps.print(incre_race_time+" "+incre_dl_time+" ");
		}

//		System.err.println("total size: " + bugs.size());
//
//		System.out.println("Removed bugs ============================ " + removedbugs.size());
//		System.out.println("Added bugs ============================ " + addedbugs.size());

		bugs.removeAll(removedbugs);
		bugs.addAll(addedbugs);
	}



	private void includeTraceToInterestL(CGNode target) {
		Trace trace;
		if(target instanceof AstCGNode2){
			trace = shb.getTrace(((AstCGNode2) target).getCGNode());
		}else{
			trace = shb.getTrace(target);
		}
		for (INode inode : trace.getContent()) {
			if(inode instanceof DLockNode){
				interested_l.add((DLockNode) inode);
			}
		}
	}


	private void includeTraceToInterestRW(CGNode node) {
		Trace trace = shb.getTrace(node);
		if(trace == null){
			if(node instanceof AstCGNode2){
				trace = shb.getTrace(((AstCGNode2) node).getCGNode());
			}
		}

		HashMap<String, ArrayList<ReadNode>> rMap = trace.getRsigMapping();
		if(rMap.size() > 0){
			Set<String> rsig = rMap.keySet();
			interested_rw.addAll(rsig);
		}
		HashMap<String, ArrayList<WriteNode>> wMap = trace.getWsigMapping();
		if(wMap.size() > 0){
			Set<String> wsig = wMap.keySet();//can be reduced to smaller range
			interested_rw.addAll(wsig);
		}
	}



	private void recheckRace() {
//		System.err.println("Start to detect races INCREMENTALLLy: ");
		//1. find shared vars
		HashSet<String> new_sharedFields = new HashSet<>();
		//seq
		for (String sig : interested_rw) {
			HashMap<Integer, Integer> writeTids = wsig_tid_num_map.get(sig);
			if(writeTids != null){
				if(writeTids.keySet().size() > 1){
					sharedFields.add(sig);
					new_sharedFields.add(sig);
				}else{
					if(rsig_tid_num_map.containsKey(sig)){
						HashMap<Integer, Integer> readTids = rsig_tid_num_map.get(sig);
						if(readTids!=null){
							if(readTids.keySet().size() + writeTids.keySet().size() >1){
								sharedFields.add(sig);
								new_sharedFields.add(sig);
							}
						}
					}
				}
			}
		}

//		System.out.println("rsig tid map ----------------------------");
//		System.out.println(rsig_tid_num_map.toString());
//		System.out.println("wsig tid map ----------------------------");
//		System.out.println(wsig_tid_num_map.toString());
//		System.out.println("new shared Fields ---------------------------------");
//		for (String field : new_sharedFields) {
//			System.out.println(field + "   w:" + wsig_tid_num_map.get(field) + "  r:" + rsig_tid_num_map.get(field));
//		}

		//2. remove local vars  => for incremental change check, not for functionalities
		bughub.tell(new IncreRemoveLocalVar(new_sharedFields), bughub);
		awaitBugHubComplete();

//		for (String field : new_sharedFields) {
//			HashSet<ReadNode> readNodes = sigReadNodes.get(field);
//			HashSet<WriteNode> writes = sigWriteNodes.get(field);
//			System.out.println(field + ": R " + readNodes.size() + " W: " + writes.size());
//		}

		//3. perform race detection
		bughub.tell(new IncrementalCheckDatarace(new_sharedFields), bughub);//interest_rw?
		awaitBugHubComplete();

		//4. recheck existing races that have been protected by common locks
		bughub.tell(new IncrementalRecheckCommonLock(), bughub);
		awaitBugHubComplete();

		recheckRaces.clear();
	}



	private void recheckLock() {
//		System.err.println("Start to detect deadlock INCREMENTALLLy:");
		bughub.tell(new IncrementalDeadlock(interested_l), bughub);
		awaitBugHubComplete();
	}

	//ignore variable identified by sig
	public void removeRWSigFromRWMaps(String excludedSig){
		sigReadNodes.remove(excludedSig);
		sigWriteNodes.remove(excludedSig);
	    rsig_tid_num_map.remove(excludedSig);
	    wsig_tid_num_map.remove(excludedSig);
	}

	public void collectRWSigToRWMaps(String excludedSig){
		HashSet<WriteNode> exWrites = new HashSet<>();
		HashSet<ReadNode> exReads = new HashSet<>();
		//collect all the rws in all traces
		ArrayList<Trace> traces = shb.getAllTraces();
		for (Trace trace : traces) {
			ArrayList<WriteNode> traceWrites = trace.getWsigMapping().get(excludedSig);
			if(traceWrites != null){
				exWrites.addAll(traceWrites);
			}
			ArrayList<ReadNode> traceReads = trace.getRsigMapping().get(excludedSig);
			if(traceReads != null){
				exReads.addAll(traceReads);
			}
		}
		//collect all the rws in excludedsigr/wmap
		HashSet<WriteNode> ignoreWrites = excludedWriteSigMapping.get(excludedSig);
		if(ignoreWrites != null){
			exWrites.addAll(ignoreWrites);
		}
		HashSet<ReadNode> ignoreReads = excludedReadSigMapping.get(excludedSig);
		if(ignoreReads != null){
			exReads.addAll(ignoreReads);
		}
		//add back to current storage
		sigWriteNodes.put(excludedSig, exWrites);
		HashMap<Integer, Integer> rtidnummap = wsig_tid_num_map.get(excludedSig);
		if(rtidnummap == null){
			rtidnummap = new HashMap<>();
			wsig_tid_num_map.put(excludedSig, rtidnummap);
		}
		for (WriteNode write : exWrites) {
			ArrayList<Integer> wtids = shb.getTrace(write.getBelonging()).getTraceTids();
			for (Integer wtid : wtids) {
				if(rtidnummap.keySet().contains(wtid)){
					int num = rtidnummap.get(wtid);
					rtidnummap.replace(wtid, ++num);
				}else{
					rtidnummap.put(wtid, 1);
				}

			}
		}
		//add back to current storage
		sigReadNodes.put(excludedSig, exReads);
		HashMap<Integer, Integer> wtidnummap = rsig_tid_num_map.get(excludedSig);
		if(wtidnummap == null){
			wtidnummap = new HashMap<>();
			rsig_tid_num_map.put(excludedSig, wtidnummap);
		}
		for (ReadNode read : exReads) {
			ArrayList<Integer>rtids = shb.getTrace(read.getBelonging()).getTraceTids();
			for (Integer rtid : rtids) {
				if(wtidnummap.keySet().contains(rtid)){
					int num = wtidnummap.get(rtid);
					wtidnummap.replace(rtid, ++num);
				}else{
					wtidnummap.put(rtid, 1);
				}
			}
		}
	}


	public HashSet<TIDERace> excludeThisSigForRace(String excludedSig) {
		excludedSigForRace.add(excludedSig);
		//remove from current storage
//		sigReadNodes.remove(excludedSig);
//		sigWriteNodes.remove(excludedSig);
		removeRWSigFromRWMaps(excludedSig);
		return removeBugsRelatedToSig(excludedSig);
	}



	public HashSet<ITIDEBug> considerThisSigForRace(String considerSig) {
		interested_rw.clear();
		interested_l.clear();
		addedbugs.clear();
		removedbugs.clear();
		excludedSigForRace.remove(considerSig);
		collectRWSigToRWMaps(considerSig);
		interested_rw.add(considerSig);
		recheckRace();
		bugs.addAll(addedbugs);
		return addedbugs;
	}

	public HashSet<TIDERace> excludeThisSigForRace(TIDERace race, String excludedSig) {
		excludedSigForRace.add(excludedSig);
		//remove from current storage
//		sigReadNodes.remove(excludedSig);
//		sigWriteNodes.remove(excludedSig);
		removeRWSigFromRWMaps(excludedSig);
		bugs.remove(race);//remove the specific one
		return removeBugsRelatedToSig(excludedSig);//remove all the race realted to the sig
	}

	public HashSet<ITIDEBug> considerThisSigForRace(TIDERace race, String considerSig) {
		// should not be called, since no race should exist.
		return considerThisSigForRace(considerSig);
	}


	public void excludeThisSigForRace(HashSet<TIDERace> races, String excludedSig) {
		excludedSigForRace.add(excludedSig);
		//remove from current storage
//		sigReadNodes.remove(excludedSig);
//		sigWriteNodes.remove(excludedSig);
		removeRWSigFromRWMaps(excludedSig);
		bugs.remove(races);//remove the specific ones
	}

	public void considerThisSigForRace(HashSet<TIDERace> races, String considerSig) {
		// should not be called, since no race should exist.
		considerThisSigForRace(considerSig);
	}


	/**
	 * only for ignore this variable (race)
	 * @param sig
	 * @return
	 */
	public HashSet<TIDERace> removeBugsRelatedToSig(String sig){
		HashSet<TIDERace> set = new HashSet<>();
		for (ITIDEBug bug : bugs) {
			if(bug instanceof TIDERace){
				TIDERace race = (TIDERace) bug;
				if(race.sig.equals(sig)){
					set.add(race);
				}
			}
		}
		bugs.removeAll(set);
//		removedbugs.addAll(set);//??
		return set;
	}

















	//	private ArrayList<DLLockPair> recoverNewLockRelation(ArrayList<Integer> newcontent) {
	//		//lockset has been recovered during traversing, here recover for deadlock
	//		//return new lockorders
	//		ArrayList<DLLockPair> result = new ArrayList<>();
	//		for(int i=0; i<newcontent.size(); i=i+2){
	//			int start = newcontent.get(i);
	//			int end = newcontent.get(i+1);
	//			List<INode> sub = trace.subList(start, end);
	//			ArrayList<DLLockPair> recovers = new ArrayList<>();
	//			for (INode n : sub) {
	//				if(n instanceof DLockNode){
	//					boolean goon = true;
	//					int idx = trace.indexOf(n);
	//					//search before until reach lock/unlock
	//					while(goon){
	//						INode before = trace.get(idx--);
	//						if(before instanceof DUnlockNode){
	//							goon = false;
	//						}else if(before instanceof DLockNode){
	//							//add before -> n
	//							DLLockPair new_pair = new DLLockPair((DLockNode)before, (DLockNode)n);
	//							recovers.add(new_pair);
	//							//discover order of before and add to n
	//							ArrayList<DLLockPair> existpairs = threadDLLockPairs.get(curTID);
	//							for (DLLockPair pair : existpairs) {
	//								if(pair.lock2 == before){
	//									//more orders
	//									DLLockPair more_pair = new DLLockPair(pair.lock1, (DLockNode)n);
	//									recovers.add(more_pair);
	//								}
	//							}
	//
	//						}
	//					}
	//					//search after
	//					goon = true;
	//					while(goon){
	//						INode after = trace.get(idx++);
	//						if(after instanceof DUnlockNode){
	//							goon = false;
	//						}else if(after instanceof DLockNode){
	//							//add n -> after
	//							DLLockPair new_pair = new DLLockPair((DLockNode)n, (DLockNode)after);
	//							recovers.add(new_pair);
	//							//discover order of before and add to n
	//							ArrayList<DLLockPair> existpairs = threadDLLockPairs.get(curTID);
	//							for (DLLockPair pair : existpairs) {
	//								if(pair.lock1 == after){
	//									//more orders
	//									DLLockPair more_pair = new DLLockPair(pair.lock1, (DLockNode)n);
	//									recovers.add(more_pair);
	//								}
	//							}
	//
	//						}
	//					}
	//					//add all recover back
	//					threadDLLockPairs.get(curTID).addAll(recovers);
	//					result.addAll(recovers);
	//				}
	//			}
	//		}
	//		return result;
	//	}







	//////////////////////
	//	  public static HSSFWorkbook wb = new HSSFWorkbook();
	//	  HSSFSheet sheet = wb.createSheet("parallel");
	//
	//	  public void initializeSheet(){
	//	    Row row1st = sheet.createRow(0);
	//	    Cell r1c1 = row1st.createCell(0);    r1c1.setCellValue("race Time");
	//	    Cell r1c2 = row1st.createCell(1);    r1c2.setCellValue("dl time");
	//	  }
	//
	//	  int rowNum = 1;
	//	  Row row;
	//
	//	  public void writeRaceTime(long dt){
	//	    row = sheet.createRow(rowNum);
	//	    row.createCell(0).setCellValue(dt);
	//	  }
	//
	//	  public void writeDlTime(long at){
	//	    row.createCell(1).setCellValue(at);
	//	  }
	//
	//	  public void writeInst(SSAInstruction inst){
	//	    row.createCell(4).setCellValue(inst.toString());
	//	    rowNum++;
	//	  }


	///
	//	private void traverseNode(CGNode n)
	//	{
	//		System.out.println("Traverse Node: "+ n.toString());
	//		if(alreadyProcessedNodes.contains(n))
	//		{
	//			//allow multiple entries of a method if there exist sync in between
	//			if(!hasSyncBetween)
	//				return;
	//			else
	//				hasSyncBetween = false;
	//		}
	//		alreadyProcessedNodes.add(n);
	//
	//		if(n.getIR() == null)
	//			return;
	//
	//		SSACFG ssacfg = n.getIR().getControlFlowGraph();
	//		ISSABasicBlock curbb = ssacfg.getBasicBlock(0);
	//		HashSet<DLockNode> currentHoldLock = new HashSet<DLockNode>();
	//		HashSet<ISSABasicBlock> processedBB = new HashSet<>();
	//		processBasicBlock(curbb, n, currentHoldLock, processedBB);
	//
	//	}


	//	private void traverseMethod(CGNode n, HashSet<DLockNode> currentHoldLock)
	//	{
	//		System.out.println("Traverse Method: "+ n.toString());
	//		if(alreadyProcessedNodes.contains(n))
	//		{
	//			//allow multiple entries of a method if there exist sync in between
	//			if(!hasSyncBetween)
	//				return;
	//			else
	//				hasSyncBetween = false;
	//		}
	//		alreadyProcessedNodes.add(n);
	//		if(n.getIR() == null)
	//			return;
	//
	//		SSACFG ssacfg = n.getIR().getControlFlowGraph();
	//		//		System.out.println(ssacfg.toString());
	//		ISSABasicBlock curbb = ssacfg.getBasicBlock(0);
	//		HashSet<DLockNode> currentHoldLock2 = new HashSet<DLockNode>();
	//		currentHoldLock2.addAll(currentHoldLock);
	//		HashSet<ISSABasicBlock> processedBB = new HashSet<>();
	//		processBasicBlock(curbb, n, currentHoldLock2, processedBB);
	//	}
	//
	//	public void processBasicBlock(ISSABasicBlock curbb, CGNode n, HashSet<DLockNode> currentHoldLock, HashSet<ISSABasicBlock> processedBB){
	//		//		System.out.println(curbb.toString());
	//		Iterator<SSAInstruction> insts = curbb.iterator();
	//		while(insts.hasNext()){
	//			SSAInstruction inst = insts.next();
	//			//			if(inst.iindex == -1) // all are phi inst
	//			//				System.out.println(" ------ " + inst.iindex + "   " + inst.toString());
	//			if(inst!=null && inst.iindex != -1){
	//				IMethod method = n.getMethod() ;
	//				int sourceLineNum = 0;
	//				try{//get source code line number of this inst
	//					if(n.getIR().getMethod() instanceof IBytecodeMethod){
	//						int bytecodeindex = ((IBytecodeMethod) n.getIR().getMethod()).getBytecodeIndex(inst.iindex);
	//						sourceLineNum = (int)n.getIR().getMethod().getLineNumber(bytecodeindex);
	//					}else{
	//						sourceLineNum = n.getMethod().getSourcePosition(inst.iindex).getFirstLine();//.getLastLine();
	//					}
	//				}catch(Exception e)
	//				{
	//					e.printStackTrace();
	//				}
	//
	//				//System.out.println(inst.toString());
	//				if(inst instanceof SSAFieldAccessInstruction){
	//
	//					//not in constructor
	//					if(n.getMethod().isClinit()||n.getMethod().isInit())
	//						continue;
	//					//TODO: handling field access of external objects
	//					String classname = ((SSAFieldAccessInstruction)inst).getDeclaredField().getDeclaringClass().getName().toString();
	//					String fieldname = ((SSAFieldAccessInstruction)inst).getDeclaredField().getName().toString();
	//					String sig = classname.substring(1)+"."+fieldname;
	//
	//					String typeclassname =  method.getDeclaringClass().getName().toString();
	//					String instSig =typeclassname.substring(1)+":"+sourceLineNum;
	//
	//					if(((SSAFieldAccessInstruction)inst).isStatic())
	//						logFieldAccess(inst, sig, sourceLineNum, instSig);
	//					else
	//					{
	//
	//						int baseValueNumber = ((SSAFieldAccessInstruction)inst).getUse(0);
	//						if(baseValueNumber==1)//this.f
	//						{
	//							if(curReceivers!=null)
	//							{
	//								for(String receiver : curReceivers)
	//								{
	//									String sig2 = sig+"."+receiver;
	//									logFieldAccess(inst, sig2, sourceLineNum, instSig);
	//								}
	//							}
	//						}
	//						else
	//						{
	//							PointerKey basePointer =pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, baseValueNumber);
	//							OrdinalSet<InstanceKey> baseObjects = pointerAnalysis.getPointsToSet(basePointer);
	//							for (InstanceKey instanceKey : baseObjects) {
	//								if(curReceivers==null||curReceivers.isEmpty())
	//								{
	//									String sig2 = sig+"."+String.valueOf(instanceKey.hashCode());
	//									logFieldAccess(inst, sig2, sourceLineNum, instSig);
	//								}
	//								else
	//								{
	//									for(String receiver : curReceivers)
	//									{
	//										String sig2 = sig+"."+receiver+"Y"+String.valueOf(instanceKey.hashCode());
	//										logFieldAccess(inst, sig2, sourceLineNum, instSig);
	//									}
	//								}
	//							}
	//						}
	//					}
	//
	//				}
	//				else if (inst instanceof SSAArrayReferenceInstruction)
	//				{
	//					SSAArrayReferenceInstruction arrayRefInst = (SSAArrayReferenceInstruction) inst;
	//					int arrayRef = arrayRefInst.getArrayRef();
	//					PointerKey key = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, arrayRef);
	//					OrdinalSet<InstanceKey> instances = pointerAnalysis.getPointsToSet(key);
	//					for(InstanceKey ins: instances)
	//					{
	//						//TypeName name = ins.getConcreteType().getName();
	//						//String sig = currentClassName+".array."+ins.hashCode();
	//						String sig = "array."+ins.hashCode();
	//						//						IMethod method = n.getMethod() ;
	//						//						int sourceLineNum = method.getLineNumber(inst.iindex);
	//						String typeclassname =  method.getDeclaringClass().getName().toString();
	//						String instSig =typeclassname.substring(1)+":"+sourceLineNum;
	//						logArrayAccess(inst, sig, sourceLineNum, instSig);
	//					}
	//				}
	//				else if (inst instanceof SSAAbstractInvokeInstruction){
	//
	//					CallSiteReference csr = ((SSAAbstractInvokeInstruction)inst).getCallSite();
	//					MethodReference mr = csr.getDeclaredTarget();
	//					//if (AnalysisUtils.implementsRunnableInterface(iclass) || AnalysisUtils.extendsThreadClass(iclass))
	//					{
	//						com.ibm.wala.classLoader.IMethod imethod = callGraph.getClassHierarchy().resolveMethod(mr);
	//						if(imethod!=null){
	//							String sig = imethod.getSignature();
	//							//System.out.println("Invoke Inst: "+sig);
	//							if(sig.equals("java.lang.Thread.start()V")){
	//
	//								PointerKey key = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, ((SSAAbstractInvokeInstruction) inst).getReceiver());
	//								OrdinalSet<InstanceKey> instances = pointerAnalysis.getPointsToSet(key);
	//								for(InstanceKey ins: instances)
	//								{
	//									TypeName name = ins.getConcreteType().getName();
	//									CGNode node = threadSigNodeMap.get(name);
	//									HashSet<String> threadReceivers = new HashSet();
	//									//FIXME: BUG
	//									if(node==null){
	//										//TODO: find out which runnable object -- need data flow analysis
	//										int param = ((SSAAbstractInvokeInstruction)inst).getUse(0);
	//										node = handleRunnable(ins,threadReceivers, param, n);
	//										if(node==null)
	//										{
	//											System.err.println("ERROR: starting new thread: "+ name);
	//											continue;
	//										}
	//									}else{//get threadReceivers
	//										//should be the hashcode of the instancekey
	//										threadReceivers.add(String.valueOf(ins.hashCode()));//SHOULD BE "this thread/runnable object"
	//									}
	//
	//									//duplicate graph node id
	//									if(tidpool.contains(node.getGraphNodeId())){
	//										AstCGNode2 threadNode = new AstCGNode2(imethod, node.getContext());
	//										int threadID = ++maxGraphNodeID;
	//										threadNode.setGraphNodeId(threadID);
	//										threadNode.setCGNode(node);
	//										threadNode.setIR(node.getIR());
	//										newRunTargets.put(threadNode, node);
	//										node = threadNode;
	//									}
	//
	//									threadNodes.add(node);
	//									int tid_child = node.getGraphNodeId();
	//									tidpool.add(tid_child);
	//									//add node to trace
	//									StartNode startNode = new StartNode(getIncrementGID(), curTID, tid_child);
	//									trace.add(startNode);
	//									mapOfStartNode.put(tid_child, startNode);
	//									mapOfStartNode.get(curTID).addChild(tid_child);
	//
	//									//put to tid -> curreceivers map
	//									tid2Receivers.put(node.getGraphNodeId(), threadReceivers);
	//									//TODO: check if it is in a simple loop
	//									boolean isInLoop = isInLoop(n,inst);
	//
	//									if(isInLoop)
	//									{
	//										AstCGNode2 node2 = new AstCGNode2(node.getMethod(),node.getContext());
	//										threadNodes.add(node2);
	//										int newID = ++maxGraphNodeID;
	//										StartNode duplicate = new StartNode(getIncrementGID(),curTID,newID);
	//										trace.add(duplicate);//thread id +1
	//										mapOfStartNode.put(newID, duplicate);
	//										mapOfStartNode.get(curTID).addChild(newID);
	//
	//										node2.setGraphNodeId(newID);
	//										node2.setIR(node.getIR());
	//										node2.setCGNode(node);
	//
	//										//need to change thread receiver id as well
	//										Set<String> threadReceivers2 = new HashSet();
	//										for(String id: threadReceivers)
	//										{
	//											threadReceivers2.add(id+"X");//"X" as the marker
	//										}
	//										//put to tid -> curreceivers map
	//										tid2Receivers.put(newID, threadReceivers2);
	//									}
	//								}
	//								//find loops in this method!!
	//								//node.getIR().getControlFlowGraph();
	//								hasSyncBetween = true;
	//							}
	//							else if(sig.equals("java.lang.Thread.join()V")){
	//								PointerKey key = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, ((SSAAbstractInvokeInstruction) inst).getReceiver());
	//								OrdinalSet<InstanceKey> instances = pointerAnalysis.getPointsToSet(key);
	//								for(InstanceKey ins: instances)
	//								{
	//									TypeName name = ins.getConcreteType().getName();
	//									CGNode node = threadSigNodeMap.get(name);
	//									//threadNodes.add(node);
	//
	//									HashSet<String> threadReceivers = new HashSet();
	//
	//									if(node==null){//could be a runnable class
	//										int param = ((SSAAbstractInvokeInstruction)inst).getUse(0);
	//										node = handleRunnable(ins,threadReceivers, param, n);
	//										if(node==null){
	//											System.err.println("ERROR: joining new thread: "+ name );
	//											continue;
	//										}
	//									}
	//
	//									//add node to trace
	//									JoinNode jNode = new JoinNode(getIncrementGID(),curTID,node.getGraphNodeId());
	//									trace.add(jNode);
	//									mapOfJoinNode.put(node.getGraphNodeId(), jNode);
	//
	//									boolean isInLoop = isInLoop(n,inst);
	//									if(isInLoop)
	//									{
	//										AstCGNode2 node2 = new AstCGNode2(node.getMethod(),node.getContext());
	//										//threadNodes.add(node2);
	//										int newID = ++maxGraphNodeID;
	//										JoinNode jNode2 = new JoinNode(getIncrementGID(),curTID,newID);
	//										trace.add(jNode2);//thread id +1
	//										mapOfJoinNode.put(newID, jNode2);
	//										node2.setGraphNodeId(newID);
	//										node2.setIR(node.getIR());
	//										node2.setCGNode(node);
	//									}
	//								}
	//								hasSyncBetween = true;
	//							}
	//							else
	//							{
	//								//other method calls
	//								//save current curReceivers
	//								Set<String> curReceivers_pre = curReceivers;
	//								//process NEW method call
	//								Set<CGNode> set = new HashSet<>();
	//								if(n instanceof AstCGNode2){
	//									set = callGraph.getPossibleTargets(newRunTargets.get(n), csr);
	//								}else{
	//									set = callGraph.getPossibleTargets(n, csr);
	//								}
	//								for(CGNode node: set){
	//									if(AnalysisUtils.isApplicationClass(node.getMethod().getDeclaringClass())
	//											//&&node.getMethod().getName().toString().equals(csr.getDeclaredTarget().getName().toString())
	//											)
	//									{
	//										//static method call
	//										if(node.getMethod().isStatic())
	//										{
	//											//set current receivers to null
	//											curReceivers = null;
	//											//use classname as lock name
	//											String lock = node.getMethod().getDeclaringClass().getName().toString();
	//											//if synchronized method, add lock/unlock
	//											if(node.getMethod().isSynchronized())
	//											{
	//												trace.add(new LockNode(getIncrementGID(),curTID,lock));
	//											}
	//											traverseMethod(node, currentHoldLock);
	//											if(node.getMethod().isSynchronized())
	//											{
	//												trace.add(new UnlockNode(getIncrementGID(),curTID,lock));
	//											}
	//										}
	//										else
	//										{
	//											//instance
	//											int objectValueNumber = inst.getUse(0);
	//											PointerKey objectPointer =pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, objectValueNumber);
	//											OrdinalSet<InstanceKey> abstractObjects = pointerAnalysis.getPointsToSet(objectPointer);
	//											int gidforlocks = getIncrementGID();
	//											boolean isSync = false;
	//											HashSet<DLockNode> willBeLocks = new HashSet<>();
	//											if(abstractObjects.size()>0)//must be larger than 0
	//											{
	//												curReceivers = new HashSet();
	//												//configuration
	//												int K_obj_sensitive = 0;//0 means non-object sensitive
	//												for (InstanceKey instanceKey : abstractObjects) {
	//													//add receiver
	//													if(K_obj_sensitive>0&&curReceivers_pre!=null)
	//													{
	//														for(String instance_pre: curReceivers_pre)
	//														{
	//															String temmStr = instance_pre;
	//															String curObject = String.valueOf(instanceKey.hashCode());
	//															{
	//																//find the last Y or not
	//																int indexY = instance_pre.lastIndexOf("Y");
	//																if(indexY>-1)
	//																	temmStr = instance_pre.substring(indexY);
	//																//object sensitivity is memory-demanding -- limit it to 2
	//																//count number of Ys
	//																int Kount = temmStr.length() - temmStr.replaceAll("Y", "").length();
	//																if(Kount<=K_obj_sensitive
	//																		&&!temmStr.equals(curObject))//-- limit it to 2
	//																	curReceivers.add(instance_pre+"Y"+curObject);
	//															}
	//														}
	//													}
	//													else
	//														curReceivers.add(String.valueOf(instanceKey.hashCode()));
	//
	//													if(node.getMethod().isSynchronized()){
	//														isSync = true;
	//														String typeclassname =  n.getMethod().getDeclaringClass().getName().toString();
	//														String instSig =typeclassname.substring(1)+":"+sourceLineNum;
	//														String lock = instanceKey.getConcreteType().getName()+"."+instanceKey.hashCode();
	//														SSAInstruction createinst = findInitialInst(n, instanceKey);
	//														// for deadlock
	//														//													trace.add(new DLockNode(gidforlocks,curTID,instSig, lock, sourceLineNum, createinst));
	//														//for race
	//														trace.add(new LockNode(getIncrementGID(),curTID,lock));
	//														//for deadlock path sensitive
	//														DLockNode willLock = new DLockNode(gidforlocks,curTID,instSig, lock, sourceLineNum, createinst);
	//														willBeLocks.add(willLock);
	//														if(!currentHoldLock.isEmpty()){
	//															ArrayList<DLLockPair> dlpairs = threadDLLockPairs.get(curTID);
	//															if(dlpairs==null){
	//																dlpairs = new ArrayList<DLLockPair>();
	//																threadDLLockPairs.put(curTID, dlpairs);
	//															}
	//															for (DLockNode exist : currentHoldLock) {
	//																dlpairs.add(new DLLockPair(exist, willLock));
	//															}
	//														}
	//													}
	//												}
	//												currentHoldLock.addAll(willBeLocks);
	//											}
	//
	//											traverseMethod(node, currentHoldLock);
	//											currentHoldLock.removeAll(willBeLocks);
	//											willBeLocks.clear();
	//
	//											if(isSync)
	//												for (InstanceKey instanceKey : abstractObjects) {
	//													String typeclassname =  n.getMethod().getDeclaringClass().getName().toString();
	//													String instSig =typeclassname.substring(1)+":"+sourceLineNum;
	//													String lock = instanceKey.getConcreteType().getName()+"."+instanceKey.hashCode();
	//													// for deadlock
	//													//													trace.add(new DUnlockNode(gidforlocks,curTID,instSig, instanceKey));
	//													//for race
	//													trace.add(new UnlockNode(getIncrementGID(),curTID,lock));
	//												}
	//										}
	//									}
	//								}
	//								curReceivers = curReceivers_pre;
	//							}
	//						}
	//					}
	//				}
	//				else if(inst instanceof SSAMonitorInstruction)
	//				{
	//					//lock node: GID, TID, LockID
	//					SSAMonitorInstruction monitorInstruction = ((SSAMonitorInstruction) inst);
	//					int lockValueNumber = monitorInstruction.getRef();
	//
	//					PointerKey lockPointer =pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, lockValueNumber);
	//					OrdinalSet<InstanceKey> lockObjects = pointerAnalysis.getPointsToSet(lockPointer);
	//					//lets use must alias analysis for race
	//					if(lockObjects.size()==1){
	//						for (InstanceKey instanceKey : lockObjects) {
	//							String lock = instanceKey.getConcreteType().getName()+"."+instanceKey.hashCode();
	//							if(((SSAMonitorInstruction) inst).isMonitorEnter()){
	//								trace.add(new LockNode(getIncrementGID(),curTID,lock));
	//							}else{
	//								trace.add(new UnlockNode(getIncrementGID(),curTID,lock));
	//							}
	//						}
	//					}
	//					// for deadlock
	//					int gidforlocks = getIncrementGID();
	//					HashSet<DLockNode> willBeLocks = new HashSet<>();
	//					ArrayList<DLLockPair> dlpairs = threadDLLockPairs.get(curTID);
	//					if(dlpairs==null){
	//						dlpairs = new ArrayList<DLLockPair>();
	//						threadDLLockPairs.put(curTID, dlpairs);
	//					}
	//					for (InstanceKey instanceKey : lockObjects) {
	//						String typeclassname =  n.getMethod().getDeclaringClass().getName().toString();
	//						String instSig =typeclassname.substring(1)+":"+sourceLineNum;
	//						String lock = instanceKey.getConcreteType().getName()+"."+instanceKey.hashCode();
	//						SSAInstruction createinst = findInitialInst(n, instanceKey);
	//						DLockNode willLock = new DLockNode(gidforlocks,curTID,instSig,lock, sourceLineNum, createinst);
	//						if(((SSAMonitorInstruction) inst).isMonitorEnter()){
	//							willBeLocks.add(willLock);
	//							//							trace.add();
	//							if(!currentHoldLock.isEmpty()){
	//								for (DLockNode exist : currentHoldLock) {
	//									dlpairs.add(new DLLockPair(exist, willLock));
	//								}
	//							}
	//						}else{
	//							//							trace.add(new DUnlockNode(gidforlocks,curTID,instSig, instanceKey));
	//							willBeLocks.add(willLock);
	//						}
	//					}
	//					if(((SSAMonitorInstruction) inst).isMonitorEnter()){
	//						currentHoldLock.addAll(willBeLocks);
	//					}else{
	//						currentHoldLock.removeAll(willBeLocks);
	//					}
	//					willBeLocks.clear();
	//					hasSyncBetween = true;
	//				}else if(inst instanceof SSAConditionalBranchInstruction){
	//					SSAConditionalBranchInstruction condition = (SSAConditionalBranchInstruction) inst;
	//
	//				}
	//			}
	//		}
	//		SSACFG cfg = n.getIR().getControlFlowGraph();
	//		Collection<ISSABasicBlock> bbkids = cfg.getNormalSuccessors(curbb);
	//		Iterator<ISSABasicBlock> iter = bbkids.iterator();
	//		while(iter.hasNext()){
	//			ISSABasicBlock next = iter.next();
	//			if(next.getNumber() > curbb.getNumber() &&!next.isCatchBlock() && !next.isExitBlock()){  //
	//				HashSet<DLockNode> currentHoldLock2 = new HashSet<DLockNode>();
	//				currentHoldLock2.addAll(currentHoldLock);
	//				//				System.out.println("processsed bb: " + next.toString());
	//				processBasicBlock(next, n, currentHoldLock2, processedBB);
	//			}else if(next.getNumber() < curbb.getNumber() && !processedBB.contains(next)){
	//				processedBB.add(next);
	//				HashSet<DLockNode> currentHoldLock2 = new HashSet<DLockNode>();
	//				currentHoldLock2.addAll(currentHoldLock);
	//				//				System.out.println("processsed bb: " + next.toString());
	//				processBasicBlock(next, n, currentHoldLock2, processedBB);
	//			}
	//		}
	//	}


}
