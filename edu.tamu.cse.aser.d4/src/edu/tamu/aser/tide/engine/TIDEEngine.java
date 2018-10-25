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
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.IMethod.SourcePosition;
import com.ibm.wala.fixpoint.IVariable;
import com.ibm.wala.ide.util.JdtPosition;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.propagation.AllocationSiteInNode;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.LocalPointerKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ipa.callgraph.propagation.PointsToSetVariable;
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
import com.ibm.wala.ssa.SSAPhiInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.graph.dominators.NumberedDominators;
import com.ibm.wala.util.intset.IntSetUtil;
import com.ibm.wala.util.intset.MutableIntSet;
import com.ibm.wala.util.intset.OrdinalSet;

import akka.actor.ActorRef;
import edu.tamu.aser.tide.akkabug.BugHub;
import edu.tamu.aser.tide.akkabug.DistributeDatarace;
import edu.tamu.aser.tide.akkabug.DistributeDeadlock;
import edu.tamu.aser.tide.akkabug.FindSharedVariable;
import edu.tamu.aser.tide.akkabug.IncreRemoveLocalVar;
import edu.tamu.aser.tide.akkabug.IncrementalCheckDatarace;
import edu.tamu.aser.tide.akkabug.IncrementalDeadlock;
import edu.tamu.aser.tide.akkabug.RemoveLocalVar;
import edu.tamu.aser.tide.nodes.DLPair;
import edu.tamu.aser.tide.nodes.DLockNode;
import edu.tamu.aser.tide.nodes.DUnlockNode;
import edu.tamu.aser.tide.nodes.INode;
import edu.tamu.aser.tide.nodes.JoinNode;
import edu.tamu.aser.tide.nodes.LockPair;
import edu.tamu.aser.tide.nodes.MemNode;
import edu.tamu.aser.tide.nodes.MethodNode;
import edu.tamu.aser.tide.nodes.ReadNode;
import edu.tamu.aser.tide.nodes.StartNode;
import edu.tamu.aser.tide.nodes.SyncNode;
import edu.tamu.aser.tide.nodes.WriteNode;
import edu.tamu.aser.tide.shb.SHBEdge;
import edu.tamu.aser.tide.shb.SHBGraph;
import edu.tamu.aser.tide.shb.Trace;
import edu.tamu.wala.increpta.ipa.callgraph.propagation.IPAPointerAnalysisImpl;
import edu.tamu.wala.increpta.ipa.callgraph.propagation.IPAPropagationGraph;
import edu.tamu.wala.increpta.ipa.callgraph.propagation.IPASSAPropagationCallGraphBuilder;
import edu.tamu.wala.increpta.util.IPAAbstractFixedPointSolver;

public class TIDEEngine{

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
	private HashSet<CGNode> scheduledAstNodes = new HashSet<CGNode>();

	private LinkedList<CGNode> mainEntryNodes = new LinkedList<CGNode>();
	private LinkedList<CGNode> threadNodes = new LinkedList<CGNode>();

	private MutableIntSet stidpool = IntSetUtil.make();//start
	private HashMap<Integer, AstCGNode2> dupStartJoinTidMap = new HashMap<>();//join with dup tid
	private HashMap<TypeName, CGNode> threadSigNodeMap = new HashMap<TypeName,CGNode>();

	private boolean hasSyncBetween = false;

	public HashMap<Integer, StartNode> mapOfStartNode = new HashMap<>();
	public HashMap<Integer, JoinNode> mapOfJoinNode = new HashMap<>();
	//lock pairs for deadlock
	public HashMap<Integer, ArrayList<DLPair>> threadDLLockPairs = new HashMap<Integer, ArrayList<DLPair>>();
	//currently locked objects
	public 	HashMap<Integer, HashSet<DLockNode>> threadLockNodes = new HashMap<Integer, HashSet<DLockNode>>();
	//node <-> since it's in a loop and we create an astnode
	public HashMap<CGNode, AstCGNode2> n_loopn_map = new HashMap<>();

	private static HashMap<CGNode,Collection<Loop>> nodeLoops = new HashMap<CGNode,Collection<Loop>>();

	private IPASSAPropagationCallGraphBuilder builder;
	public CallGraph callGraph;
	public IPAPointerAnalysisImpl pointerAnalysis;
	protected IPAPropagationGraph propagationGraph;
	private int maxGraphNodeID;

	public long timeForDetectingRaces = 0;
	public long timeForDetectingDL = 0;

	public HashSet<String> sharedFields = new HashSet<String>();

	public HashSet<TIDERace> races = new HashSet<>();
	public HashSet<TIDERace> removedraces = new HashSet<>();
	public HashSet<TIDERace> addedraces = new HashSet<>();

	public HashSet<TIDEDeadlock> deadlocks = new HashSet<>();
	public HashSet<TIDEDeadlock> removeddeadlocks = new HashSet<>();
	public HashSet<TIDEDeadlock> addeddeadlocks = new HashSet<>();

	private HashSet<CGNode> syncMethods = new HashSet<>();
	private HashMap<Integer, SSAAbstractInvokeInstruction> threadInits = new HashMap<>();

	//incre
	public HashSet<CGNode> changedNodes = new HashSet<>();

	//akka system
	public ActorRef bughub;
	public SHBGraph shb;
	//to track changes from pta
	public HashMap<PointerKey, HashSet<MemNode>> pointer_rwmap = new HashMap<>();
	public HashMap<PointerKey, HashSet<SyncNode>> pointer_lmap = new HashMap<>();

	public int curTID;
	public HashMap<CGNode, Integer> astCGNode_ntid_map = new HashMap<>();

	//map: class -> belonging methods
	public HashMap<String, HashSet<String>> class2Methods = new HashMap<>();
	public void addClass2Methods(CGNode node){
		String dClass = node.getMethod().getDeclaringClass().getName().getClassName().toString();
		String id = node.getMethod().getName().toString();
		HashSet<String> set = class2Methods.get(dClass);
		if(set == null){
			set = new HashSet<>();
			class2Methods.put(dClass, set);
		}
		set.add(id);
	}

	/**
	 * flag for incremental changes
	 */
	public boolean change = false;
	public void setChange(boolean p){
		this.change = p;
	}

	boolean recursive = true; //recursively remove/consider the r,w,lock in outgoing methods

	HashMap<CGNode, Boolean> hasLocks = new HashMap<>();
	HashMap<CGNode, Boolean> hasThreads = new HashMap<>();
	//store changed objsig from pta
	HashSet<String> interest_rw = new HashSet<String>();
	HashSet<DLockNode> interest_l = new HashSet<DLockNode>();
	//old objsig  ==> ??
	HashSet<String> removed_rw = new HashSet<String>();
	HashSet<DLockNode> removed_l = new HashSet<DLockNode>();

	public boolean useMayAlias = true;//false => lockObject.size == 1;

	//hard write
	private static Set<String> consideredJDKCollectionClass = HashSetFactory.make();
	//currently considered jdk class
	private static String ARRAYLIST = "<Primordial,Ljava/util/ArrayList>";
	private static String LINKEDLIST = "<Primordial,Ljava/util/LinkedList>";
	private static String HASHSET = "<Primordial,Ljava/util/HashSet>";
	private static String HASHMAP = "<Primordial,Ljava/util/HashMap>";
	private static String ARRAYS = "<Primordial,Ljava/util/Arrays>";
	private static String STRING = "<Primordial,Ljava/util/String>";


	//	for evaluation
	private boolean isdeleting = false;
	public void setDelete(boolean b) {
		isdeleting = b;
	}
	SSAInstruction removeInst = null;





	public TIDEEngine(IPASSAPropagationCallGraphBuilder builder, String entrySignature,CallGraph callGraph, IPAPropagationGraph flowgraph, IPAPointerAnalysisImpl pointerAnalysis, ActorRef bughub){
		this.builder = builder;
		this.callGraph = callGraph;
		this.pointerAnalysis = pointerAnalysis;
		this.maxGraphNodeID = callGraph.getNumberOfNodes() + 1000;
		this.propagationGraph = flowgraph;
		this.bughub = bughub;

		consideredJDKCollectionClass.add(ARRAYLIST);
		consideredJDKCollectionClass.add(LINKEDLIST);
		consideredJDKCollectionClass.add(HASHSET);
		consideredJDKCollectionClass.add(HASHMAP);
		consideredJDKCollectionClass.add(ARRAYS);
		consideredJDKCollectionClass.add(STRING);

		Collection<CGNode> cgnodes = callGraph.getEntrypointNodes();
		for(CGNode n: cgnodes){
			String sig = n.getMethod().getSignature();
			//find the main node
			if(sig.contains(entrySignature)){
				mainEntryNodes.add(n);
			}else{
				TypeName name  = n.getMethod().getDeclaringClass().getName();
				threadSigNodeMap.put(name, n);
			}
		}
	}


	public void detectBothBugs(PrintStream ps) {
		long start = System.currentTimeMillis();

		for(CGNode main: mainEntryNodes){
			twiceProcessedNodes.clear();
			alreadyProcessedNodes.clear();//a new tid
			thirdProcessedNodes.clear();
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
			syncMethods.clear();
			races.clear();
			deadlocks.clear();
			addedraces.clear();
			addeddeadlocks.clear();
			removedraces.clear();
			removeddeadlocks.clear();
			astCGNode_ntid_map.clear();

			shb = new SHBGraph();
			if(mainEntryNodes.size() >1 )
				System.err.println("MORE THAN 1 MAIN ENTRY!");

			//start from the main method
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

			IFile file = null;
			int sourceLineNum = 0;
			try{//get source code line number and ifile of this inst
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
			}catch(Exception e){
				e.printStackTrace();
			}

			StartNode mainstart = new StartNode(-1, mainTID, null, main, sourceLineNum -1, file);
			mapOfStartNode.put(mainTID, mainstart);
			//add edge in shb
			shb.mainCGNode(main);
			shb.addEdge(mainstart, main);

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

				hasSyncBetween = false;
				traverseNode(n);//path insensitive traversal
			}

			//extended happens-before relation
			organizeThreadsRelations();// grand -> parent -> kid threads
			if(mapOfStartNode.size() == 1){
				System.out.println("ONLY HAS MAIN THREAD, NO NEED TO PROCEED:   " + main.getMethod().toString());
				mapOfStartNode.clear();
				mapOfJoinNode.clear();
				continue;
			}else{
				System.out.println("mapOfStartNode =========================");
				for (Integer tid : mapOfStartNode.keySet()) {
					System.out.println(mapOfStartNode.get(tid).toString());
				}
				System.out.println("mapOfJoinNode =========================");
				for (Integer tid : mapOfJoinNode.keySet()) {
					System.out.println(mapOfJoinNode.get(tid).toString());
				}
				System.out.println();
			}

			//race detection
			System.err.println("End stmt traversal: " + (System.currentTimeMillis() - start));
			long local_start = System.currentTimeMillis();
			System.err.println("Start to detect race: find shared variables");
			//organize variable read/write map
			organizeRWMaps();
			//1. find shared variables
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

			System.err.println("End find shared variables: " + (System.currentTimeMillis() - local_start));
			local_start = System.currentTimeMillis();
			System.err.println("Start to detect race: remove local nodes ");

			//2. remove local nodes
			bughub.tell(new RemoveLocalVar(), bughub);
			awaitBugHubComplete();

			System.err.println("End remove local nodes: " + (System.currentTimeMillis() - local_start));
			local_start = System.currentTimeMillis();
			System.err.println("Start to detect race: real detection  " );

			//3. performance race detection with Fork-Join
			bughub.tell(new DistributeDatarace(), bughub);
			awaitBugHubComplete();

			System.err.println("End real detection: " + (System.currentTimeMillis() - local_start));
			local_start = System.currentTimeMillis();
			timeForDetectingRaces = timeForDetectingRaces + (System.currentTimeMillis() - start);
			start = System.currentTimeMillis();

			//detect deadlock
			System.err.println("Start to detect deadlock: real detection ");

			//detect deadlocks
			bughub.tell(new DistributeDeadlock(), bughub);
			awaitBugHubComplete();

			timeForDetectingDL = timeForDetectingDL + (System.currentTimeMillis() -start);
		}

		System.err.println("Total Race Detection Time: " + timeForDetectingRaces);
		System.err.println("Total Deadlock Detection Time: " + timeForDetectingDL);

		races.removeAll(removedraces);
		deadlocks.removeAll(removeddeadlocks);
	}


	/**
	 * collect the rwnode sig from all trace, and count the number : //parallel?
	 */
	private void organizeRWMaps() {
		ArrayList<Trace> alltraces = shb.getAllTraces();
		for (Trace trace : alltraces) {
			singleOrganizeRWMaps(trace);
		}
	}

	/**
	 * sig-tid-num map
	 * @param trace
	 */
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


	private Trace traverseNode(CGNode n) {
		if(n.getIR() == null)
			return null;

		Trace curTrace = shb.getTrace(n);
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

		//create new trace if not in shbgraph
		if(curTrace != null){
			if(!curTrace.doesIncludeTid(curTID)){
				if(change){
					if(curTrace.ifHasJoin() || curTrace.ifHasStart()){
						return traverseNode2nd(curTrace, n);
					}else{
						shb.includeTidForKidTraces(n, curTID);
						return curTrace;
					}
				}else{
					//exist edges include new tid>>
					traverseNode2nd(curTrace, n);
				}
			}
			return curTrace;
		}else{
			if(n instanceof AstCGNode2){
				n = ((AstCGNode2)n).getCGNode();
			}
			curTrace = new Trace(curTID);
		}

		//add back to shb
		shb.addTrace(n, curTrace, curTID);

		addClass2Methods(n);

		SSACFG cfg = n.getIR().getControlFlowGraph();
		HashSet<SSAInstruction> catchinsts = InstInsideCatchBlock(cfg);//won't consider rw,lock related to catch blocks
		SSAInstruction[] insts = n.getIR().getInstructions();
		System.out.println("==== " + n.getMethod().toString());
//		for (int i = 0; i < insts.length; i++) {
//			SSAInstruction ssaInstruction = insts[i];
//			System.out.println(ssaInstruction);
//		}
//		System.out.println();

		for(int i=0; i<insts.length; i++){
			SSAInstruction inst = insts[i];
			if(inst!=null){
				if(catchinsts.contains(inst)){
					continue;
				}
				IMethod method = n.getMethod();
				IFile file = null;
				int sourceLineNum = -1;
				if(!method.isSynthetic()){
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
				}

				if(inst instanceof SSAFieldAccessInstruction){
					processSSAFieldAccessInstruction(n, method, insts, i, inst, sourceLineNum, file, curTrace);
				}else if (inst instanceof SSAArrayReferenceInstruction){
					processSSAArrayReferenceInstruction(n, method, inst, sourceLineNum, file, curTrace);
				}else if (inst instanceof SSAAbstractInvokeInstruction){
					CallSiteReference csr = ((SSAAbstractInvokeInstruction)inst).getCallSite();
					MethodReference mr = csr.getDeclaredTarget();
					IMethod imethod = callGraph.getClassHierarchy().resolveMethod(mr);
					if(imethod != null){
						String sig = imethod.getSignature();
						if(sig.contains("java.util.concurrent") && sig.contains(".submit(Ljava/lang/Runnable;)Ljava/util/concurrent/Future")){
							//Future runnable
							PointerKey key = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, ((SSAAbstractInvokeInstruction) inst).getReceiver());
							OrdinalSet<InstanceKey> instances = pointerAnalysis.getPointsToSet(key);
							for(InstanceKey ins: instances){
								TypeName name = ins.getConcreteType().getName();
								CGNode node = threadSigNodeMap.get(name);
								if(node==null){
									//TODO: find out which runnable object -- need data flow analysis
									int param = ((SSAAbstractInvokeInstruction)inst).getUse(1);
									node = handleRunnable(ins, param, n);
									if(node==null){
										System.err.println("ERROR: starting new thread: "+ name);
										continue;
									}
								}
								System.out.println("Run : " + node.toString());

								processNewThreadInvoke(n, node, imethod, inst, ins, sourceLineNum, file, curTrace,false);
							}
							hasSyncBetween = true;
						}else if(sig.equals("java.lang.Thread.start()V")
								|| (sig.contains("java.util.concurrent") && sig.contains("execute"))){
							//Thread, Executors and ThreadPoolExecutor
							PointerKey key = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, ((SSAAbstractInvokeInstruction) inst).getReceiver());
							OrdinalSet<InstanceKey> instances = pointerAnalysis.getPointsToSet(key);
							for(InstanceKey ins: instances){
								TypeName name = ins.getConcreteType().getName();
								CGNode node = threadSigNodeMap.get(name);
								if(node==null){
									//TODO: find out which runnable object -- need data flow analysis
									int param = ((SSAAbstractInvokeInstruction)inst).getUse(0);
									if(sig.contains("java.util.concurrent") && sig.contains("execute")){
										param = ((SSAAbstractInvokeInstruction)inst).getUse(1);
									}
									node = handleRunnable(ins, param, n);
									if(node==null){
										if(key instanceof LocalPointerKey){
											//class implements runnable
											LocalPointerKey localkey = (LocalPointerKey) key;
											name = localkey.getNode().getMethod().getDeclaringClass().getName();
											node = threadSigNodeMap.get(name);
											if(node == null){
												System.err.println("ERROR: starting new thread: "+ name);
												continue;
											}
										}
									}
								}
								System.out.println("Run : " + node.toString());

								processNewThreadInvoke(n, node, imethod, inst, ins, sourceLineNum, file, curTrace, false);
							}
							hasSyncBetween = true;
						}else if(sig.contains("java.util.concurrent.Future.get()Ljava/lang/Object")){
							//Future join
							PointerKey key = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, ((SSAAbstractInvokeInstruction) inst).getReceiver());
							OrdinalSet<InstanceKey> instances = pointerAnalysis.getPointsToSet(key);
							for(InstanceKey ins: instances){
								TypeName name = ins.getConcreteType().getName();
								CGNode node = threadSigNodeMap.get(name);
								if(node==null){
									//TODO: find out which runnable object -- need data flow analysis
									int param = ((SSAAbstractInvokeInstruction)inst).getUse(0);
									SSAInstruction creation = n.getDU().getDef(param);
									if(creation instanceof SSAAbstractInvokeInstruction){
										param = ((SSAAbstractInvokeInstruction)creation).getUse(1);
										node = handleRunnable(ins, param, n);
										if(node==null){
											System.err.println("ERROR: joining parent thread: "+ name);
											continue;
										}
									}
								}
								System.out.println("Join : " + node.toString());

								processNewThreadJoin(n, node, imethod, inst, ins, sourceLineNum, file, curTrace, false, false);
							}
							hasSyncBetween = true;
						}
						else if(sig.equals("java.lang.Thread.join()V")
								|| (sig.contains("java.util.concurrent") && sig.contains("shutdown()V"))){
							//Executors and ThreadPoolExecutor
							PointerKey key = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, ((SSAAbstractInvokeInstruction) inst).getReceiver());
							OrdinalSet<InstanceKey> instances = pointerAnalysis.getPointsToSet(key);
							for(InstanceKey ins: instances) {
								TypeName name = ins.getConcreteType().getName();
								CGNode node = threadSigNodeMap.get(name);
								boolean isThreadPool = false;
								if(node==null){//could be a runnable class
									int param = ((SSAAbstractInvokeInstruction)inst).getUse(0);
									//Executors and ThreadPoolExecutor
									if(sig.contains("java.util.concurrent") &&sig.contains("shutdown()V")){
										Iterator<SSAInstruction> uses = n.getDU().getUses(param);
										while(uses.hasNext()){
											SSAInstruction use = uses.next();//java.util.concurrent.Executor.execute
											if(use instanceof SSAAbstractInvokeInstruction){
												SSAAbstractInvokeInstruction invoke = (SSAAbstractInvokeInstruction) use;
												CallSiteReference ucsr = ((SSAAbstractInvokeInstruction)invoke).getCallSite();
												MethodReference umr = ucsr.getDeclaredTarget();
												IMethod uimethod = callGraph.getClassHierarchy().resolveMethod(umr);
												String usig = uimethod.getSignature();
												if(usig.contains("java.util.concurrent") &&usig.contains("execute")){
													param = ((SSAAbstractInvokeInstruction)invoke).getUse(1);
													isThreadPool = true;
													break;
												}
											}
										}
									}
									node = handleRunnable(ins,param, n);
									if(node==null){
										if(key instanceof LocalPointerKey){
											//class implements runnable
											LocalPointerKey localkey = (LocalPointerKey) key;
											name = localkey.getNode().getMethod().getDeclaringClass().getName();
											node = threadSigNodeMap.get(name);
											if(node == null){
												System.err.println("ERROR: starting new thread: "+ name);
												continue;
											}
										}
									}
								}
								System.out.println("Join : " + node.toString());

								processNewThreadJoin(n, node, imethod, inst, ins, sourceLineNum, file, curTrace, isThreadPool, false);
							}
							hasSyncBetween = true;
						}else if(sig.equals("java.lang.Thread.<init>(Ljava/lang/Runnable;)V")){
							//for new Thread(new Runnable) => record its initialization
							int use0 = inst.getUse(0);
							threadInits.put(use0, (SSAAbstractInvokeInstruction)inst);
						}else{
							//other method calls
							processNewMethodInvoke(n, csr, inst, sourceLineNum, file, curTrace);
						}
					}
				}else if(inst instanceof SSAMonitorInstruction){
					processSSAMonitorInstruction(n, method, inst, sourceLineNum, file, curTrace);
					hasSyncBetween = true;
				}
			}
		}
		return curTrace;
	}


	private void processNewMethodInvoke(CGNode n, CallSiteReference csr, SSAInstruction inst, int sourceLineNum, IFile file, Trace curTrace) {
		Set<CGNode> set = new HashSet<>();
		if(n instanceof AstCGNode2){
			CGNode temp = n;
			while (temp instanceof AstCGNode2) {
				temp = ((AstCGNode2)temp).getCGNode();
			}
			set = callGraph.getPossibleTargets(temp, csr);
		}else{
			set = callGraph.getPossibleTargets(n, csr);
		}
		for(CGNode node: set){
			IClass declaringclass = node.getMethod().getDeclaringClass();
			if(include(declaringclass)){
				//static method call
				if(node.getMethod().isStatic()){
					//omit the pointer-lock map, use classname as lock obj
					String typeclassname =  n.getMethod().getDeclaringClass().getName().toString();
					String instSig =typeclassname.substring(1)+":"+sourceLineNum;
					String lock = node.getMethod().getDeclaringClass().getName().toString();
					//take out records
					HashSet<DLockNode> currentNodes = threadLockNodes.get(curTID);
					if(currentNodes==null){
						currentNodes = new HashSet<DLockNode>();
						threadLockNodes.put(curTID,currentNodes);
					}
					ArrayList<DLPair> dLLockPairs = threadDLLockPairs.get(curTID);
					if(dLLockPairs==null){
						dLLockPairs = new ArrayList<DLPair>();
						threadDLLockPairs.put(curTID, dLLockPairs);
					}
					DLockNode will = null;
					//if synchronized method, add lock/unlock
					if(node.getMethod().isSynchronized()){
						syncMethods.add(node);
						// for deadlock
						will = new DLockNode(curTID,instSig, sourceLineNum, null, null, n, inst, file);
						will.addLockSig(lock);
						for (DLockNode exist : currentNodes) {
							dLLockPairs.add(new DLPair(exist, will));
						}
						curTrace.add(will);
						threadLockNodes.get(curTID).add(will);
						if(change){
							interest_l.add(will);
						}
					}
					MethodNode m = new MethodNode(n, node, curTID, sourceLineNum, file, (SSAAbstractInvokeInstruction) inst);
					curTrace.add(m);
					if(change){//incremental
						Trace subTrace0 = shb.getTrace(node);
						if(subTrace0 == null){
							subTrace0 = traverseNode(node);
						}else{
							//let curtrace edges include new tids
							shb.includeTidForKidTraces(node, curTID);
						}
						includeTraceToInterestL(node);
						includeTraceToInterestRW(node);
					}else{
						Trace subTrace0 = traverseNode(node);
						shb.includeTidForKidTraces(node, curTID);
					}
					shb.addEdge(m, node);
					if(node.getMethod().isSynchronized()){
						DUnlockNode unlock = new DUnlockNode(curTID, instSig, sourceLineNum, null, null, n, sourceLineNum);
						unlock.addLockSig(lock);
						curTrace.addLockPair(new LockPair(will, unlock));
						//remove
						curTrace.add(unlock);
						threadLockNodes.get(curTID).remove(will);
					}
				}else{
					//instance
					int objectValueNumber = inst.getUse(0);
					PointerKey objectPointer = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, objectValueNumber);
					OrdinalSet<InstanceKey> lockedObjects = pointerAnalysis.getPointsToSet(objectPointer);
					DLockNode will = null;
					if(lockedObjects.size()>0){//must be larger than 0
						//take out records
						HashSet<DLockNode> currentNodes = threadLockNodes.get(curTID);
						if(currentNodes==null){
							currentNodes = new HashSet<DLockNode>();
							threadLockNodes.put(curTID,currentNodes);
						}
						ArrayList<DLPair> dLLockPairs = threadDLLockPairs.get(curTID);
						if(dLLockPairs==null){
							dLLockPairs = new ArrayList<DLPair>();
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
								dLLockPairs.add(new DLPair(exist, will));
							}
							curTrace.add(will);
							threadLockNodes.get(curTID).add(will);
							if(change){
								interest_l.add(will);
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
					}
					MethodNode m = new MethodNode(n, node, curTID, sourceLineNum, file, (SSAAbstractInvokeInstruction) inst);
					curTrace.add(m);
					if(change){
						Trace subTrace1 = shb.getTrace(node);
						if(subTrace1 == null){
							subTrace1 = traverseNode(node);
						}else{
							//let curtrace edges include new tids
							shb.includeTidForKidTraces(node, curTID);
						}
						includeTraceToInterestL(node);
						includeTraceToInterestRW(node);
					}else{
						Trace subTrace1 = traverseNode(node);
						shb.includeTidForKidTraces(node,curTID);
					}
					shb.addEdge(m, node);
					if(lockedObjects.size() > 0){
						if(node.getMethod().isSynchronized()){
							String typeclassname =  n.getMethod().getDeclaringClass().getName().toString();
							String instSig =typeclassname.substring(1)+":"+sourceLineNum;
							DUnlockNode unlock = new DUnlockNode(curTID, instSig, sourceLineNum, objectPointer, lockedObjects, n, sourceLineNum);
							LockPair lockPair = new LockPair(will, unlock);
							curTrace.addLockPair(lockPair);
							for (InstanceKey instanceKey : lockedObjects) {
								String lock = instanceKey.getConcreteType().getName()+"."+instanceKey.hashCode();
								unlock.addLockSig(lock);
							}
							curTrace.add(unlock);
							threadLockNodes.get(curTID).remove(will);
						}
					}
				}
			}
		}
	}


	private void processSSAMonitorInstruction(CGNode n, IMethod method, SSAInstruction inst, int sourceLineNum,
			IFile file, Trace curTrace) {
		SSAMonitorInstruction monitorInstruction = ((SSAMonitorInstruction) inst);
		int lockValueNumber = monitorInstruction.getRef();

		PointerKey lockPointer = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, lockValueNumber);
		OrdinalSet<InstanceKey> lockObjects = pointerAnalysis.getPointsToSet(lockPointer);
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
		ArrayList<DLPair> dlpairs = threadDLLockPairs.get(curTID);
		if(dlpairs==null){
			dlpairs = new ArrayList<DLPair>();
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
				for (Iterator<DLockNode> iterator = currentNodes.iterator(); iterator.hasNext();) {
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
					dlpairs.add(new DLPair(exist, will));
				}
				curTrace.add(will);
				threadLockNodes.get(curTID).add(will);
				if(change){
					interest_l.add(will);
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
				curTrace.addLockPair(new LockPair(will, next));
				threadLockNodes.get(curTID).remove(will);
			}
		}
	}


	private void processNewThreadJoin(CGNode n, CGNode node, IMethod imethod, SSAInstruction inst, InstanceKey ins,
			int sourceLineNum, IFile file, Trace curTrace, boolean isThreadPool, boolean second) {
		//add node to trace
		int tid_child = node.getGraphNodeId();
		if(mapOfJoinNode.containsKey(tid_child)){
			CGNode threadNode = dupStartJoinTidMap.get(tid_child);
			tid_child = threadNode.getGraphNodeId();
			node = threadNode;
		}

		JoinNode jNode = new JoinNode(curTID, tid_child, n, node, sourceLineNum, file);
		if(second){
			curTrace.add2J(jNode, inst, tid_child);
		}else{
			curTrace.addJ(jNode, inst);
		}
		shb.addBackEdge(node, jNode);
		mapOfJoinNode.put(tid_child, jNode);

		boolean isInLoop = isInLoop(n,inst);
		if(isInLoop || isThreadPool){
			AstCGNode2 node2 = n_loopn_map.get(node);//should find created node2 during start
			if(node2 == null){
				node2 = dupStartJoinTidMap.get(tid_child);
				if(node2 == null){
					System.err.println("Null node obtain from n_loopn_map. ");
					return;
				}
			}
			int newID = node2.getGraphNodeId();
			JoinNode jNode2 = new JoinNode(curTID, newID, n, node2, sourceLineNum, file);
			curTrace.add2J(jNode2, inst, newID);//thread id +1
			shb.addBackEdge(node2, jNode2);
			mapOfJoinNode.put(newID, jNode2);
		}
	}


	private void processNewThreadInvoke(CGNode n, CGNode node, IMethod method, SSAInstruction inst, InstanceKey ins, int sourceLineNum, IFile file,
			Trace curTrace, boolean second) {
		boolean scheduled_this_thread = false;
		//duplicate graph node id
		if(stidpool.contains(node.getGraphNodeId())){
			if(threadNodes.contains(node) && scheduledAstNodes.contains(node)){
				//already scheduled to process twice, skip here.
				scheduled_this_thread = true;
			}else{
				scheduledAstNodes.add(node);
				AstCGNode2 threadNode = new AstCGNode2(method, node.getContext());
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
			if(second){//2nd traveral, insert
				curTrace.add2S(startNode, inst, tid_child);
			}else{
				curTrace.addS(startNode, inst, tid_child);
			}
			shb.addEdge(startNode, node);
			mapOfStartNode.put(tid_child, startNode);
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

			boolean isInLoop = isInLoop(n,inst);
			if(isInLoop){
				AstCGNode2 node2 = new AstCGNode2(node.getMethod(),node.getContext());
				threadNodes.add(node2);
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
			}
		}
	}


	private void processSSAArrayReferenceInstruction(CGNode n, IMethod method, SSAInstruction inst, int sourceLineNum,
			IFile file, Trace curTrace) {
		SSAArrayReferenceInstruction arrayRefInst = (SSAArrayReferenceInstruction) inst;
		int	arrayRef = arrayRefInst.getArrayRef();
		String typeclassname =  method.getDeclaringClass().getName().toString();
		String instSig =typeclassname.substring(1)+":"+sourceLineNum;
		PointerKey key = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, arrayRef);
		OrdinalSet<InstanceKey> instances = pointerAnalysis.getPointsToSet(key);
		//		String field = handleArrayTypes(arrayRefInst, n, instances); //currently, won't consider
		String field = "";
		logArrayAccess(inst, sourceLineNum, instSig, curTrace, n, key, instances, file, field);
	}


	private void processSSAFieldAccessInstruction(CGNode n, IMethod method, SSAInstruction[] insts, int i,
			SSAInstruction inst, int sourceLineNum, IFile file, Trace curTrace) {
		if(n.getMethod().isClinit()||n.getMethod().isInit())
			return;
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
								//another field access before monitorenter => we ignore
								//removed node in trace
								curTrace.removeLastNode();
							}
						}
					}
					return;
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
			FieldReference field = ((SSAFieldAccessInstruction)inst).getDeclaredField();
			IField f = builder.getClassHierarchy().resolveField(field);
			if(f == null)
				return;//should not be null
			PointerKey staticPointer = pointerAnalysis.getIPAHeapModel().getPointerKeyForStaticField(f);//builder.getPointerKeyForStaticField(f);
			OrdinalSet<InstanceKey> baseObjects = pointerAnalysis.getPointsToSet(staticPointer);
			logFieldAccess(inst, sourceLineNum, instSig, curTrace, n, staticPointer, baseObjects, sig, file);
		}else{
			int baseValueNumber = ((SSAFieldAccessInstruction)inst).getUse(0);
			PointerKey basePointer = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, baseValueNumber);//+
			OrdinalSet<InstanceKey> baseObjects = pointerAnalysis.getPointsToSet(basePointer);//+
			logFieldAccess(inst, sourceLineNum, instSig, curTrace, n, basePointer, baseObjects, sig, file);
		}
	}

	/**
	 * the jdk classes and method we want to consider
	 * @param declaringclass
	 * @return
	 */
	private boolean include(IClass declaringclass) {
		if(AnalysisUtils.isApplicationClass(declaringclass)){
			return true;
		}else if(AnalysisUtils.isJDKClass(declaringclass)){
			String dcName = declaringclass.toString();
			if(consideredJDKCollectionClass.contains(dcName)){
				return true;
			}
		}
		return false;
	}



	private Trace traverseNode2nd(Trace curTrace, CGNode n) {
		if(n.getIR() == null)
			return curTrace;

		//let curtrace edges include new tids
		boolean includeCurtid = !shb.includeTidForKidTraces(n, curTID);
		//start traverse inst
		SSACFG cfg = n.getIR().getControlFlowGraph();
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
				}catch(Exception e){
					e.printStackTrace();
				}

				if (inst instanceof SSAAbstractInvokeInstruction){
					CallSiteReference csr = ((SSAAbstractInvokeInstruction)inst).getCallSite();
					MethodReference mr = csr.getDeclaredTarget();
					com.ibm.wala.classLoader.IMethod imethod = callGraph.getClassHierarchy().resolveMethod(mr);
					if(imethod!=null){
						String sig = imethod.getSignature();
						if(sig.contains("java.util.concurrent") && sig.contains(".submit(Ljava/lang/Runnable;)Ljava/util/concurrent/Future")){
							//Future runnable
							PointerKey key = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, ((SSAAbstractInvokeInstruction) inst).getReceiver());
							OrdinalSet<InstanceKey> instances = pointerAnalysis.getPointsToSet(key);
							for(InstanceKey ins: instances){
								TypeName name = ins.getConcreteType().getName();
								CGNode node = threadSigNodeMap.get(name);
								if(node==null){
									//TODO: find out which runnable object -- need data flow analysis
									int param = ((SSAAbstractInvokeInstruction)inst).getUse(1);
									node = handleRunnable(ins, param, n);
									if(node==null){
										System.err.println("ERROR: starting new thread: "+ name);
										continue;
									}
								}
								System.out.println("Run : " + node.toString());

								processNewThreadInvoke(n, node, imethod, inst, ins, sourceLineNum, file, curTrace, true);
							}
							hasSyncBetween = true;
						}else if(sig.equals("java.lang.Thread.start()V")
								|| (sig.contains("java.util.concurrent") && sig.contains("execute"))){
							PointerKey key = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, ((SSAAbstractInvokeInstruction) inst).getReceiver());
							OrdinalSet<InstanceKey> instances = pointerAnalysis.getPointsToSet(key);
							for(InstanceKey ins: instances){
								TypeName name = ins.getConcreteType().getName();
								CGNode node = threadSigNodeMap.get(name);
								if(node==null){
									//TODO: find out which runnable object -- need data flow analysis
									int param = ((SSAAbstractInvokeInstruction)inst).getUse(0);
									if(sig.contains("java.util.concurrent") && sig.contains("execute")){
										param = ((SSAAbstractInvokeInstruction)inst).getUse(1);
									}
									node = handleRunnable(ins, param, n);
									if(node==null){
										if(key instanceof LocalPointerKey){
											//class implements runnable
											LocalPointerKey localkey = (LocalPointerKey) key;
											name = localkey.getNode().getMethod().getDeclaringClass().getName();
											node = threadSigNodeMap.get(name);
											if(node == null){
												System.err.println("ERROR: starting new thread: "+ name);
												continue;
											}
										}
									}
								}
								System.out.println("Run : " + node.toString());

								processNewThreadInvoke(n, node, imethod, inst, ins, sourceLineNum, file, curTrace, true);
							}
							hasSyncBetween = true;
						}
						else if(sig.contains("java.util.concurrent.Future.get()Ljava/lang/Object")){
							//Future join
							PointerKey key = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, ((SSAAbstractInvokeInstruction) inst).getReceiver());
							OrdinalSet<InstanceKey> instances = pointerAnalysis.getPointsToSet(key);
							for(InstanceKey ins: instances){
								TypeName name = ins.getConcreteType().getName();
								CGNode node = threadSigNodeMap.get(name);
								if(node==null){
									//TODO: find out which runnable object -- need data flow analysis
									int param = ((SSAAbstractInvokeInstruction)inst).getUse(0);
									SSAInstruction creation = n.getDU().getDef(param);
									if(creation instanceof SSAAbstractInvokeInstruction){
										param = ((SSAAbstractInvokeInstruction)creation).getUse(1);
										node = handleRunnable(ins, param, n);
										if(node==null){
											System.err.println("ERROR: joining parent thread: "+ name);
											continue;
										}
									}
								}
								System.out.println("Join : " + node.toString());

								processNewThreadJoin(n, node, imethod, inst, ins, sourceLineNum, file, curTrace, false, true);
							}
							hasSyncBetween = true;
						}
						else if(sig.equals("java.lang.Thread.join()V")
								|| (sig.contains("java.util.concurrent") && sig.contains("shutdown()V"))){
							PointerKey key = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, ((SSAAbstractInvokeInstruction) inst).getReceiver());
							OrdinalSet<InstanceKey> instances = pointerAnalysis.getPointsToSet(key);
							for(InstanceKey ins: instances){
								TypeName name = ins.getConcreteType().getName();
								CGNode node = threadSigNodeMap.get(name);
								boolean isThreadPool = false;
								if(node==null){//could be a runnable class
									int param = ((SSAAbstractInvokeInstruction)inst).getUse(0);
									//Executors and ThreadPoolExecutor
									if(sig.contains("java.util.concurrent") &&sig.contains("shutdown()V")){
										Iterator<SSAInstruction> uses = n.getDU().getUses(param);
										while(uses.hasNext()){
											SSAInstruction use = uses.next();//java.util.concurrent.Executor.execute
											if(use instanceof SSAAbstractInvokeInstruction){
												SSAAbstractInvokeInstruction invoke = (SSAAbstractInvokeInstruction) use;
												CallSiteReference ucsr = ((SSAAbstractInvokeInstruction)invoke).getCallSite();
												MethodReference umr = ucsr.getDeclaredTarget();
												IMethod uimethod = callGraph.getClassHierarchy().resolveMethod(umr);
												String usig = uimethod.getSignature();
												if(usig.contains("java.util.concurrent") &&usig.contains("execute")){
													param = ((SSAAbstractInvokeInstruction)invoke).getUse(1);
													isThreadPool = true;
													break;
												}
											}
										}
									}
									node = handleRunnable(ins,param, n);
									if(node==null){
										if(key instanceof LocalPointerKey){
											//class implements runnable
											LocalPointerKey localkey = (LocalPointerKey) key;
											name = localkey.getNode().getMethod().getDeclaringClass().getName();
											node = threadSigNodeMap.get(name);
											if(node == null){
												System.err.println("ERROR: starting new thread: "+ name);
												continue;
											}
										}
									}
								}
								System.out.println("Join : " + node.toString());

								processNewThreadJoin(n, node, imethod, inst, ins, sourceLineNum, file, curTrace, isThreadPool, true);
							}
							hasSyncBetween = true;
						}else if(sig.equals("java.lang.Thread.<init>(Ljava/lang/Runnable;)V")){
							//for new Thread(new Runnable)
							int use0 = inst.getUse(0);
							threadInits.put(use0, (SSAAbstractInvokeInstruction)inst);
						}else{
							//other method calls
							Set<CGNode> set = new HashSet<>();
							if(n instanceof AstCGNode2){
								CGNode temp = n;
								while (temp instanceof AstCGNode2) {
									temp = ((AstCGNode2)temp).getCGNode();
								}
								set = callGraph.getPossibleTargets(temp, csr);
							}else{
								set = callGraph.getPossibleTargets(n, csr);
							}
							for(CGNode node: set){
								IClass declaringclass = node.getMethod().getDeclaringClass();
								if(include(declaringclass)){
									if(!includeCurtid){
										shb.includeTidForKidTraces(node, curTID);
									}
								}
							}
						}
					}
				}
			}

		}
		return curTrace;
	}


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

	@SuppressWarnings("rawtypes")
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

	/**
	 * not used => expensive
	 * @param inst
	 * @param anode
	 * @param instances
	 * @return
	 */
	private String handleArrayTypes(SSAArrayReferenceInstruction inst, CGNode anode, OrdinalSet<InstanceKey> instances) {
		int def = inst.getArrayRef();
		String returnValue = "";
		for (InstanceKey instKey : instances) {//size? mutiple => assignment between arrays?
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
			}else{
				System.out.println("CANNOT HANDLE ARRAY: " + inst);
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
		}else if(creation instanceof SSAPhiInstruction){//infinit loop
			SSAPhiInstruction phi = (SSAPhiInstruction) creation;
			int def0 = phi.getUse(0);
			SSAInstruction creation0 = who.getDU().getDef(def0);
			return classifyStmtTypes(creation0, who);
		}
		else{
			System.out.println(creation);
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
		if(instKey instanceof AllocationSiteInNode){
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
				int new_param = findDefsInDataFlowFor(useNode, param, creation.iindex);
				if(new_param != -1){
					node = handleRunnable(instKey, new_param, useNode);
					if(node != null)
						return node;
				}
				if(creation instanceof SSAArrayLoadInstruction){
					new_param = ((SSAArrayLoadInstruction)creation).getArrayRef();
				}
				while (node == null){
					new_param = findDefsInDataFlowFor(useNode, new_param, creation.iindex);
					if(new_param == -1)
					    return null;
					node = handleRunnable(instKey, new_param, useNode);
				}
				return node;
			}
		}
		return null;
	}



	private int findDefsInDataFlowFor(CGNode node, int param, int idx) {
		if(param == -1)
			return -1;
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



	private void logArrayAccess(SSAInstruction inst, int sourceLineNum, String instSig, Trace curTrace, CGNode n,
			PointerKey key, OrdinalSet<InstanceKey> instances, IFile file, String field) {
		String sig = "array.";
		if(inst instanceof SSAArrayLoadInstruction){//read
			ReadNode readNode = new ReadNode(curTID,instSig,sourceLineNum,key, sig, n, inst, file);
			for (InstanceKey instanceKey : instances) {
				String sig2 = sig + instanceKey.hashCode();
				readNode.addObjSig(sig2);
				curTrace.addRsigMapping(sig2, readNode);
				if(change){
					interest_rw.add(sig2);
				}
			}
			readNode.setLocalSig(field);
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
					interest_rw.add(sig2);
				}
			}
			writeNode.setLocalSig(field);
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



	private void logFieldAccess(SSAInstruction inst, int sourceLineNum, String instSig, Trace curTrace, CGNode n,
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
					interest_rw.addAll(sigs);
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
					interest_rw.add(sig);
				}
			}
		}else{//write
			WriteNode writeNode;
			if(key != null){
				for (InstanceKey instanceKey : instances) {
					String sig2 = sig+"."+String.valueOf(instanceKey.hashCode());
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
					interest_rw.addAll(sigs);
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
					interest_rw.add(sig);
				}
			}
		}
	}


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
		this.removedraces.addAll(removes);
	}

	/**
	 * for processIncreRecheckCommonLocks
	 */
	public HashSet<TIDERace> recheckRaces = new HashSet<>();
	public synchronized void addRecheckBugs(MemNode wnode, MemNode xnode) {
		TIDERace recheck = new TIDERace(wnode, xnode);
		if(!recheckRaces.contains(recheck)){
			recheckRaces.add(recheck);
		}
	}


	public synchronized void addBugsBack(HashSet<ITIDEBug> bs) {
		Iterator<ITIDEBug> iterator = bs.iterator();
		while(iterator.hasNext()){
			ITIDEBug _bug = iterator.next();
			if (_bug instanceof TIDEDeadlock) {
				TIDEDeadlock dl = (TIDEDeadlock) _bug;
				boolean iscontain = false;
				Iterator<TIDEDeadlock> iter = deadlocks.iterator();
				while(iter.hasNext()) {
					TIDEDeadlock exist = iter.next();
					if(_bug.equals(exist)){
						iscontain = true;
					}
				}
				if(!iscontain){
					deadlocks.add(dl);
					addeddeadlocks.add(dl);
				}
			}else if(_bug instanceof TIDERace){//race bug:
				boolean iscontain = false;
				TIDERace race = (TIDERace) _bug;
				Iterator<TIDERace> iter = races.iterator();
				while(iter.hasNext()) {
					TIDERace exist = iter.next();
					if(race.equals(exist)){
						iscontain = true;
					}
				}
				if(!iscontain){
					races.add(race);
					addedraces.add(race);
				}
			}
		}
	}


	/**
	 * add/remove synchronized modifier in method
	 * @param node
	 * @param old_trace
	 */
	private void updateLockUnlockForSyncMethod(CGNode node, Trace curTrace) {
		boolean curStatus = false;
		boolean preStatus = false;
		IMethod method = node.getMethod() ;
		if(method.isSynchronized()){
			curStatus = true;
		}
		if(syncMethods.contains(node)){
			preStatus = true;
		}
		//update
		HashSet<INode> inodes = shb.getIncomingSourcesOf(node);
		for (INode inode : inodes) {
			CGNode cgNode = inode.getBelonging();
			Trace trace = shb.getTrace(cgNode);
			int idx_i = trace.indexOf(inode);
			//remove sync
			if(!curStatus && preStatus){
				syncMethods.remove(node);
				INode lock = trace.get(idx_i-1);
				INode unlock = trace.get(idx_i+1);
				trace.remove(lock);
				trace.remove(unlock);
				//trace
				LockPair pair = new LockPair((DLockNode)lock, (DUnlockNode)unlock);
				trace.removeLockPair(pair);
				//deadlock: remove the dlpair that related to trace
				ArrayList<DLPair> removed = new ArrayList<>();
				for (int tid : trace.getTraceTids()) {
					ArrayList<DLPair> threadlocks = threadDLLockPairs.get(tid);
					if(threadlocks == null)//should not??
						continue;
					for (DLPair existPair : threadlocks) {
						DLockNode lockNode = existPair.lock1;
						DLockNode lockNode2 = existPair.lock2;
						if(lockNode.equals(lock) || lockNode2.equals(lock)){
							removed.add(existPair);
							removed_l.add(lockNode);
							removed_l.add(lockNode2);
						}
					}
					threadlocks.removeAll(removed);
					removed.clear();
				}
			}
			//add sync
			if(curStatus && !preStatus){
				syncMethods.add(node);
				//basic info
				MethodNode mNode = (MethodNode) inode;
				SSAAbstractInvokeInstruction inst = mNode.getInvokeInst();
				int sourceLineNum = 0;
				IFile file = null;
				try{//get source code line number of this inst
					if(node.getIR().getMethod() instanceof IBytecodeMethod){
						int bytecodeindex = ((IBytecodeMethod) cgNode.getIR().getMethod()).getBytecodeIndex(inst.iindex);
						sourceLineNum = (int)node.getIR().getMethod().getLineNumber(bytecodeindex);
					}else{
						SourcePosition position = cgNode.getMethod().getSourcePosition(inst.iindex);
						sourceLineNum = position.getFirstLine();
						if(position instanceof JdtPosition){
							file = ((JdtPosition) position).getEclipseFile();
						}
					}
				}catch(Exception e){
					e.printStackTrace();
				}
				String typeclassname = cgNode.getMethod().getDeclaringClass().getName().toString();
				String instSig = typeclassname.substring(1)+":"+sourceLineNum;

				DLockNode will = null;
				DUnlockNode unlock = null;
				ArrayList<Integer> tids = trace.getTraceTids();
				for (Integer tid : tids) {
					//take out records
					HashSet<DLockNode> currentNodes = threadLockNodes.get(tid);
					if(currentNodes==null){
						currentNodes = new HashSet<DLockNode>();
						threadLockNodes.put(tid,currentNodes);
					}
					ArrayList<DLPair> dLLockPairs = threadDLLockPairs.get(tid);
					if(dLLockPairs==null){
						dLLockPairs = new ArrayList<DLPair>();
						threadDLLockPairs.put(tid, dLLockPairs);
					}

					//add lock/unlock nodes
					if(method.isStatic()){
						//use classname as lock obj
						String lock = cgNode.getMethod().getDeclaringClass().getName().toString();
						will = new DLockNode(tid,instSig, sourceLineNum, null, null, cgNode, inst,file);
						will.addLockSig(lock);
						for (DLockNode exist : currentNodes) {
							dLLockPairs.add(new DLPair(exist, will));
						}
						interest_l.add(will);

						unlock = new DUnlockNode(tid, instSig, sourceLineNum, null, null, cgNode, sourceLineNum);
						unlock.addLockSig(lock);
					}else{
						int objectValueNumber = inst.getUse(0);
						PointerKey objectPointer = pointerAnalysis.getHeapModel().getPointerKeyForLocal(cgNode, objectValueNumber);
						OrdinalSet<InstanceKey> lockedObjects = pointerAnalysis.getPointsToSet(objectPointer);
						if(lockedObjects.size() > 0){//must be larger than 0
							//start to record new locks
							will = new DLockNode(tid,instSig, sourceLineNum, objectPointer, lockedObjects, cgNode, inst, file);
							for (InstanceKey key : lockedObjects) {
								String lock = key.getConcreteType().getName()+"."+key.hashCode();
								will.addLockSig(lock);
							}
							// for deadlock
							for (DLockNode exist : currentNodes) {
								dLLockPairs.add(new DLPair(exist, will));
							}
							//for race
							interest_l.add(will);
							//for pointer-lock map
							HashSet<SyncNode> ls = pointer_lmap.get(objectPointer);
							if(ls == null){
								ls = new HashSet<>();
								ls.add(will);
								pointer_lmap.put(objectPointer, ls);
							}else{
								ls.add(will);
							}

							unlock = new DUnlockNode(tid, instSig, sourceLineNum, objectPointer, lockedObjects, cgNode, sourceLineNum);
							for (InstanceKey instanceKey : lockedObjects) {
								String lock = instanceKey.getConcreteType().getName()+"."+instanceKey.hashCode();
								unlock.addLockSig(lock);
							}
						}
					}
				}
				//insert
				if(will != null && unlock != null){
					String m = cgNode.getMethod().getReference().getName().toString();
					if(m.equals("run")){
						if(trace.getContent().contains(will)){
							continue;
						}
					}
					trace.insert(will, unlock, idx_i);
					trace.addLockPair(new LockPair(will, unlock));
					System.out.println(trace.print());
				}
			}
		}
		//consider
		if(recursive){
			recursiveIncludeTraceToInterestedRW(node);
			recursiveIncludeTraceToInterestedL(node);
		}else{
			includeTraceToInterestL(node);
			includeTraceToInterestRW(node);
		}
	}


	//0 -> not change; 1 -> new added; -1 -> new del; 2 -> objchange
	public void updateEngine(HashSet<CGNode> changedNodes, HashSet<CGNode> changedModifiers,
			HashSet<CGNode> updateIRNodes, boolean ptachanges, PrintStream ps) {
		long start_time = System.currentTimeMillis();
		alreadyProcessedNodes.clear();
		twiceProcessedNodes.clear();
		thirdProcessedNodes.clear();
		scheduledAstNodes.clear();
		hasLocks.clear();
		hasThreads.clear();
		interest_l.clear();
		interest_rw.clear();
		removed_l.clear();
		removed_rw.clear();
		addedraces.clear();
		removedraces.clear();
		addeddeadlocks.clear();
		removeddeadlocks.clear();

		this.changedNodes = changedNodes;
		System.out.println("+++++++++++ changed nodes +++++++++++" + changedNodes.size());
		for (CGNode cgNode : changedNodes) {
			System.out.println(cgNode.getMethod().toString());
		}
		System.out.println("+++++++++++ changed modifier +++++++++++" + changedModifiers.size());
		for (CGNode cgNode : changedModifiers) {
			System.out.println(cgNode.getMethod().toString());
		}
		//start to modify engine
		for (CGNode node : changedModifiers) {
			//only consider sync
			Trace old_trace = shb.getTrace(node);
			if(old_trace == null){//shoud not be??
				continue;
			}
			System.out.println("  => old trace is \n" + old_trace.print());
			//compute currentLockednodes, and remove
			computeCurrentLockedNodes(node, old_trace);
			//add lock/unlock for sync method
			updateLockUnlockForSyncMethod(node, old_trace);
		}
		for (CGNode node : changedNodes) {
			Trace trace = shb.getTrace(node);
			if(trace == null){//shoud not be??
				continue;
			}
			System.out.println("  => old trace is \n" + trace.print());
			ArrayList<INode> copy = new ArrayList<>();
			copy.addAll(trace.getContent());
			//remove mapofstartnode inst_start_map threadnodes
			if(trace.ifHasStart() || trace.ifHasJoin()){
				removeKidThreadRelations(trace);
				hasThreads.put(node, true);
				//recursive consider the hb relation in the thread
				for (INode inode : trace.getContent()) {
					if(inode instanceof StartNode){
						StartNode start = (StartNode) inode;
						CGNode target = start.getTarget();
						if(recursive){
							recursiveIncludeTraceToInterestedRW(target);
							recursiveIncludeTraceToInterestedL(target);
						}else{
							includeTraceToInterestL(target);
							includeTraceToInterestRW(target);
						}
					}
				}
			}
			//compute currentLockednodes, and remove
			computeAndRemoveCurrentLockedNodes(node, trace);
			//also remove pointer_lmap and pointer_rwmap
			removeRelatedFromPointerMaps(trace);
			//remove from other maps
			removeRelatedFromRWMaps(node);
			//remove related lock info/ rw mapping
			shb.clearOutgoingEdgesFor(node.getMethod().toString());
			//keep tids, incoming edges
			trace.clearContent();
			//replace with new trace and new relation
			traverseNodeInc(node, trace);
			System.out.println("  => new trace is \n" + trace.print());
			//if calls are removed, check the outgoing nodes and remove them from sigRW
			compareOldNewTraceToUpdateRW(copy, trace.getContent());
		}
		//record memnodes within removed_l/interested_l to recheck their lockset if the lock change is inside changedNode
		considerRWNodesForInterestLock();//should only related to changednodes
		System.out.println("mapofstartnodes after change ====================================");
		for (Integer tid : mapOfStartNode.keySet()) {
			System.out.println(mapOfStartNode.get(tid).toString());
		}
		System.out.println("mapOfJoinNode =========================");
		for (Integer tid : mapOfJoinNode.keySet()) {
			System.out.println(mapOfJoinNode.get(tid).toString());
		}
		System.out.println();

		if(ptachanges){
			updatePTA(changedNodes);
		}

		//redo detection
		detectBothBugsAgain(changedNodes, changedModifiers, start_time, ps);
		//recheck markers in the same class with addedbugs
		if(updateIRNodes.size() > 0){
			updateMemNodeInfo(updateIRNodes);
		}
	}


	private void updateMemNodeInfo(HashSet<CGNode> updateIRNodes) {
		//check bugs
		for (TIDERace race : races) {
			MemNode mem1 = race.node1;
			MemNode mem2 = race.node2;
			CGNode node1 = mem1.getBelonging();
			CGNode node2 = mem2.getBelonging();
			//need to update
			boolean update = false;
			if(updateIRNodes.contains(node1)){
				int line1 = locateLineNumber(node1, mem1.inst);
				if(line1 != mem1.getLine()){
					update = true;
					mem1 = mem1.copy(line1);
				}
			}
			if(updateIRNodes.contains(node2)){
				int line2 = locateLineNumber(node2, mem2.inst);
				if(line2 != mem2.getLine()){
					update = true;
					mem2 = mem2.copy(line2);
				}
			}
			if(update){
				TIDERace new_race = new TIDERace(race.sig, mem1, mem1.getTID(), mem2, mem2.getTID());
				addedraces.add(new_race);
				removedraces.add(race);
			}
		}
		for (TIDEDeadlock dl : deadlocks) {
			DLockNode dl11 = dl.lp1.lock1;
			DLockNode dl12 = dl.lp1.lock2;
			DLockNode dl21 = dl.lp2.lock1;
			DLockNode dl22 = dl.lp2.lock2;
			CGNode node11 = dl11.getBelonging();
			CGNode node12 = dl12.getBelonging();
			CGNode node21 = dl21.getBelonging();
			CGNode node22 = dl22.getBelonging();
			//need to update
			boolean update = false;
			if(updateIRNodes.contains(node11)){
				int l = locateLineNumber(node11, dl11.inst);
				if(l != dl11.getLine()){
					update = true;
					dl11 = dl11.copy(l);
				}
			}
			if(updateIRNodes.contains(node12)){
				int l = locateLineNumber(node12, dl12.inst);
				if(l != dl12.getLine()){
					update = true;
					dl12 = dl12.copy(l);
				}
			}
			if(updateIRNodes.contains(node21)){
				int l = locateLineNumber(node21, dl21.inst);
				if(l != dl21.getLine()){
					update = true;
					dl21 = dl21.copy(l);
				}
			}
			if(updateIRNodes.contains(node22)){
				int l = locateLineNumber(node22, dl22.inst);
				if(l != dl22.getLine()){
					update = true;
					dl22 = dl22.copy(l);
				}
			}
			if(update){
				TIDEDeadlock new_dl = new TIDEDeadlock(new DLPair(dl11, dl12), new DLPair(dl21, dl22));
				addeddeadlocks.add(new_dl);
				removeddeadlocks.add(dl);
			}
		}
		races.addAll(addedraces);
		races.removeAll(removedraces);
		deadlocks.addAll(addeddeadlocks);
		deadlocks.removeAll(removeddeadlocks);
	}


	public int locateLineNumber(CGNode n, SSAInstruction inst){
		IMethod method = n.getMethod();
		int sourceLineNum = -1;
		if(!method.isSynthetic()){
			try{//get source code line number of this inst
				if(n.getIR().getMethod() instanceof IBytecodeMethod){
					int bytecodeindex = ((IBytecodeMethod) n.getIR().getMethod()).getBytecodeIndex(inst.iindex);
					sourceLineNum = (int)n.getIR().getMethod().getLineNumber(bytecodeindex);
				}else{
					SourcePosition position = n.getMethod().getSourcePosition(inst.iindex);
					sourceLineNum = position.getFirstLine();
				}
			}catch(Exception e){
				e.printStackTrace();
			}
		}
		return sourceLineNum;
	}


	/**
	 * if calls are removed, check the outgoing nodes and remove them from sigRW
	 * if calls are remaining, check the outgoing nodes and add them to interest_rw
	 * @param copy
	 * @param content
	 */
	private void compareOldNewTraceToUpdateRW(ArrayList<INode> copy, ArrayList<INode> current) {
		HashSet<MethodNode> oldCalls = filterMethodNodes(copy);
		HashSet<MethodNode> newCalls = filterMethodNodes(current);
		HashSet<MethodNode> remains = new HashSet<>();
		for (MethodNode newCall : newCalls) {
			for (MethodNode oldCall : oldCalls) {
				if(oldCall.generalEqual(newCall)){
					remains.add(oldCall);
					break;
				}
			}
		}
		oldCalls.removeAll(remains);
		//remove them from sigRW
		if(oldCalls.size() > 0){
			for (MethodNode node : oldCalls) {
				CGNode target = node.getTarget();
				HashSet<INode> others = shb.getIncomingSourcesOf(target);
				if(others.size() == 0){
					//recursive remove rwnodes from sig
					if(recursive)
						recursiveRemoveTraceFromSigRW(target);
					else
						removeTraceFromSigRW(target);
				}
			}
		}
		//add them to interest_rw
		if(remains.size() > 0){
			for (MethodNode node : remains) {
				CGNode target = node.getTarget();
				if(recursive)
					recursiveRemoveTraceFromSigRW(target);
				else
					removeTraceFromSigRW(target);
			}
		}
	}

	private HashSet<MethodNode> filterMethodNodes(ArrayList<INode> nodes) {
		HashSet<MethodNode> value = new HashSet<>();
		for (INode node : nodes) {
			if(node instanceof MethodNode){
				value.add((MethodNode) node);
			}
		}
		return value;
	}



	private void considerRWNodesForInterestLock() {
		HashSet<CGNode> involved = new HashSet<>();
		for (DLockNode lock : interest_l) {
			CGNode cgNode = lock.getBelonging();
			Trace trace = shb.getTrace(cgNode);
			ArrayList<LockPair> pairs = trace.getLockPair();
			for (LockPair pair : pairs) {
				DLockNode plock = pair.lock;
				if(plock.equals(lock)){
					DUnlockNode unlock = pair.unlock;
					//locate inbetween nodes, add to interested_rw
					int idx_start = trace.indexOf(lock);
					int idx_end = trace.indexOf(unlock);
					for (int i = idx_start + 1; i < idx_end; i++) {
						INode node = trace.get(i);
						if(node instanceof MemNode){
							MemNode mNode = (MemNode) node;
							HashSet<String> sigs = mNode.getObjSig();
							interest_rw.addAll(sigs);
						}else if(node instanceof MethodNode){//should be recursive
							MethodNode method = (MethodNode) node;
							CGNode tar = method.getTarget();
							if(recursive){
								recursiveIncludeTraceToInterestedRW(tar);
							}else{
								includeTraceToInterestRW(tar);
							}
							involved.add(tar);
						}
					}
				}
			}
		}
		removeBugsRelatedToInterests(involved);
	}



	// ignore method/cgnode
	public void ignoreCGNodes(HashSet<CGNode> ignoreNodes) {
		hasLocks.clear();
		hasThreads.clear();
		interest_l.clear();
		interest_rw.clear();
		removed_l.clear();
		removed_rw.clear();
		addedraces.clear();
		removedraces.clear();
		addeddeadlocks.clear();
		removeddeadlocks.clear();
		System.out.println("+++++++++++ Ignore nodes +++++++++++");
		for (CGNode cgNode : ignoreNodes) {
			System.out.println(cgNode.getMethod().toString());
		}
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
				//recursive consider the hb relation in the thread
				for (INode inode : iTrace.getContent()) {
					if(inode instanceof StartNode){
						StartNode start = (StartNode) inode;
						CGNode target = start.getTarget();
						if(recursive){
							recursiveIncludeTraceToInterestedRW(target);
							recursiveIncludeTraceToInterestedL(target);
						}else{
							includeTraceToInterestL(target);
							includeTraceToInterestRW(target);
						}
					}
				}
			}
			if(iTrace.doesIncludeTid(id)){
				//this is the thread node trace
				removeThisThreadRelations(iTrace);
			}
			//compute currentLockednodes, and remove
			computeAndRemoveCurrentLockedNodes(ignore, iTrace);
			//normal method node => remove related r/w/l
			//remove related lock info/ rw mapping
			//also remove pointer_lmap and pointer_rwmap
			removeRelatedFromPointerMaps(iTrace);
			//remove from other maps
			removeRelatedFromRWMaps(ignore);
			shb.delTrace(ignore);
		}
		//remove related bugs
		removeBugsRelatedToInterests(ignoreNodes);
	}


	public void considerCGNodes(HashSet<CGNode> considerNodes) {
		alreadyProcessedNodes.clear();
		twiceProcessedNodes.clear();
		thirdProcessedNodes.clear();
		scheduledAstNodes.clear();
		hasLocks.clear();
		hasThreads.clear();
		interest_l.clear();
		interest_rw.clear();
		removed_l.clear();
		removed_rw.clear();
		addedraces.clear();
		removedraces.clear();
		addeddeadlocks.clear();
		removeddeadlocks.clear();
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
					cTrace = traverseNode(consider);
				}
				singleOrganizeRWMaps(cTrace);
				//reconnect incoming edges
				for (Trace callerTrace : callerTraces) {
					shb.reconnectIncomingSHBEdgesFor(callerTrace, cTrace, consider);
				}
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

		detectBothBugsAgain(considerNodes, null, System.currentTimeMillis(), null);
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

	/**
	 * for experiement of del inst, do not use
	 * @param changedNodes
	 * @param ptachanges
	 * @param ps
	 * @return
	 */
	public void updateEngineToEvaluate(HashSet<CGNode> changedNodes, boolean ptachanges, SSAInstruction removeInst, PrintStream ps) {
		long start_time = System.currentTimeMillis();
		alreadyProcessedNodes.clear();
		twiceProcessedNodes.clear();
		thirdProcessedNodes.clear();
		scheduledAstNodes.clear();
		hasLocks.clear();
		hasThreads.clear();
		interest_l.clear();
		interest_rw.clear();
		removed_l.clear();
		removed_rw.clear();
		addedraces.clear();
		removedraces.clear();
		addeddeadlocks.clear();
		removeddeadlocks.clear();
		this.removeInst = removeInst;
		for (CGNode node : changedNodes) {
			Trace old_trace = shb.getTrace(node);
			if(old_trace == null){
				continue;
			}
			System.out.println("  => old trace is " + old_trace.getContent().toString());
			//compute currentLockednodes, and remove
			computeAndRemoveCurrentLockedNodes(node, old_trace);
			//remove related lock info/ rw mapping
			shb.clearOutgoingEdgesFor(node.getMethod().toString());
			//remove newruntarget mapofstartnode inst_start_map threadnodes
			if(old_trace.ifHasStart() || old_trace.ifHasJoin()){
				removeKidThreadRelations(old_trace);
				hasThreads.put(node, true);
				//recursive consider the hb relation in the thread
				for (INode inode : old_trace.getContent()) {
					if(inode instanceof StartNode){
						StartNode start = (StartNode) inode;
						CGNode target = start.getTarget();
						if(recursive){
							recursiveIncludeTraceToInterestedRW(target);
							recursiveIncludeTraceToInterestedL(target);
						}else{
							includeTraceToInterestL(target);
							includeTraceToInterestRW(target);
						}
					}
				}
			}
			//also remove pointer_lmap and pointer_rwmap
			removeRelatedFromPointerMaps(old_trace);
			old_trace.clearContent();//keep tids, incoming edges
			//replace with new trace and new relation
			traverseNodeInc(node, old_trace);
			System.out.println("  => new trace is " + old_trace.getContent().toString());
		}

		if(ptachanges){
			updatePTA(changedNodes);
		}

		//redo detection
		detectBothBugsAgain(changedNodes, null, start_time, ps);
		removeInst = null;
	}


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


	private void removeBugsRelatedToInterests(Set<CGNode> keys) {
		HashSet<TIDERace> removedraces = new HashSet<>();
		HashSet<TIDEDeadlock> removeddeadlocks = new HashSet<>();
		for (TIDERace race : races) {
			String racesig = race.initsig;
			if(removed_rw.contains(racesig)){
				removedraces.add(race);
			}else{
				CGNode node1 = race.node1.getBelonging();
				CGNode node2 = race.node2.getBelonging();
				if(keys.contains(node1) || keys.contains(node2)){
					removedraces.add(race);
				}
			}
		}
		for (TIDEDeadlock dl : deadlocks) {
			HashSet<String> dlsigs = dl.getInvolvedSig();
			for (String dlsig : dlsigs) {
				if(removed_l.contains(dlsig)){
					removeddeadlocks.add(dl);
				}else{
					CGNode dl11 = dl.lp1.lock1.getBelonging();
					CGNode dl12 = dl.lp1.lock2.getBelonging();
					CGNode dl21 = dl.lp2.lock1.getBelonging();
					CGNode dl22 = dl.lp2.lock2.getBelonging();
					if(keys.contains(dl11) || keys.contains(dl12) || keys.contains(dl21) || keys.contains(dl22)){
						removeddeadlocks.add(dl);
					}
				}
				if(deadlocks.size() == removeddeadlocks.size()){
					break;
				}
			}
		}
		System.out.println("SIZE of Removed Bugs (in removeBugsRelatedToInterests): " + removedraces.size() + " " + removeddeadlocks.size()) ;

		races.removeAll(removedraces);
		this.removedraces.addAll(removedraces);
		deadlocks.removeAll(removeddeadlocks);
		this.removeddeadlocks.addAll(removeddeadlocks);
	}


	private void removeKidThreadRelations(Trace old_trace) {
		ArrayList<Integer> ptids = old_trace.getTraceTids();
		for (Integer pid : ptids) {//parent id
			//start map
			HashSet<Integer> kids = old_trace.getKidTidFor(pid);//kid id of parent id
			if(kids == null)//no such relation, deleted in previous round
				continue;
			StartNode parentnode = mapOfStartNode.get(pid);
			if(parentnode != null){//should not be null
				for (Integer kid : kids) {
					parentnode.removeChild(kid);
					mapOfStartNode.remove(kid);
					stidpool.remove(kid);
					//join map
					mapOfJoinNode.remove(kid);
				}
			}
		}
	}

	private void removeThisThreadRelations(Trace trace) {
		ArrayList<Integer> tids = trace.getTraceTids();
		for (Integer tid : tids) {//kid
			StartNode snode = mapOfStartNode.get(tid);
			if(snode != null){
				int pid = snode.getParentTID();
				StartNode pnode = mapOfStartNode.get(pid);
				if(pnode != null){
					pnode.removeChild(tid);
				}
			}
			mapOfStartNode.remove(tid);
			mapOfJoinNode.remove(tid);
			stidpool.remove(tid);
		}
	}

	private void computeCurrentLockedNodes(CGNode node, Trace old_trace) {
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
			ArrayList<DLPair> lockorders = threadDLLockPairs.get(tid);
			if(lockorders == null)
				continue;
			for (DLPair order : lockorders) {
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
	}


	private void computeAndRemoveCurrentLockedNodes(CGNode node, Trace old_trace) {
		computeCurrentLockedNodes(node, old_trace);
		//remove the dlpair that related to trace
		ArrayList<DLPair> removed = new ArrayList<>();
		for (int tid : old_trace.getTraceTids()) {
			ArrayList<DLPair> threadlocks = threadDLLockPairs.get(tid);
			if(threadlocks == null)//should not??
				continue;
			for (DLPair pair : threadlocks) {
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
	private void traverseNodeInc(CGNode n, Trace curTrace) {
		if(n.getIR() == null)
			return;

		ArrayList<Integer> tids = curTrace.getTraceTids();
		ArrayList<Integer> oldkids = new ArrayList<>();
		oldkids.addAll(curTrace.getOldKids());
		HashMap<Integer, Integer> oldkid_line_map = new HashMap<>();
		oldkid_line_map.putAll(curTrace.getOldkidsMap());
		ArrayList<Integer> dupkids = new ArrayList<>();

		curTID = tids.get(0);
		stidpool.add(curTID);

		addClass2Methods(n);

		//start traverse inst
		SSACFG cfg = n.getIR().getControlFlowGraph();
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

				if(inst instanceof SSAFieldAccessInstruction){
					processSSAFieldAccessInstruction(n, method, insts, i, inst, sourceLineNum, file, curTrace);
				}else if (inst instanceof SSAArrayReferenceInstruction){
					processSSAArrayReferenceInstruction(n, method, inst, sourceLineNum, file, curTrace);
				}else if (inst instanceof SSAAbstractInvokeInstruction){
					CallSiteReference csr = ((SSAAbstractInvokeInstruction)inst).getCallSite();
					MethodReference mr = csr.getDeclaredTarget();
					com.ibm.wala.classLoader.IMethod imethod = callGraph.getClassHierarchy().resolveMethod(mr);
					if(imethod!=null){
						String sig = imethod.getSignature();
						//System.out.println("Invoke Inst: "+sig);
						if(sig.contains("java.util.concurrent") && sig.contains(".submit(Ljava/lang/Runnable;)Ljava/util/concurrent/Future")){
							//Future runnable
							PointerKey key = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, ((SSAAbstractInvokeInstruction) inst).getReceiver());
							OrdinalSet<InstanceKey> instances = pointerAnalysis.getPointsToSet(key);
							for(InstanceKey ins: instances){
								TypeName name = ins.getConcreteType().getName();
								CGNode node = threadSigNodeMap.get(name);
								if(node==null){
									//TODO: find out which runnable object -- need data flow analysis
									int param = ((SSAAbstractInvokeInstruction)inst).getUse(1);
									node = handleRunnable(ins, param, n);
									if(node==null){
										System.err.println("ERROR: starting new thread: "+ name);
										continue;
									}
								}
								System.out.println("Run : " + node.toString());

								processNewThreadInvokeIncremental(n, node, imethod, inst, ins, sourceLineNum, file, curTrace, oldkids, oldkid_line_map, dupkids, false);
							}
							hasSyncBetween = true;
						}else if(sig.equals("java.lang.Thread.start()V")
								|| (sig.contains("java.util.concurrent") && sig.contains("execute"))){
							PointerKey key = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, ((SSAAbstractInvokeInstruction) inst).getReceiver());
							OrdinalSet<InstanceKey> instances = pointerAnalysis.getPointsToSet(key);
							for(InstanceKey ins: instances){
								TypeName name = ins.getConcreteType().getName();
								CGNode node = threadSigNodeMap.get(name);
								if(node==null){
									//TODO: find out which runnable object -- need data flow analysis
									int param = ((SSAAbstractInvokeInstruction)inst).getUse(0);
									if(sig.contains("java.util.concurrent") && sig.contains("execute")){
										param = ((SSAAbstractInvokeInstruction)inst).getUse(1);
									}
									node = handleRunnable(ins, param, n);
									if(node==null){
										if(key instanceof LocalPointerKey){
											//class implements runnable
											LocalPointerKey localkey = (LocalPointerKey) key;
											name = localkey.getNode().getMethod().getDeclaringClass().getName();
											node = threadSigNodeMap.get(name);
											if(node == null){
												System.err.println("ERROR: starting new thread: "+ name);
												continue;
											}
										}
									}
								}
								System.out.println("Run : " + node.toString());

								//duplicate graph node id or existing node with trace?
								processNewThreadInvokeIncremental(n, node, imethod, inst, ins, sourceLineNum, file, curTrace, oldkids, oldkid_line_map, dupkids, false);
							}
							hasSyncBetween = true;
						}
						else if(sig.contains("java.util.concurrent.Future.get()Ljava/lang/Object")){
							//Future join
							PointerKey key = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, ((SSAAbstractInvokeInstruction) inst).getReceiver());
							OrdinalSet<InstanceKey> instances = pointerAnalysis.getPointsToSet(key);
							for(InstanceKey ins: instances){
								TypeName name = ins.getConcreteType().getName();
								CGNode node = threadSigNodeMap.get(name);
								if(node==null){
									//TODO: find out which runnable object -- need data flow analysis
									int param = ((SSAAbstractInvokeInstruction)inst).getUse(0);
									SSAInstruction creation = n.getDU().getDef(param);
									if(creation instanceof SSAAbstractInvokeInstruction){
										param = ((SSAAbstractInvokeInstruction)creation).getUse(1);
										node = handleRunnable(ins, param, n);
										if(node==null){
											System.err.println("ERROR: joining parent thread: "+ name);
											continue;
										}
									}
								}
								System.out.println("Join : " + node.toString());

								processNewThreadJoin(n, node, imethod, inst, ins, sourceLineNum, file, curTrace, false, false);
							}
							hasSyncBetween = true;
						}
						else if(sig.equals("java.lang.Thread.join()V")
								|| (sig.contains("java.util.concurrent") && sig.contains("shutdown()V"))){
							PointerKey key = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, ((SSAAbstractInvokeInstruction) inst).getReceiver());
							OrdinalSet<InstanceKey> instances = pointerAnalysis.getPointsToSet(key);
							for(InstanceKey ins: instances){
								TypeName name = ins.getConcreteType().getName();
								CGNode node = threadSigNodeMap.get(name);
								boolean isThreadPool = false;
								if(node==null){//could be a runnable class
									int param = ((SSAAbstractInvokeInstruction)inst).getUse(0);
									//Executors and ThreadPoolExecutor
									if(sig.contains("java.util.concurrent") &&sig.contains("shutdown()V")){
										Iterator<SSAInstruction> uses = n.getDU().getUses(param);
										while(uses.hasNext()){
											SSAInstruction use = uses.next();//java.util.concurrent.Executor.execute
											if(use instanceof SSAAbstractInvokeInstruction){
												SSAAbstractInvokeInstruction invoke = (SSAAbstractInvokeInstruction) use;
												CallSiteReference ucsr = ((SSAAbstractInvokeInstruction)invoke).getCallSite();
												MethodReference umr = ucsr.getDeclaredTarget();
												com.ibm.wala.classLoader.IMethod uimethod = callGraph.getClassHierarchy().resolveMethod(umr);
												String usig = uimethod.getSignature();
												if(usig.contains("java.util.concurrent") &&usig.contains("execute")){
													param = ((SSAAbstractInvokeInstruction)invoke).getUse(1);
													isThreadPool = true;
													break;
												}
											}
										}
									}
									node = handleRunnable(ins,param, n);
									if(node==null){
										if(key instanceof LocalPointerKey){
											//class implements runnable
											LocalPointerKey localkey = (LocalPointerKey) key;
											name = localkey.getNode().getMethod().getDeclaringClass().getName();
											node = threadSigNodeMap.get(name);
											if(node == null){
												System.err.println("ERROR: starting new thread: "+ name);
												continue;
											}
										}
									}
								}
								System.out.println("Join : " + node.toString());

								processNewThreadJoin(n, node, imethod, inst, ins, sourceLineNum, file, curTrace, false, false);
							}
							hasSyncBetween = true;
						}else if(sig.equals("java.lang.Thread.<init>(Ljava/lang/Runnable;)V")){
							//for new Thread(new Runnable)
							int use0 = inst.getUse(0);
							threadInits.put(use0, (SSAAbstractInvokeInstruction)inst);
						}else{
							//other method calls
							processNewMethodInvoke(n, csr, inst, sourceLineNum, file, curTrace);
						}
					}
				}else if(inst instanceof SSAMonitorInstruction){
					processSSAMonitorInstruction(n, method, inst, sourceLineNum, file, curTrace);
					hasSyncBetween = true;
				}
			}
		}

		for (int tid : tids) {
			//threads with same instructions, adding nodes to curTrace with other tids
			if(tid != curTID){
				traverseNodeInc2(n, curTrace, tid);
			}
		}

		//if has new threadnode; traverse all its instructions as the first time
		while(!threadNodes.isEmpty()){
			CGNode newnode = threadNodes.removeFirst();
			int newID = newnode.getGraphNodeId();
			if(mapOfStartNode.get(newID) == null){
				//no such node, should be created and added before, should not be??
				System.err.println("thread " + newID + " is missing from mapOfStartNode");
			}
			curTID = newID;
			hasSyncBetween = false;
			traverseNode(newnode);
		}

		//if old kids has left => the thread should have been removed earlier
		if(oldkids.size() > 0){
			for (int oldkid : oldkids) {
				System.err.println("thread " + oldkid + " should have been removed earlier");
			}
		}
		if(dupkids.size() > 0){
			for (int dupkid : dupkids) {
				System.err.println("thread " + dupkid + " should not have duplicate tids");
			}
		}
		//add back to shb
		shb.replaceTrace(n, curTrace);
		//organize wtid num map/rwsigmap
		singleOrganizeRWMaps(curTrace);
	}


	private void processNewThreadInvokeIncremental(CGNode n, CGNode node, IMethod imethod, SSAInstruction inst,
			InstanceKey ins, int sourceLineNum, IFile file, Trace curTrace, ArrayList<Integer> oldkids,
			HashMap<Integer, Integer> oldkid_line_map, ArrayList<Integer> dupkids, boolean second) {
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
				node = threadNode;
			}
		}else{//new thread, may exist
			exist = shb.getTrace(node);
		}
		if(exist == null){//new threadnode
			threadNodes.add(node);
		}else{
			oldkids.remove(oldkids.indexOf(tempid));
		}
		int tid_child = node.getGraphNodeId();
		stidpool.add(tid_child);
		//add node to trace
		StartNode startNode = new StartNode(curTID, tid_child, n, node, sourceLineNum, file);
		if(second){
			curTrace.add2S(startNode, inst, tid_child);
		}else{
			curTrace.addS(startNode, inst, tid_child);
		}
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
		}
	}


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



	private void traverseNodeInc2(CGNode n, Trace curTrace, int localtid) {
		if(n.getIR() == null)
			return;

		stidpool.add(localtid);
		ArrayList<Integer> oldkids = new ArrayList<>();
		oldkids.addAll(curTrace.getOldKids());
		HashMap<Integer, Integer> oldkid_line_map = new HashMap<>();
		oldkid_line_map.putAll(curTrace.getOldkidsMap());
		ArrayList<Integer> dupkids = new ArrayList<>();

		//let curtrace edges include new tids
		HashSet<SHBEdge> edges = shb.getOutGoingEdgesOf(n.getMethod().toString());
		for (SHBEdge edge : edges) {
			edge.includeTid(localtid);
		}
		//start traverse inst
		SSACFG cfg = n.getIR().getControlFlowGraph();
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
					IMethod imethod = callGraph.getClassHierarchy().resolveMethod(mr);
					if(imethod!=null){
						String sig = imethod.getSignature();
						if(sig.contains("java.util.concurrent") && sig.contains(".submit(Ljava/lang/Runnable;)Ljava/util/concurrent/Future")){
							//Future runnable
							PointerKey key = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, ((SSAAbstractInvokeInstruction) inst).getReceiver());
							OrdinalSet<InstanceKey> instances = pointerAnalysis.getPointsToSet(key);
							for(InstanceKey ins: instances){
								TypeName name = ins.getConcreteType().getName();
								CGNode node = threadSigNodeMap.get(name);
								if(node==null){
									//TODO: find out which runnable object -- need data flow analysis
									int param = ((SSAAbstractInvokeInstruction)inst).getUse(1);
									node = handleRunnable(ins, param, n);
									if(node==null){
										System.err.println("ERROR: starting new thread: "+ name);
										continue;
									}
								}

								processNewThreadInvokeIncremental(n, node, imethod, inst, ins, sourceLineNum, file, curTrace, oldkids, oldkid_line_map, dupkids, true);
							}
							//find loops in this method!!
							//node.getIR().getControlFlowGraph();
							hasSyncBetween = true;
						}else if(sig.equals("java.lang.Thread.start()V")
								|| (sig.contains("java.util.concurrent") && sig.contains("execute"))){
							PointerKey key = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, ((SSAAbstractInvokeInstruction) inst).getReceiver());
							OrdinalSet<InstanceKey> instances = pointerAnalysis.getPointsToSet(key);
							for(InstanceKey ins: instances){
								TypeName name = ins.getConcreteType().getName();
								CGNode node = threadSigNodeMap.get(name);
								if(node==null){
									//TODO: find out which runnable object -- need data flow analysis
									int param = ((SSAAbstractInvokeInstruction)inst).getUse(0);
									if(sig.contains("java.util.concurrent") && sig.contains("execute")){
										param = ((SSAAbstractInvokeInstruction)inst).getUse(1);
									}
									node = handleRunnable(ins, param, n);
									if(node==null){
										if(key instanceof LocalPointerKey){
											//class implements runnable
											LocalPointerKey localkey = (LocalPointerKey) key;
											name = localkey.getNode().getMethod().getDeclaringClass().getName();
											node = threadSigNodeMap.get(name);
											if(node == null){
												System.err.println("ERROR: starting new thread: "+ name);
												continue;
											}
										}
									}
								}

								processNewThreadInvokeIncremental(n, node, imethod, inst, ins, sourceLineNum, file, curTrace, oldkids, oldkid_line_map, dupkids, true);
							}
							hasSyncBetween = true;
						}
						else if(sig.contains("java.util.concurrent.Future.get()Ljava/lang/Object")){
							//Future join
							PointerKey key = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, ((SSAAbstractInvokeInstruction) inst).getReceiver());
							OrdinalSet<InstanceKey> instances = pointerAnalysis.getPointsToSet(key);
							for(InstanceKey ins: instances){
								TypeName name = ins.getConcreteType().getName();
								CGNode node = threadSigNodeMap.get(name);
								if(node==null){
									//TODO: find out which runnable object -- need data flow analysis
									int param = ((SSAAbstractInvokeInstruction)inst).getUse(0);
									SSAInstruction creation = n.getDU().getDef(param);
									if(creation instanceof SSAAbstractInvokeInstruction){
										param = ((SSAAbstractInvokeInstruction)creation).getUse(1);
										node = handleRunnable(ins, param, n);
										if(node==null){
											System.err.println("ERROR: joining parent thread: "+ name);
											continue;
										}
									}
								}

								processNewThreadJoin(n, node, imethod, inst, ins, sourceLineNum, file, curTrace, false, true);
							}
							hasSyncBetween = true;
						}else if(sig.equals("java.lang.Thread.join()V")
								|| (sig.contains("java.util.concurrent") && sig.contains("shutdown()V"))){
							PointerKey key = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, ((SSAAbstractInvokeInstruction) inst).getReceiver());
							OrdinalSet<InstanceKey> instances = pointerAnalysis.getPointsToSet(key);
							for(InstanceKey ins: instances){
								TypeName name = ins.getConcreteType().getName();
								CGNode node = threadSigNodeMap.get(name);
								boolean isThreadPool = false;
								if(node==null){//could be a runnable class
									int param = ((SSAAbstractInvokeInstruction)inst).getUse(0);
									//Executors and ThreadPoolExecutor
									if(sig.contains("java.util.concurrent") &&sig.contains("shutdown()V")){
										Iterator<SSAInstruction> uses = n.getDU().getUses(param);
										while(uses.hasNext()){
											SSAInstruction use = uses.next();//java.util.concurrent.Executor.execute
											if(use instanceof SSAAbstractInvokeInstruction){
												SSAAbstractInvokeInstruction invoke = (SSAAbstractInvokeInstruction) use;
												CallSiteReference ucsr = ((SSAAbstractInvokeInstruction)invoke).getCallSite();
												MethodReference umr = ucsr.getDeclaredTarget();
												IMethod uimethod = callGraph.getClassHierarchy().resolveMethod(umr);
												String usig = uimethod.getSignature();
												if(usig.contains("java.util.concurrent") &&usig.contains("execute")){
													param = ((SSAAbstractInvokeInstruction)invoke).getUse(1);
													isThreadPool = true;
													break;
												}
											}
										}
									}
									node = handleRunnable(ins,param, n);
									if(node==null){
										if(key instanceof LocalPointerKey){
											//class implements runnable
											LocalPointerKey localkey = (LocalPointerKey) key;
											name = localkey.getNode().getMethod().getDeclaringClass().getName();
											node = threadSigNodeMap.get(name);
											if(node == null){
												System.err.println("ERROR: starting new thread: "+ name);
												continue;
											}
										}
									}
								}
								processNewThreadJoin(n, node, imethod, inst, ins, sourceLineNum, file, curTrace, isThreadPool, true);
							}
							hasSyncBetween = true;
						}else if(sig.equals("java.lang.Thread.<init>(Ljava/lang/Runnable;)V")){
							//for new Thread(new Runnable)
							int use0 = inst.getUse(0);
							threadInits.put(use0, (SSAAbstractInvokeInstruction)inst);
						}else{
							//other method calls
							Set<CGNode> set = new HashSet<>();
							if(n instanceof AstCGNode2){
								CGNode temp = n;
								while (temp instanceof AstCGNode2) {
									temp = ((AstCGNode2)temp).getCGNode();
								}
								set = callGraph.getPossibleTargets(temp, csr);
							}else{
								set = callGraph.getPossibleTargets(n, csr);
							}
							for(CGNode node: set){
								IClass declaringclass = node.getMethod().getDeclaringClass();
								if(include(declaringclass)){
									shb.includeTidForKidTraces(node, localtid);
								}
							}
						}
					}
				}
			}
		}
		if(dupkids.size() > 0){
			for (int dupkid : dupkids) {
				mapOfStartNode.remove(dupkid);
				stidpool.remove(dupkid);
				shb.removeTidFromAllTraces(n, dupkid);
			}
		}
	}



	private void updatePTA(Set<CGNode> keys) {
		//////we did not replace siglist in nodes
		HashSet<IVariable> changes = IPAAbstractFixedPointSolver.changes;
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
					//old remove rw sig tid num map (global); sig rw map
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
					interest_rw.addAll(new_sigs);
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
					String prefix = lock.getPrefix();
					//new sigs
					HashSet<String> new_sigs = new HashSet<>();
					for (InstanceKey instanceKey : newobjects) {
						String new_sig = prefix + "." + String.valueOf(instanceKey.hashCode());
						new_sigs.add(new_sig);
					}
					//replace
					lock.replaceLockSig(new_sigs);
					interest_l.add(lock);
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
		if(hasThreads.size() != 0){
			lock = true;
			race = true;
		}
		if(hasLocks.size() != 0 || interest_l.size() != 0 || removed_l.size() != 0){
			lock = true;
		}
		if(interest_rw.size() != 0 || lock){
			race = true;
		}
		//remove bugs related to interests
		removeBugsRelatedToInterests(changedNodes);
		if(changedModifiers != null){
			removeBugsRelatedToInterests(changedModifiers);
		}
		//start
		organizeThreadsRelations();
		//lock
		if(lock){
			recheckLock();
		}
		//race: any races between deadlocks will not be reported.
		if(race){
			recheckRace();
		}
		//        ps.print(incre_race_time+" "+incre_dl_time+" ");

		System.out.println("Removed bugs ============================ " + removedraces.size() + " " + removeddeadlocks.size());
		System.out.println("Added bugs ============================ " + addedraces.size() + " " + addeddeadlocks.size());

//		races.removeAll(removedraces);
//		deadlocks.removeAll(removeddeadlocks);
	}



	public void recursiveIncludeTraceToInterestedL(CGNode root){
		HashSet<CGNode> sinks = shb.getOutGoingSinksOf(root);
		HashSet<CGNode> temps = new HashSet<>();
		temps.addAll(sinks);
		while (temps.size() > 0) {
			for (CGNode temp : temps) {
				includeTraceToInterestL(temp);
				HashSet<CGNode> next_sinks = shb.getOutGoingSinksOf(temp);
				sinks.addAll(next_sinks);
			}
			temps.clear();
			temps.addAll(sinks);
			sinks.clear();
		}
	}


	private void includeTraceToInterestL(CGNode target) {
		Trace trace = shb.getTrace(target);
		if(trace == null){
			if(target instanceof AstCGNode2){
				trace = shb.getTrace(((AstCGNode2) target).getCGNode());
			}
		}
		for (INode inode : trace.getContent()) {
			if(inode instanceof DLockNode){
				interest_l.add((DLockNode) inode);
			}
		}
	}

	public void recursiveIncludeTraceToInterestedRW(CGNode root){
		HashSet<CGNode> sinks = shb.getOutGoingSinksOf(root);
		HashSet<CGNode> temps = new HashSet<>();
		temps.addAll(sinks);
		while (temps.size() > 0) {
			for (CGNode temp : temps) {
				includeTraceToInterestRW(temp);
				HashSet<CGNode> next_sinks = shb.getOutGoingSinksOf(temp);
				sinks.addAll(next_sinks);
			}
			removeBugsRelatedToInterests(temps);
			temps.clear();
			temps.addAll(sinks);
			sinks.clear();
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
		if(rMap != null)
			if(rMap.size() > 0){
				Set<String> rsig = rMap.keySet();
				interest_rw.addAll(rsig);
			}
		HashMap<String, ArrayList<WriteNode>> wMap = trace.getWsigMapping();
		if(wMap != null)
			if(wMap.size() > 0){
				Set<String> wsig = wMap.keySet();//can be reduced to smaller range
				interest_rw.addAll(wsig);
			}
	}

	/**
	 * to remove recursive rw in start/join/sync method/method, may cause FPs
	 * @param root
	 */
	public void recursiveRemoveTraceFromSigRW(CGNode root){
		HashSet<CGNode> sinks = shb.getOutGoingSinksOf(root);
		HashSet<CGNode> temps = new HashSet<>();
		temps.addAll(sinks);
		while (temps.size() > 0) {
			sinks.clear();
			for (CGNode temp : temps) {
				removeTraceFromSigRW(temp);
				HashSet<CGNode> next_sinks = shb.getOutGoingSinksOf(temp);
				sinks.addAll(next_sinks);
			}
			temps.clear();
			temps.addAll(sinks);
		}
	}

	private void removeTraceFromSigRW(CGNode node) {
		Trace trace = shb.getTrace(node);
		if(trace == null){
			if(node instanceof AstCGNode2){
				trace = shb.getTrace(((AstCGNode2) node).getCGNode());
			}
		}

		HashMap<String, ArrayList<ReadNode>> rMap = trace.getRsigMapping();
		if(rMap != null){
			if(rMap.size() > 0){
				for (String sig : rMap.keySet()) {
					ArrayList<ReadNode> map = rMap.get(sig);
					HashSet<ReadNode> exist = sigReadNodes.get(sig);
					if(exist != null){
						exist.removeAll(map);
						removed_rw.add(sig);
					}
				}
			}
		}
		HashMap<String, ArrayList<WriteNode>> wMap = trace.getWsigMapping();
		if(wMap != null){
			if(wMap.size() > 0){
				for (String sig : wMap.keySet()) {
					ArrayList<WriteNode> map = wMap.get(sig);
					HashSet<WriteNode> exist = sigWriteNodes.get(sig);
					if(exist != null){
						exist.removeAll(map);
						removed_rw.add(sig);
					}
				}
			}
		}
	}




	private void recheckRace() {
		System.err.println("Start to detect races INCREMENTALLLy: ");
		//1. find shared vars
		HashSet<String> new_sharedFields = new HashSet<>();
		//seq
		for (String sig : interest_rw) {
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

		//2. remove local vars  => for incremental change check, not for functionalities
		bughub.tell(new IncreRemoveLocalVar(new_sharedFields), bughub);
		awaitBugHubComplete();

		//3. perform race detection
		bughub.tell(new IncrementalCheckDatarace(new_sharedFields), bughub);//interest_rw?
		awaitBugHubComplete();

//		//4. recheck existing races that have been protected by common locks
//		bughub.tell(new IncrementalRecheckCommonLock(), bughub);
//		awaitBugHubComplete();

		recheckRaces.clear();
	}



	private void recheckLock() {
		System.err.println("Start to detect deadlock INCREMENTALLLy:");
		bughub.tell(new IncrementalDeadlock(interest_l), bughub);
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



	public HashSet<TIDERace> considerThisSigForRace(String considerSig) {
		interest_rw.clear();
		interest_l.clear();
		addedraces.clear();
		removedraces.clear();
		excludedSigForRace.remove(considerSig);
		collectRWSigToRWMaps(considerSig);
		interest_rw.add(considerSig);
		recheckRace();
		races.addAll(addedraces);
		return addedraces;
	}

	public HashSet<TIDERace> excludeThisSigForRace(TIDERace race, String excludedSig) {
		excludedSigForRace.add(excludedSig);
		//remove from current storage
		//		sigReadNodes.remove(excludedSig);
		//		sigWriteNodes.remove(excludedSig);
		removeRWSigFromRWMaps(excludedSig);
		races.remove(race);//remove the specific one
		return removeBugsRelatedToSig(excludedSig);//remove all the race realted to the sig
	}

	public HashSet<TIDERace> considerThisSigForRace(TIDERace race, String considerSig) {
		// should not be called, since no race should exist.
		return considerThisSigForRace(considerSig);
	}


	public void excludeThisSigForRace(HashSet<TIDERace> races, String excludedSig) {
		excludedSigForRace.add(excludedSig);
		//remove from current storage
		//		sigReadNodes.remove(excludedSig);
		//		sigWriteNodes.remove(excludedSig);
		removeRWSigFromRWMaps(excludedSig);
		races.remove(races);//remove the specific ones
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
		for (TIDERace race : races) {
			if(race.sig.equals(sig)){
				set.add(race);
			}
		}
		races.removeAll(set);
		return set;
	}



}