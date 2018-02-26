package edu.tamu.aser.tide.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSAGotoInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSAMonitorInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.intset.OrdinalSet;

public class TIDELockEngine {

	protected CallGraph callGraph;
	protected PointerAnalysis<InstanceKey> pointerAnalysis;

	private HashSet<CGNode> alreadyProcessedNodes = new HashSet<CGNode>();
	private LinkedList<CGNode> threadNodes = new LinkedList<CGNode>();
	private LinkedList<CGNode> twiceProcessedNodes = new LinkedList<CGNode>();
	private HashMap<TypeName, CGNode> threadSigNodeMap = new HashMap<TypeName, CGNode>();
	private LinkedList<CGNode> twiceProcessedMethods = new LinkedList<CGNode>();

	private HashSet<CGNode> alreadyProcessedMethods = new HashSet<CGNode>();
	private HashMap<Integer, HashSet<Locknode>> lockNodes = new HashMap<>();

	public int curTID;
	public HashMap<Integer, HashMap<Locknode, HashSet<Locknode>>> dependencygraph = new HashMap<Integer, HashMap<Locknode, HashSet<Locknode>>>();
	public HashMap<Integer, HashMap<SSAInstruction, Integer>> happensbeforegraph = new HashMap<Integer, HashMap<SSAInstruction, Integer>>();
//	public HashSet<Locknode> currentHoldLock = new HashSet<Locknode>();
	public HashSet<ISSABasicBlock> traversedblock = new HashSet<>();
	public HashSet<ISSABasicBlock> donotfurther = new HashSet<>();
//	public HashSet<SSAInstruction> newInstructions = new HashSet<>();
//	public HashMap<Integer, SSAInstruction> newInstructions = new HashMap<>();
//	public HashMap<Integer, SSAInstruction> initialInstructions = new HashMap<>();
	private int newinvokeindex;
	private boolean rememberinvoke = false;
	private HashSet<Integer> tidpool = new HashSet<>();
	private HashMap<CGNode, CGNode> newRunTargets = new HashMap<>();
	private HashSet<SSAInstruction> intermedia = new HashSet<>();
	private TypeName globalTypename;
	private HashMap<CGNode, Integer> twiceTraversedInvoke = new HashMap<>();
//	private boolean is_1st_functioncall= false;
//	private boolean is_2nd_functioncall = false;
//	private LinkedList<ISSABasicBlock> nonTravereledBraches = new LinkedList<>();
//	private LinkedList<ISSABasicBlock> nonTraLoopBraches = new LinkedList<>();

	private boolean hasSyncBetween = false;
	private HashMap<CGNode, Integer> traversedTimes = new HashMap<>();
	private HashMap<Integer, CGNode> idnode = new HashMap<>();
	private LinkedList<CGNode> mainEntryNodes = new LinkedList<CGNode>();
//	private HashMap<CGNode, Integer> traversedAst = new HashMap<>();
	private HashSet<CGNode> alreadyProcessedAst = new HashSet<CGNode>();



	private HashMap<Integer, ThreadInfo> threads= new HashMap<>();
	private int sigID;

	public TIDELockEngine(String entrySignature, CallGraph callGraph, PointerAnalysis<InstanceKey> pointerAnalysis) {
			this.callGraph = callGraph;
			this.pointerAnalysis = pointerAnalysis;

			Collection<CGNode> cgnodes = callGraph.getEntrypointNodes();
			for (CGNode n : cgnodes) {
				String sig = n.getMethod().getSignature();
				// find the main node/run node
				if (sig.contains(entrySignature)) {
					mainEntryNodes.add(n);
				    sigID = n.getGraphNodeId();
					ThreadInfo nthread = new ThreadInfo(-1, 0);//main, no parents
					threads.put(sigID, nthread);
				} else {
					TypeName name = n.getMethod().getDeclaringClass().getName();
					threadSigNodeMap.put(name, n);
					////
//					mainEntryNodes.add(n);
//				    sigID = n.getGraphNodeId();
//					ThreadInfo nthread = new ThreadInfo(-1, 0);//main, no parents
//					threads.put(sigID, nthread);
				}
			}

			for(CGNode main: mainEntryNodes)
			{
				//threadSigNodeMap.clear();
				//threadNodes.clear();
				alreadyProcessedNodes.clear();
				twiceProcessedNodes.clear();
				alreadyProcessedAst.clear();
				threadNodes.clear();
				traversedTimes.clear();

				threadNodes.add(main);
			    startAnalysis();
			}

		}

	private void startAnalysis() {

		while (!threadNodes.isEmpty()) {
			CGNode n = threadNodes.removeFirst();
			curTID = n.getGraphNodeId();
			tidpool.add(curTID);
			idnode.putIfAbsent(curTID, n);
			// only twice at most for a node
			if (alreadyProcessedNodes.contains(n))
				if (twiceProcessedNodes.contains(n))
					continue;
				else
					twiceProcessedNodes.add(n);

			hasSyncBetween = false;
//			if(n.toString().contains("main"))
//				System.out.println("processing : "+ n.toString());
//			if(n.toString().contains("Tsp, main"))
//				System.out.println();
//			System.out.println("processing : "+ n.toString());

			traverseNode(n);
			traversedTimes.clear();
		}
	}

	private void traverseNode(CGNode n) {
		if(n instanceof AstCGNode2){
			CGNode realnode = newRunTargets.get(n);
			if(alreadyProcessedAst.contains(realnode)){
				return;
			}
			alreadyProcessedAst.add(realnode);
		}

		if(alreadyProcessedNodes.contains(n)){
			if(!hasSyncBetween)
				return;
			else
				hasSyncBetween = false;
		}
		alreadyProcessedNodes.add(n);

		if(n.getIR() == null)
			return;

		SSACFG ssacfg = n.getIR().getControlFlowGraph();
		ISSABasicBlock curbb = ssacfg.getBasicBlock(1);
		HashSet<Locknode> currentHoldLock = new HashSet<Locknode>();
		processBasicBlock(curbb, n, currentHoldLock);
	}

	private void traverseMethod(CGNode n, HashSet<Locknode> currentHoldLock, int sln) {
//		if(twiceProcessedMethods.contains(n))
//			return;
//		if(alreadyProcessedMethods.contains(n)){
//			twiceProcessedMethods.add(n);
//		}else
//			alreadyProcessedMethods.add(n);


		if(traversedTimes.keySet().contains(n)){
			int times = traversedTimes.get(n);
			if(times >= 8)
				return;
			else{
				int next = times+1;
				traversedTimes.put(n, next);
			}
		}else
			traversedTimes.put(n, 1);

//		System.out.println("traverse method :" + n.toString());
		if(n.getIR() == null){
//			System.err.println("method node == null: " + n.toString());
			return;
		}

		if(n.toString().contains("run()V")){
			System.out.println("run method");
//			if(tidpool.contains(n.getGraphNodeId())){
//				AstCGNode2 threadNode = new AstCGNode2(imethod, node.getContext());
//				int threadID = getAnotherTID();
//				threadNode.setGraphNodeId(threadID);
//				threadNode.setCGNode(node);
//				threadNode.setIR(node.getIR());
//				newRunTargets.put(threadNode, node);
//				node = threadNode;
//			}
			threadNodes.add(n);
			int nID = n.getGraphNodeId();
			tidpool.add(nID);
			idnode.putIfAbsent(nID, n);
			//happen before graph
			//add kid
			threads.get(curTID).addKids(nID);
			//add parent
			ThreadInfo kthread = new ThreadInfo(curTID, sln);
			threads.put(nID, kthread);
			return;
		}

		SSACFG ssacfg = n.getIR().getControlFlowGraph();
//		for(int j=1; j< ssacfg.getNumberOfNodes();j++){
//			ISSABasicBlock curbb = ssacfg.getBasicBlock(j);
//			if(curbb.isCatchBlock()||curbb.isExitBlock())
//				continue;
//			else
//				processBasicBlock(curbb, n);
//		}
		ISSABasicBlock curbb = ssacfg.getBasicBlock(1);
		processBasicBlock(curbb, n, currentHoldLock);
//		goThroughOnePath(ssacfg, curbb,n);
//		while (!nonTravereledBraches.isEmpty()) {
//			ISSABasicBlock branch = nonTravereledBraches.removeLast();
//			goThroughOnePath(ssacfg, branch, n);
//		}
	}

//	private void goThroughOnePath(SSACFG ssacfg, ISSABasicBlock curbb, CGNode n) {
//		Collection<ISSABasicBlock> successors = ssacfg.getNormalSuccessors(curbb);
//		if(successors.isEmpty())
//		  return;
//		Iterator<ISSABasicBlock> it = successors.iterator();
//		LinkedList<ISSABasicBlock> effective = new LinkedList<>();
//		//eliminate catch and exit block
//		while(it.hasNext()){
//			ISSABasicBlock next = it.next();
//			if(!next.isCatchBlock() ||!next.isExitBlock()){
//				effective.add(next);
//			}
//		}
//		//seperate: branch, non-branch
//		if(effective.size() == 1){
//			ISSABasicBlock now = effective.pop();
//			processBasicBlock(now, n);
////			if(now.getNumber() == 7)
////				System.out.println();
//			goThroughOnePath(ssacfg, now, n);
//		}else{
////			nonTravereledBraches.add(effective.pop());
//			ISSABasicBlock now = effective.pop();
//			processBasicBlock(now, n);
//			goThroughOnePath(ssacfg, now, n);
//		}
//	}

	@SuppressWarnings("unchecked")
	public void processBasicBlock(ISSABasicBlock curbb, CGNode n, HashSet<Locknode> currentHoldLock){
		Iterator<SSAInstruction> insts = curbb.iterator();
		while(insts.hasNext()){
			SSAInstruction inst = insts.next();
			int sln = 0;
			if(inst != null){
				//get source line num
				try {
					if(n.getIR().getMethod() instanceof IBytecodeMethod){
						int bytecodeindex = ((IBytecodeMethod)n.getIR().getMethod()).getBytecodeIndex(inst.iindex);
						sln = (int)n.getIR().getMethod().getLineNumber(bytecodeindex);
					}else{
						sln = n.getMethod().getSourcePosition(inst.iindex).getFirstLine();//.getLastLine();
					}
				} catch (Exception e) {
				}

				if(inst instanceof SSAAbstractInvokeInstruction){
//					if(rememberinvoke){
//						initialInstructions.put(newinvokeindex, inst);
//						rememberinvoke = false;
//					}

					CallSiteReference csr = ((SSAAbstractInvokeInstruction) inst).getCallSite();
					MethodReference mr = csr.getDeclaredTarget();
					IMethod imethod = callGraph.getClassHierarchy().resolveMethod(mr);
					if(imethod != null){
						String sig = imethod.getSignature();
						//new a thread
						if(sig.equals("java.lang.Thread.start()V")){
							PointerKey key = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n,
									((SSAAbstractInvokeInstruction) inst).getReceiver());
							OrdinalSet<InstanceKey> instances = pointerAnalysis.getPointsToSet(key);
							for(InstanceKey ins : instances){
								//thread
								TypeName name = ins.getConcreteType().getName();;
								CGNode node = threadSigNodeMap.get(name);
								if(node != null)
									threadNodes.add(node);
								else{
									SSAInstruction initial = findInitialInst(n, ins);
									if(initial!=null){
										int param = initial.getUse(1);
										PointerKey keyR = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, param);
										OrdinalSet<InstanceKey> instancesR = pointerAnalysis.getPointsToSet(keyR);
										for(InstanceKey insR: instancesR){
											TypeName localname = insR.getConcreteType().getName();
											CGNode localnode = threadSigNodeMap.get(localname);
											if(localnode != null){
												threadNodes.add(localnode);
												node = localnode;
											}
										}
									}else
										System.out.println("cannot find initial");
								}

								if(node == null){
									System.err.println("node should not be null");
									continue;
								}
								//duplicate graph node id
								if(tidpool.contains(node.getGraphNodeId())){
									AstCGNode2 threadNode = new AstCGNode2(imethod, node.getContext());
									int threadID = getAnotherTID();
									threadNode.setGraphNodeId(threadID);
									threadNode.setCGNode(node);
									threadNode.setIR(node.getIR());
									newRunTargets.put(threadNode, node);
									node = threadNode;
								}
								threadNodes.add(node);
								int nID =node.getGraphNodeId();
								tidpool.add(nID);
								idnode.putIfAbsent(nID, node);
								//happen before graph
								//add kid
								threads.get(curTID).addKids(nID);
								//add parent
								ThreadInfo kthread = new ThreadInfo(curTID, sln);
								threads.put(nID, kthread);
							}
						}else if(sig.equals("java.lang.Thread.join()V")){

						}else{
							Set<CGNode> targets = new HashSet<>();
							if(n instanceof AstCGNode2){
								targets  = callGraph.getPossibleTargets(newRunTargets.get(n), csr);
							}else{
								targets = callGraph.getPossibleTargets(n, csr);
							}
							if(((SSAAbstractInvokeInstruction) inst).getNumberOfParameters()>0){
							for(CGNode target: targets){
								if(AnalysisUtils.isApplicationClass(target.getMethod().getDeclaringClass())){
									if(target.getMethod().isSynchronized()){
										PointerKey key = new PointerKey() {};
										if(n instanceof AstCGNode2)
											key = pointerAnalysis.getHeapModel().getPointerKeyForLocal(newRunTargets.get(n),
													((SSAAbstractInvokeInstruction) inst).getReceiver());
										else
											key = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n,
													((SSAAbstractInvokeInstruction) inst).getReceiver());
										OrdinalSet<InstanceKey> lockObjs = pointerAnalysis.getPointsToSet(key);
										HashSet<Locknode> thisMethod = new HashSet<>();
										for(InstanceKey obj : lockObjs){
											String typeclassname;
											if(n instanceof AstCGNode2)
												typeclassname =  newRunTargets.get(n).getMethod().getDeclaringClass().getName().toString();
											else
												typeclassname =  n.getMethod().getDeclaringClass().getName().toString();
											String instSig =typeclassname.substring(1)+":"+ target.getMethod().getName().toString();
											String locksig = instSig;//obj.getConcreteType().getName().toString();
											Locknode newLockNode = new Locknode(curTID, obj, locksig);
											newLockNode.setStartPosition(sln);
											thisMethod.add(newLockNode);
											//simulate monitor enter
											HashMap<Locknode, HashSet<Locknode>> depend = threads.get(curTID).getDepend();
											if(!currentHoldLock.isEmpty()){
												for(Locknode cur : currentHoldLock){
													HashSet<Locknode> will = new HashSet<>();
													will.add(newLockNode);
													depend.putIfAbsent(cur, will);
												}
											}
										}
										//add to current hold
										currentHoldLock.addAll(thisMethod);
										hasSyncBetween = true;
										traverseMethod(target, currentHoldLock, sln);
										alreadyProcessedMethods.clear();

										//simulate monitor exit
										currentHoldLock.removeAll(thisMethod);
									}else{
										traverseMethod(target, currentHoldLock, sln);
									}
								}
							}
							}
						}
					}
				}else if(inst instanceof SSAMonitorInstruction){
					SSAMonitorInstruction monitorInstruction = ((SSAMonitorInstruction) inst);
					int lockValueNumber = monitorInstruction.getRef();//same lockvaluenumber
					if (n instanceof AstCGNode2)
						n = ((AstCGNode2) n).getCGNode();
					PointerKey lockPointer = pointerAnalysis.getHeapModel().getPointerKeyForLocal(n,lockValueNumber);
					OrdinalSet<InstanceKey> lockObjs = pointerAnalysis.getPointsToSet(lockPointer);
					HashSet<Locknode> created = new HashSet<>();

					if(((SSAMonitorInstruction)inst).isMonitorEnter()){
						for(InstanceKey obj : lockObjs){
							String typeclassname =  n.getMethod().getDeclaringClass().getName().toString();
							String instSig =typeclassname.substring(1)+":"+ n.getMethod().getName().toString();
							String locksig = instSig;//obj.getConcreteType().getName().toString();
							IMethod method = n.getMethod();//
							Locknode newLockNode = new Locknode(curTID, obj, locksig);
							newLockNode.setStartPosition(sln);
							created.add(newLockNode);

						}
						// monitor enter
						HashMap<Locknode, HashSet<Locknode>> depend = threads.get(curTID).getDepend();
						if(!currentHoldLock.isEmpty()){
							for(Locknode cur : currentHoldLock){
								depend.putIfAbsent(cur, created);
							}
						}
						lockNodes.put(lockValueNumber, created);
						currentHoldLock.addAll(created);
					}else{
						//monitor exit
						HashSet<Locknode> dels = lockNodes.get(lockValueNumber);
						currentHoldLock.removeAll(dels);
					}
					hasSyncBetween = true;

				}
//				else if(inst instanceof SSANewInstruction){
//					newinvokeindex = ((SSANewInstruction)inst).getNewSite().getProgramCounter();
//					rememberinvoke = true;
//					newInstructions.put(inst.iindex, inst);
//				}
				else if(inst instanceof SSAGotoInstruction){
					int endofloopindex = ((SSAGotoInstruction) inst).getTarget();
					int startofloopindex = inst.iindex;
					boolean needReTraverse = dealwithloops(startofloopindex, endofloopindex, n);
					if(needReTraverse){
						ISSABasicBlock endBB = n.getIR().getControlFlowGraph().getBlockForInstruction(endofloopindex);
						Collection<ISSABasicBlock> startBBs = n.getIR().getControlFlowGraph().getNormalSuccessors(endBB);
						for(ISSABasicBlock startBB : startBBs){
							goThroughLoop(startBB, endBB, n, currentHoldLock);
							break;
						}
//						while(nonTraLoopBraches.isEmpty()){
//							ISSABasicBlock branch = nonTraLoopBraches.removeLast();
//							goThroughLoop(branch, endBB, n);
//						}
					}
				}
//				else if(inst instanceof SSAArrayLoadInstruction || inst instanceof SSAArrayStoreInstruction || inst instanceof SSALoadIndirectInstruction
//						|| inst instanceof SSAArrayReferenceInstruction || inst instanceof SSAAddressOfInstruction || inst instanceof SSALoadMetadataInstruction){
//					intermedia.add(inst);
//				}
			}
		}

		if(n.getIR() == null)
			return;

		SSACFG cfg = n.getIR().getControlFlowGraph();
		Collection<ISSABasicBlock> bbkids = cfg.getNormalSuccessors(curbb);
		Iterator<ISSABasicBlock> iter = bbkids.iterator();
		while(iter.hasNext()){
			ISSABasicBlock next = iter.next();
			if(next.getNumber() > curbb.getNumber() && !next.isCatchBlock()){
				HashSet<Locknode> currentHoldLock2 = new HashSet<Locknode>();
				currentHoldLock2.addAll(currentHoldLock);
				processBasicBlock(next, n, currentHoldLock2);
			}
		}

	}

	private SSAInstruction findInitialInst(CGNode n, InstanceKey ins) {
		SSAInstruction answer = null;
		NewSiteReference nsr = findNewSiteRef(ins);
		if(nsr != null && n.getIR().existNew(nsr)){
			SSANewInstruction media = n.getIR().getNew(nsr);
			int defIdx = media.getDef();
			Iterator<SSAInstruction> useIter = n.getDU().getUses(defIdx);
			while(useIter.hasNext()){
				SSAInstruction initNode = useIter.next();
//				System.out.println(initNode.toString());
				if(initNode.toString().contains("<init>")){
					answer = initNode;
				}
			}
		}
//System.out.println();
		if(answer == null){
			System.err.println("not found runnable");
		}

	    return answer;
	}

	private void goThroughLoop(ISSABasicBlock startBB, ISSABasicBlock endBB, CGNode n, HashSet<Locknode> currentHoldLock) {
		processBasicBlock(startBB, n, currentHoldLock);
		//rest of work has been scheduled in processbaiscblock
//		SSACFG ssacfg = n.getIR().getControlFlowGraph();
//		Collection<ISSABasicBlock> successors = ssacfg.getNormalSuccessors(startBB);
//		Iterator<ISSABasicBlock> it = successors.iterator();
//		LinkedList<ISSABasicBlock> effective = new LinkedList<>();
//		//eliminate catch and exit block
//		while(it.hasNext()){
//			ISSABasicBlock next = it.next();
//			if(!next.isCatchBlock() && next.getNumber() > startBB.getNumber()){
////				effective.add(next);
//				HashSet<Locknode> currentHoldLock2 = new HashSet<Locknode>();
//				currentHoldLock2.addAll(currentHoldLock);
//				goThroughLoop(next, endBB, n, currentHoldLock2);
//			}
//		}
		//seperate: branch, non-branch
//		if(effective.size() == 1){
//			ISSABasicBlock now = effective.pop();
//			if (now.equals(endBB)) {
//				return;
//			}
//			processBasicBlock(now, n);
//			goThroughLoop(now, endBB, n);
//		}else{
//			nonTraLoopBraches.add(effective.pop());
//			ISSABasicBlock now = effective.pop();
//			if (now.equals(endBB)) {
//				return;
//			}
//			goThroughLoop(now, endBB, n);
//		}
	}

	private boolean dealwithloops(int startofloopindex, int endofloopindex, CGNode n) {
		SSAInstruction[] allInsts = n.getIR().getInstructions();
		LinkedList<SSAInstruction> instInLoop = new LinkedList<>();
		boolean havenewthread = false;
		boolean havesync = false;
		for(int i=startofloopindex; i<=endofloopindex; i++){
			if(allInsts[i]!=null){
				SSAInstruction here = allInsts[i];
				instInLoop.add(here);
				if(here instanceof SSAInvokeInstruction){
					CallSiteReference csr = ((SSAAbstractInvokeInstruction) here).getCallSite();
					MethodReference mr = csr.getDeclaredTarget();
					IMethod imethod = callGraph.getClassHierarchy().resolveMethod(mr);
					if(imethod != null){
						String sig = imethod.getSignature();
						if (sig.equals("java.lang.Thread.start()V")||sig.equals("java.lang.Thread.join()V")){
							havenewthread = true;
						}else{
							Set<CGNode> targets = new HashSet<>();
							if(n instanceof AstCGNode2){
								targets  = callGraph.getPossibleTargets(newRunTargets.get(n), csr);
							}else{
								targets = callGraph.getPossibleTargets(n, csr);
							}
							for(CGNode target: targets){
								if(AnalysisUtils.isApplicationClass(target.getMethod().getDeclaringClass())
										&& target.getMethod().isSynchronized()){
									havesync = true;
								}
							}
						}
					}
				}
//				else if(here instanceof SSAMonitorInstruction){
//					havesync = true;
//				}
			}
		}
		if(havenewthread){
//			AstCGNode2 loopnode = new AstCGNode2(n.getMethod(), n.getContext());
//			int newTID = getAnotherTID();
//			loopnode.setGraphNodeId(newTID);
//			loopnode.setCGNode(n);
//			loopnode.setIR(n.getIR());

			return true;
		}else
			return false;

	}


	private int getAnotherTID() {
		int tid = -1;
		for(int k=100; k<400; k++){
			if(!tidpool.contains(k)){
				tid = k;
				break;
			}
		}
		return tid;
	}

	private NewSiteReference findNewSiteRef(InstanceKey ins) {
		Iterator<Pair<CGNode, NewSiteReference>> createsite = ins.getCreationSites(callGraph);
		NewSiteReference ref = null;
		int counter = 0;
		while (createsite.hasNext()) {
			Pair<com.ibm.wala.ipa.callgraph.CGNode, com.ibm.wala.classLoader.NewSiteReference> pair = (Pair<com.ibm.wala.ipa.callgraph.CGNode, com.ibm.wala.classLoader.NewSiteReference>) createsite
					.next();
			ref = pair.snd;
			counter++;
		}
		if(counter >1)
			System.err.println("CREATION SITE IS MORE THAN 1");
		return ref;
	}

	private int findProgramCounter(InstanceKey ins) {
		Iterator<Pair<CGNode, NewSiteReference>> createsite = ins.getCreationSites(callGraph);
		int pcounter = -1;
		int counter = 0;
		while (createsite.hasNext()) {
			Pair<com.ibm.wala.ipa.callgraph.CGNode, com.ibm.wala.classLoader.NewSiteReference> pair = (Pair<com.ibm.wala.ipa.callgraph.CGNode, com.ibm.wala.classLoader.NewSiteReference>) createsite
					.next();
			pcounter = pair.snd.getProgramCounter();
			counter++;
		}
		if(counter >1)
			System.err.println("CREATION SITE IS MORE THAN 1");
		return pcounter;
	}

	public Set<DLNode> deteckLock() {
		// 2 threads
		HashSet<DLNode> dlpair = new HashSet<DLNode>();

		//check threadinfo -- debug
//		System.out.println();
//		Iterator<Integer> iterator0 = threads.keySet().iterator();
//		while (iterator0.hasNext()) {
//			int id = iterator0.next();
//			ThreadInfo threadInfo = threads.get(id);
//			System.out.println("THREAD ID: " + id + " " + idnode.get(id));
//			if(idnode.get(id) instanceof AstCGNode2){
//				System.out.println("\t REAL NODE: "+ newRunTargets.get(idnode.get(id)));
//			}
//			System.out.println("--- PARENT:" + threadInfo.getParent());
//			System.out.println("--- KIDS:"+ threadInfo.getKids().toString());
//			System.out.println("--- Dependency:" + threadInfo.getDepend().toString());
////			if(!threadInfo.getDepend().isEmpty()){
////				HashMap<Locknode, HashSet<Locknode>> depends = threadInfo.getDepend();
////				Iterator<Locknode> it = depends.keySet().iterator();
////				while(it.hasNext()){
////					Locknode obj1 = it.next();
////					System.out.println("------ " + obj1.getLockobj().toString() );
////					for (Locknode obj2 : depends.get(obj1)) {
////						System.out.println("-------- "+ obj2.getLockobj().toString());
////					}
////				}
////			}
//		}


		Iterator<Integer> iterator = threads.keySet().iterator();
		//compare among kids
		while(iterator.hasNext()){
			int parent = iterator.next();
			ThreadInfo paInfo = threads.get(parent);
			ArrayList<Integer> kids = paInfo.getKids();
			if(kids.size() > 1){
				for(int i=0; i< kids.size()-1; i++){
					ThreadInfo comer = threads.get(kids.get(i));
					HashMap<Locknode, HashSet<Locknode>> comerDepends = comer.getDepend();
					for(int j=i+1; j<kids.size(); j++){
					  ThreadInfo comee = threads.get(kids.get(j));
					  HashMap<Locknode, HashSet<Locknode>> comeeDepends = comee.getDepend();
					  if(comerDepends.isEmpty() || comeeDepends.isEmpty())
						  continue;
					  findAllDL(comeeDepends, comerDepends, dlpair);
					}
				}
			}
		}

		//compare all kids with parent
		Iterator<Integer> iterator2 = threads.keySet().iterator();
		while(iterator2.hasNext()){
			int parent = iterator2.next();
			ThreadInfo paInfo = threads.get(parent);
			HashMap<Locknode, HashSet<Locknode>> pDepends = paInfo.getDepend();
			if(pDepends.isEmpty())
				continue;
			ArrayList<Integer> directKids = paInfo.getKids();
			Iterator<Integer> iterator3 = directKids.iterator();
			while(iterator3.hasNext()){
				Integer direcTid = iterator3.next();
				ThreadInfo directKid = threads.get(direcTid);
				int startDK = directKid.getStartP();
				LinkedList<Integer> otherKids = findAllKidThreads(direcTid);
				otherKids.add(direcTid);
				for(Locknode p1st : pDepends.keySet()){
					int p1stStart = p1st.getStartPosition();
					if(p1stStart > startDK){
						findPossibleDL(p1st, pDepends, otherKids, dlpair);
					}
				}
			}
		}

		//compare all parents
		Iterator<Integer> iterator3 = threads.keySet().iterator();
		while(iterator3.hasNext()){
			int pc = iterator3.next();
			ThreadInfo pcInfo = threads.get(pc);
			HashMap<Locknode, HashSet<Locknode>> pcDepends = pcInfo.getDepend();
			if(pcDepends.isEmpty())
				continue;
			for (int pp : threads.keySet()) {
				if(pp != pc){
					ThreadInfo ppInfo = threads.get(pp);
					HashMap<Locknode, HashSet<Locknode>> ppDepends = ppInfo.getDepend();
					if(ppDepends.isEmpty())
						continue;
					findAllDL(pcDepends, ppDepends, dlpair);
				}
			}
		}
//        System.out.println("dl number: "+dlpair.size());
		return dlpair;
	}

	private LinkedList<Integer> findAllKidThreads(int parent) {
		LinkedList<Integer> kids = new LinkedList<>();
		ArrayList<Integer> directKids = threads.get(parent).getKids();
		kids.addAll(directKids);
		while(!directKids.isEmpty()){
			LinkedList<Integer> thisRound = new LinkedList<>(directKids);
			directKids.clear();
			while(!thisRound.isEmpty()){
				Integer tid = thisRound.pop();
				directKids.addAll(threads.get(tid).getKids());
			}
		}
		return kids;
	}

	private void findAllDL(HashMap<Locknode, HashSet<Locknode>> comeeDepends,
			HashMap<Locknode, HashSet<Locknode>> comerDepends, HashSet<DLNode> dlpair) {
		for(Locknode ee1 : comeeDepends.keySet()){
			InstanceKey ee1obj = ee1.getLockobj();
			HashSet<Locknode> ee2s = comeeDepends.get(ee1);
			for(Locknode ee2 : ee2s){
				InstanceKey ee2obj = ee2.getLockobj();

				for(Locknode er1 : comerDepends.keySet()){
					InstanceKey er1obj = er1.getLockobj();
					HashSet<Locknode> er2s = comerDepends.get(er1);
					for(Locknode er2 : er2s){
						InstanceKey er2obj = er2.getLockobj();

					    if(ee1obj.equals(er2obj) && ee2obj.equals(er1obj)){
//					    	if(ee1obj.equals(ee2obj) || er1obj.equals(er2obj))
//					    		continue;
					    	if(ee1.startPosition == er1.startPosition && ee2.startPosition == er2.startPosition)
					    		continue;
					    	DLNode newDl = new DLNode(ee1.getLockSig(), ee1, ee2, er1, er2);
					    	if(!Duplicate(newDl, dlpair))
					    		dlpair.add(newDl);
					    }
					}
				}
			}
		}
	}

	private void findPossibleDL(Locknode p1, HashMap<Locknode, HashSet<Locknode>> pDepends,
			LinkedList<Integer> otherKids, HashSet<DLNode> dlpair) {
		InstanceKey p1obj = p1.getLockobj();
		HashSet<Locknode> p2s = pDepends.get(p1);
		for(Locknode p2 : p2s){
			InstanceKey p2obj = p2.getLockobj();

			while(!otherKids.isEmpty()){
				Integer kid = otherKids.pop();
				ThreadInfo kidT = threads.get(kid);
				HashMap<Locknode, HashSet<Locknode>> kDependes = kidT.getDepend();
				if(kDependes.isEmpty())
					continue;
				for(Locknode k1 : kDependes.keySet()){
					InstanceKey k1obj = k1.getLockobj();
					HashSet<Locknode> k2s = kDependes.get(k1);
					for(Locknode k2 : k2s){
						InstanceKey k2obj = k2.getLockobj();
						if(p1obj.equals(k2obj) && p2obj.equals(k1obj)){
//							if(p1obj.equals(p2obj) || k1obj.equals(k2obj))
//								continue;
							if(p1.startPosition == k1.startPosition && p2.startPosition == k2.startPosition)
					    		continue;
							DLNode newDl = new DLNode(p1.getLockSig(), p1, p2, k1, k2);
					    	if(!Duplicate(newDl, dlpair))
					    		dlpair.add(newDl);
						}
					}
				}
			}
		}
	}

	private boolean Duplicate(DLNode newDl, HashSet<DLNode> dlpair) {
		boolean isDup = false;
		int new1 = newDl.node1.getStartPosition();
    	int new2 = newDl.node2.getStartPosition();
    	int new3 = newDl.node3.getStartPosition();
    	int new4 = newDl.node4.getStartPosition();
        for (DLNode dlNode : dlpair) {
        	int dl1 = dlNode.node1.getStartPosition();
        	int dl2 = dlNode.node2.getStartPosition();
        	int dl3 = dlNode.node3.getStartPosition();
        	int dl4 = dlNode.node4.getStartPosition();
        	if(dl1 == new1 && dl2 == new2 && dl3 == new3 && dl4 == new4)
        		isDup = true;
        	else if(dl1 == new3 && dl2 == new4 && dl3 == new1 && dl4 == new2)
        		isDup = true;
		}
		return isDup;
	}

}
