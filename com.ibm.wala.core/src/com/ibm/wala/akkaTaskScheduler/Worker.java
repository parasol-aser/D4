package com.ibm.wala.akkaTaskScheduler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.fixedpoint.impl.AbstractFixedPointSolver;
import com.ibm.wala.fixpoint.AbstractOperator;
import com.ibm.wala.fixpoint.AbstractStatement;
import com.ibm.wala.fixpoint.IVariable;
import com.ibm.wala.fixpoint.UnaryStatement;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.propagation.AssignOperator;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ipa.callgraph.propagation.PointsToSetVariable;
import com.ibm.wala.ipa.callgraph.propagation.PropagationCallGraphBuilder;
import com.ibm.wala.ipa.callgraph.propagation.PropagationCallGraphBuilder.FilterOperator;
import com.ibm.wala.ipa.callgraph.propagation.PropagationGraph;
import com.ibm.wala.ipa.callgraph.propagation.PropagationSystem;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.intset.IntIterator;
import com.ibm.wala.util.intset.IntSetAction;
import com.ibm.wala.util.intset.IntSetUtil;
import com.ibm.wala.util.intset.MutableIntSet;
import com.ibm.wala.util.intset.MutableSharedBitVectorIntSet;
import com.ibm.wala.util.intset.MutableSharedBitVectorIntSetFactory;
import com.ibm.wala.util.intset.MutableSparseIntSet;
import com.ibm.wala.util.intset.MutableSparseIntSetFactory;
import com.sun.javafx.scene.paint.GradientUtils.Point;
import com.sun.org.apache.bcel.internal.generic.NEW;

import akka.actor.UntypedActor;

public class Worker extends UntypedActor{

	@Override
	public void onReceive(Object message) throws Exception {
		if(message instanceof WorkContentForCheckChange){
		  WorkContentForCheckChange work = (WorkContentForCheckChange) message;
		  ResultFromRR next = processCheckChangeUpdate(work);
		  getSender().tell(next, getSelf());
		}else if(message instanceof WorkContentForSpecial){
		  WorkContentForSpecial work = (WorkContentForSpecial) message;
		  final boolean isAddition = work.getIsAdd();
		  ResultFromS result;
		  if(isAddition){
		    result = processSpecialWorkAddition(work);
		  }else{
		    result = processSpecialWorkDeletion(work);
		  }
 		  getSender().tell(result, getSelf());
 		}else {
			unhandled(message);
		}
	}




//  private HashSet<PointsToSetVariable> store = new HashSet<>();// for isreachable
//  private HashSet<PointsToSetVariable> processed = new HashSet<>();// for pf


  private ResultFromS processSpecialWorkAddition(WorkContentForSpecial work) {
    final PointsToSetVariable user = work.getUser();
    final MutableIntSet targets = work.getTargets();
    final PropagationSystem system = work.getPropagationSystem();

    ArrayList<PointsToSetVariable> next = new ArrayList<>();
    if(user.getValue() == null)
      return new ResultFromS(user, next, (MutableSharedBitVectorIntSet) targets, work.getIsAdd());

    final MutableSharedBitVectorIntSet remaining = new MutableSharedBitVectorIntSetFactory().make();
    IntSetAction action = new IntSetAction() {
      @Override
      public void act(int i) {
        if(!user.contains(i)){
          remaining.add(i);
        }
      }
    };
    targets.foreach(action);

    if(!remaining.isEmpty()){
      synchronized (user) {
        user.addAll(remaining);
      }
      AbstractFixedPointSolver.addToChanges(user);
//    //copy ??
//      MutableIntSet copy;
//      synchronized (user) {//?
//        copy = IntSetUtil.makeMutableCopy(user.getValue());
//      }
//      further check
      for (Iterator it = system.getPropagationGraph().getStatementsThatUse(user); it.hasNext();) {
        AbstractStatement s = (AbstractStatement) it.next();
        AbstractOperator op = s.getOperator();
        if(op instanceof AssignOperator){
          PointsToSetVariable pv = (PointsToSetVariable) s.getLHS();
          if(pv.getValue() != null)
            next.add(pv);
        }else if(op instanceof FilterOperator){
          FilterOperator filter = (FilterOperator) op;
          PointsToSetVariable pv = (PointsToSetVariable) s.getLHS();
          //sync? pv?
          byte mark = filter.evaluate(pv, (PointsToSetVariable)((UnaryStatement)s).getRightHandSide());
          if(mark == 1){
            AbstractFixedPointSolver.addToChanges(pv);
            next.add(pv);
          }
        }else{// if(s instanceof UnaryStatement && iv == null)
          system.addToWorklistAkka(s);
        }
      }
    }else{
      next = null;
    }
    return new ResultFromS(user, next, remaining, work.getIsAdd());
  }

  private ResultFromS processSpecialWorkDeletion(WorkContentForSpecial work) {
    final PointsToSetVariable user = work.getUser();
    final MutableIntSet targets = work.getTargets();
    final PropagationSystem system = work.getPropagationSystem();

    ArrayList<PointsToSetVariable> next = new ArrayList<>();
    if(AbstractFixedPointSolver.theRoot.contains(user)
        || system.getPropagationGraph().getNumberOfStatementsThatDef(user) == 0 //root
        || user.getValue() == null)
      return new ResultFromS(user, next, (MutableSharedBitVectorIntSet) targets, work.getIsAdd());

    final MutableSharedBitVectorIntSet remaining = new MutableSharedBitVectorIntSetFactory().makeCopy(targets);
    for (Iterator it = system.getPropagationGraph().getStatementsThatDef(user); it.hasNext();) {
      if(remaining.isEmpty())
        break;
      UnaryStatement s = (UnaryStatement) it.next();
      IVariable iv = s.getRightHandSide();
      if(iv instanceof PointsToSetVariable){
        PointsToSetVariable pv = (PointsToSetVariable)iv;
        if(pv.getValue() != null){
          IntSetAction action = new IntSetAction() {
            @Override
            public void act(int i) {
              if(remaining.isEmpty())
                return;
              if(targets.contains(i)){
                remaining.remove(i);
              }
            }
          };
//          pv.getValue().foreach(action);
          MutableIntSet set = pv.getValue();
          if(set != null){
            MutableIntSet set1;
            synchronized (pv) {//?
              set1 = IntSetUtil.makeMutableCopy(set);
            }
            set1.foreach(action);
          }else
            continue;
        }
      }
    }

    if(!remaining.isEmpty()){
      MutableSharedBitVectorIntSet removed;
      synchronized (user) {
        removed = user.removeSome(remaining);//?sync
      }
      if(removed.size() > 0){
        AbstractFixedPointSolver.addToChanges(user);
        //copy
        MutableIntSet copy;
        synchronized (user) {//?
          copy = IntSetUtil.makeMutableCopy(user.getValue());
        }
        //future
        for (Iterator it = system.getPropagationGraph().getStatementsThatUse(user); it.hasNext();) {
          AbstractStatement s = (AbstractStatement) it.next();
          AbstractOperator op = s.getOperator();
          if(op instanceof AssignOperator){
            PointsToSetVariable pv = (PointsToSetVariable) s.getLHS();
            if(pv.getValue() != null)
              next.add(pv);
          }else if(op instanceof FilterOperator){
            FilterOperator filter = (FilterOperator) op;
            PointsToSetVariable pv = (PointsToSetVariable) s.getLHS();
            if(AbstractFixedPointSolver.theRoot.contains(pv))
              continue;
            //sync? pv?
            synchronized (pv) {
              byte mark = filter.evaluateDel(pv, (MutableSharedBitVectorIntSet)copy);
              if(mark == 1){
                AbstractFixedPointSolver.addToChanges(pv);
                inner2CorePointsToDel2Better(pv, copy, next, system);
              }
            }
          }else{// if(s instanceof UnaryStatement && iv == null)
            system.addToWorklistAkka(s);
          }
        }
      }else{
        next = null;
      }
    }else{//all included, early return
      next = null;
    }
    return new ResultFromS(user, next, remaining, work.getIsAdd());
  }


  private ResultFromRR processCheckChangeUpdate(WorkContentForCheckChange work) {
    final PointsToSetVariable user = work.getUser();
    final MutableIntSet targets = work.getTargets();
    final PropagationSystem system = work.getPropagationSystem();

    ArrayList<PointsToSetVariable> next = new ArrayList<>();
    if(AbstractFixedPointSolver.theRoot.contains(user)
        || system.getPropagationGraph().getNumberOfStatementsThatDef(user) == 0 //root
        || user.getValue() == null)
      return new ResultFromRR(user, next, (MutableSharedBitVectorIntSet) targets);
    //check
    final MutableSharedBitVectorIntSet remaining = new MutableSharedBitVectorIntSetFactory().makeCopy(targets);
    for (Iterator it = system.getPropagationGraph().getStatementsThatDef(user); it.hasNext();) {
      if(remaining.isEmpty())
        break;
      UnaryStatement s = (UnaryStatement) it.next();
      IVariable iv = s.getRightHandSide();
      if(iv instanceof PointsToSetVariable){
        PointsToSetVariable pv = (PointsToSetVariable)iv;
        if(pv.getValue() != null){
          IntSetAction action = new IntSetAction() {
            @Override
            public void act(int i) {
              if(remaining.isEmpty())
                return;
              if(targets.contains(i)){
                remaining.remove(i);
              }
            }
          };
//          pv.getValue().foreach(action);
          MutableIntSet set = pv.getValue();
          if(set != null){
            MutableIntSet set1;
            synchronized (pv) {//?
              set1 = IntSetUtil.makeMutableCopy(set);
            }
            set1.foreach(action);
          }else
            continue;
        }
      }
    }
    //check if changed
    if(!remaining.isEmpty()){
      MutableSharedBitVectorIntSet removed ;
      synchronized (user) {
        removed = user.removeSome(remaining);//?sync
      }
      if(removed.size() > 0){
        AbstractFixedPointSolver.addToChanges(user);
        //copy
        MutableIntSet copy;
        synchronized (user) {//?
          copy = IntSetUtil.makeMutableCopy(user.getValue());
        }
        //future
        for (Iterator it = system.getPropagationGraph().getStatementsThatUse(user); it.hasNext();) {
          AbstractStatement s = (AbstractStatement) it.next();
          AbstractOperator op = s.getOperator();
          if(op instanceof AssignOperator){
            PointsToSetVariable pv = (PointsToSetVariable) s.getLHS();
            if(pv.getValue() != null)
              next.add(pv);
          }else if(op instanceof FilterOperator){
            FilterOperator filter = (FilterOperator) op;
            PointsToSetVariable pv = (PointsToSetVariable) s.getLHS();
            if(AbstractFixedPointSolver.theRoot.contains(pv))
              continue;
            //sync? pv?
            synchronized (pv) {
              byte mark = filter.evaluateDel(pv, (MutableSharedBitVectorIntSet)copy);
              if(mark == 1){
                AbstractFixedPointSolver.addToChanges(pv);
                inner2CorePointsToDel2Better(pv, copy, next, system);
              }
            }
          }else{// if(s instanceof UnaryStatement && iv == null)
            system.addToWorklistAkka(s);
          }
        }
      }else{
        next = null;
      }
    }else{//all included, early return
      next = null;
    }

    return new ResultFromRR(user, next, remaining);
  }

  private void inner2CorePointsToDel2Better(PointsToSetVariable L, final MutableIntSet targets,
      ArrayList<PointsToSetVariable> next, PropagationSystem system){
    for (Iterator it = system.getPropagationGraph().getStatementsThatUse(L); it.hasNext();) {
      AbstractStatement s = (AbstractStatement) it.next();
      AbstractOperator op = s.getOperator();
      if(op instanceof AssignOperator){
        PointsToSetVariable pv = (PointsToSetVariable) s.getLHS();
        if(pv.getValue() != null){
//          processCheckChangeUpdate(new WorkContentForCheckChange(pv, targets));
          next.add(pv);
        }
      }else if(op instanceof FilterOperator){
        FilterOperator filter = (FilterOperator) op;
        PointsToSetVariable pv = (PointsToSetVariable) s.getLHS();
        if(AbstractFixedPointSolver.theRoot.contains(pv))
          continue;
        synchronized (pv) {
          byte mark = filter.evaluateDel(pv, (MutableSharedBitVectorIntSet)targets);
          if(mark == 1){
            AbstractFixedPointSolver.addToChanges(pv);
            inner2CorePointsToDel2Better(pv, targets, next, system);
          }
        }
      }else{// if(s instanceof UnaryStatement && iv == null)
        system.addToWorklistAkka(s);
      }
    }
  }





//  private ResetResult processSpecialWorkDeletionFirst(WorkContentForSpecial work) {
//    final PointsToSetVariable user = work.getUser();
////    if(user.toString().contains("lhs is: [Node: < Primordial, Ljava/util/HashMap$TreeNode, removeTreeNode(Ljava/util/HashMap;[Ljava/util/HashMap$Node;Z)V > Context: ReceiverInstanceContext<SITE_IN_NODE{< Primordial, Ljava/util/LinkedHashMap, replacementTreeNode(Ljava/util/HashMap$Node;Ljava/util/HashMap$Node;)Ljava/util/HashMap$TreeNode; >:NEW <Primordial,Ljava/util/HashMap$TreeNode>@5 in ReceiverInstanceContext<SITE_IN_NODE{< Primordial, Ljava/util/ServiceLoader, <init>(Ljava/lang/Class;Ljava/lang/ClassLoader;)V >:NEW <Primordial,Ljava/util/LinkedHashMap>@5 in Everywhere}>}>, v15]"))
////      System.out.println();
//    final MutableIntSet targets = work.getTargets();
//    ArrayList<PointsToSetVariable> results = new ArrayList<>();
//    MutableSparseIntSet localtargets = new MutableSparseIntSetFactory().makeCopy(targets);
//    //reachability user
//    boolean isReachable = false;
//    IntIterator intIterator = targets.intIterator();
//    while(intIterator.hasNext()){
//      int delIndex = intIterator.next();
//      InstanceKey instKey = PropagationCallGraphBuilder.system.getInstanceKey(delIndex);
//      Iterator<Pair<CGNode, NewSiteReference>> pairIt = instKey.getCreationSites(PropagationSystem.cg);
//      while(pairIt.hasNext()){//should be unique??
//        Pair<CGNode, NewSiteReference> pair = pairIt.next();
//        CGNode n = pair.fst;
//        NewSiteReference site = pair.snd;
//        SSAInstruction inst2;
//        if(n.getIR().existNew(site)){
//          inst2 = n.getIR().getNew(site);
//        }else{
//          continue;
//        }
//        Iterator<SSAInstruction> useIt =n.getDU().getUses(inst2.getDef());
//        while(useIt.hasNext()){//may have multiple
//          SSAInstruction useInstruction = useIt.next();
//          int defIndex = useInstruction.getDef();
//          if(defIndex==-1) continue;
//          PointerKey basePointerKey = PropagationCallGraphBuilder.system.pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, defIndex);
//          PointsToSetVariable baseVar = PropagationCallGraphBuilder.system.findOrCreatePointsToSet(basePointerKey);
//          //the statement should have already been removed from the graph
//          if(baseVar!=null){
//            isReachable = isReachableInFlowGraph(baseVar, user);//isReachableWithoutEdgeR2L(baseVar,L,R);
//            if(isReachable) {
//              localtargets.remove(delIndex);
//              store.clear();
//              break;
//            }
//          }
//          store.clear();
//        }
//      }
//    }
////    processed.add(user);
//    //check change
//    if(!localtargets.isEmpty()){
////      further check
//      synchronized (user) {
//        user.removeSome(localtargets);
//      }
//      for (Iterator it = PropagationSystem.flowGraph.getStatementsThatUse(user); it.hasNext();) {
//        AbstractStatement s = (AbstractStatement) it.next();
//        IVariable iv = s.getLHS();
//        if(iv instanceof PointsToSetVariable){
//          PointsToSetVariable pv = (PointsToSetVariable) iv;
////          if(pv.toString().contains("Node: < Primordial, Ljava/util/HashMap$TreeNode, removeTreeNode(Ljava/util/HashMap;[Ljava/util/HashMap$Node;Z)V > Context: ReceiverInstanceContext<SITE_IN_NODE{< Primordial, Ljava/util/LinkedHashMap, replacementTreeNode(Ljava/util/HashMap$Node;Ljava/util/HashMap$Node;)Ljava/util/HashMap$TreeNode; >:NEW <Primordial,Ljava/util/HashMap$TreeNode>@5 in ReceiverInstanceContext<SITE_IN_NODE{< Primordial, Ljava/util/ServiceLoader, <init>(Ljava/lang/Class;Ljava/lang/ClassLoader;)V >:NEW <Primordial,Ljava/util/LinkedHashMap>@5 in Everywhere}>}>, v15"))
////            System.out.println();
////          if(!processed.contains(pv))
////            processSpecialWorkDeletion(new WorkContentForSpecial(pv, localtargets, false));
//            results.add(pv);
//        }else
//          if(s instanceof UnaryStatement && iv == null)
//          {
//          PropagationCallGraphBuilder.system.addToWorklistAkka(s);
////        s.evaluateDel();
//        }
//      }
//    }
//    return new ResetResult(user, results);
//  }

//	private ResetResult processCheckChange(WorkContentForCheckChange work) {
//	  PointsToSetVariable user = work.getUser();
//	  MutableIntSet targets = work.getTargets();
//	  ArrayList<PointsToSetVariable> next = new ArrayList<>();
//    MutableSparseIntSet localtargets = new MutableSparseIntSetFactory().makeCopy(targets);
//	  //reachability user
//    boolean isReachable = false;
//    IntIterator intIterator = targets.intIterator();
//    while(intIterator.hasNext()){
//      int delIndex = intIterator.next();
//      InstanceKey instKey = PropagationCallGraphBuilder.system.getInstanceKey(delIndex);
//      Iterator<Pair<CGNode, NewSiteReference>> pairIt = instKey.getCreationSites(PropagationSystem.cg);
//      while(pairIt.hasNext()){//should be unique??
//        Pair<CGNode, NewSiteReference> pair = pairIt.next();
//        CGNode n = pair.fst;
//        NewSiteReference site = pair.snd;
//        SSAInstruction inst2;
//        if(n.getIR().existNew(site)){
//          inst2 = n.getIR().getNew(site);
//        }else{
//          continue;
//        }
//        Iterator<SSAInstruction> useIt =n.getDU().getUses(inst2.getDef());
//        while(useIt.hasNext()){//may have multiple
//          SSAInstruction useInstruction = useIt.next();
//          int defIndex = useInstruction.getDef();
//          if(defIndex==-1) continue;
//          PointerKey basePointerKey = PropagationCallGraphBuilder.system.pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, defIndex);
//          PointsToSetVariable baseVar = PropagationCallGraphBuilder.system.findOrCreatePointsToSet(basePointerKey);
//          //the statement should have already been removed from the graph
//          if(baseVar!=null){
//            isReachable = isReachableInFlowGraph(baseVar, user);//isReachableWithoutEdgeR2L(baseVar,L,R);
//            if(isReachable) {
//              localtargets.remove(delIndex);
//              store.clear();
//              break;
//            }
//          }
//          store.clear();
//        }
//      }
//    }
//
//	  //check change
//	  if(!localtargets.isEmpty()){
//	    synchronized (user) {
//	      user.removeSome(localtargets);
//	    }
//	    //further check
//	    for (Iterator it = PropagationSystem.flowGraph.getStatementsThatUse(user); it.hasNext();) {
//        AbstractStatement s = (AbstractStatement) it.next();
//        IVariable iv = s.getLHS();
//        if(iv instanceof PointsToSetVariable){
//          PointsToSetVariable pv = (PointsToSetVariable) iv;
//          next.add(pv);
//        }else
//          if(s instanceof UnaryStatement && iv == null)
//          {
//          PropagationCallGraphBuilder.system.addToWorklistAkka(s);
////        s.evaluateDel();
//        }
//      }
//	  }else{
//	    next = null;
//	  }
//	  return new ResetResult(user, next);
//  }


//	private boolean isReachableInFlowGraph(PointsToSetVariable startVar, PointsToSetVariable endVar) {
//    for (Iterator it = PropagationSystem.flowGraph.getStatementsThatUse(startVar); it.hasNext();) {
//      AbstractStatement s = (AbstractStatement) it.next();
//      IVariable iv = s.getLHS();
//      if(iv == null)
//        return false;
//      if(store.contains(startVar)){
//        return false;
//      }else{
//        store.add(startVar);
//      }
//      if(iv==endVar)
//        return true;
//      else if(iv instanceof PointsToSetVariable){
//        String  string;
//        synchronized (iv) {
//           string = iv.toString();
//        }
//        if(!string.contains("< Application"))
//          continue;
//
//        boolean isReachable =isReachableInFlowGraph((PointsToSetVariable)iv,endVar);
//        if(isReachable) return true;
//      }
//    }
//    return false;
//  }


//  private ResetResult processCheckChangeOnebyOne(WorkContentForCheckChange work) {
//    final PointsToSetVariable user = work.getUser();
//    final MutableIntSet targets = work.getTargets();
//    IntIterator isetit = targets.intIterator();
//    while(isetit.hasNext())
//    {
//      long start = System.currentTimeMillis();
//      int index = isetit.next();
//        corePointsToDel2(user, index);
//    }
//
//    return new ResetResult(user, null);
//  }

//  private void corePointsToDel2(PointsToSetVariable L, final int delIndex){
//    if (L.contains(delIndex)) {
//      {
//        boolean isReachable = false;
//        InstanceKey instKey = PropagationCallGraphBuilder.system.getInstanceKey(delIndex);
//        Iterator<Pair<CGNode, NewSiteReference>> pairIt = instKey.getCreationSites(PropagationSystem.cg);
//        //        int sizeOfNSR = 0;
//        while(pairIt.hasNext()){//should be unique??
//          Pair<CGNode, NewSiteReference> pair = pairIt.next();
//          CGNode n = pair.fst;
//          NewSiteReference site = pair.snd;
//          SSAInstruction inst2;
//          if(n.getIR().existNew(site)){
//            inst2 = n.getIR().getNew(site);
//          }else{
//            continue;
//          }
//          Iterator<SSAInstruction> useIt =n.getDU().getUses(inst2.getDef());
//          while(useIt.hasNext())
//          {//may have multiple
//            SSAInstruction useInstruction = useIt.next();
//            //consider different types of instructions
//            //return, field, call, array
//            int defIndex = useInstruction.getDef();
//            if(defIndex==-1) continue;
//            PointerKey basePointerKey = PropagationCallGraphBuilder.system.pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, defIndex);
//            PointsToSetVariable baseVar = PropagationCallGraphBuilder.system.findOrCreatePointsToSet(basePointerKey);
//            if(baseVar!=null){
//              isReachable = isReachableInFlowGraph(baseVar,L);//isReachableWithoutEdgeR2L(baseVar,L,R);
//              if(isReachable) {
//                store.clear();
//                return;
//              }
//            }
//            store.clear();
//          }
//        }
//
//        //if not reachable, deleting, and continue for other nodes
//        {
//          synchronized (L) {
//            L.remove(delIndex);
//          }
//          //          System.out.println("remove del index:   " + delIndex);
////          if(!processed.contains(L))
////            processed.add(L);
//
//          for (Iterator it = PropagationCallGraphBuilder.system.flowGraph.getStatementsThatUse(L); it.hasNext();) {
//            AbstractStatement s = (AbstractStatement) it.next();
//            IVariable iv = s.getLHS();
//            if(iv instanceof PointsToSetVariable ){//&& !processed.contains(iv)
//              PointsToSetVariable pv = (PointsToSetVariable)iv;
//              corePointsToDel2(pv, delIndex);
//            }else
//              if(s instanceof UnaryStatement && iv == null)
//              {
//              PropagationCallGraphBuilder.system.addToWorklistAkka(s);
////            s.evaluateDel();
//            }
//          }
//        }
//      }
//
//    }
//  }


//  private void processTaskForReset(WorkContentForRecompute work) {
//	  ArrayList<AbstractStatement> worklist = new ArrayList<>();
//	  PointsToSetVariable leader = work.getLeader();
//	  ArrayList<PointsToSetVariable> team = work.getMembers();
////	  ConcurrentLinkedQueue<PointsToSetVariable> localMembers = new ConcurrentLinkedQueue<>();
//	  boolean localChanges = false;
//	  for (Iterator it = PropagationSystem.flowGraph.getStatementsThatDef(leader); it.hasNext();) {
//      AbstractStatement s = (AbstractStatement) it.next();
//      byte code = s.evaluate();
//
//      if (code == 1) {
//        if(!AbstractFixedPointSolver.changes.contains(s.getLHS()))
//          AbstractFixedPointSolver.changes.add(s.getLHS());
//        localChanges = true;
//      }
//    }
//	  if(localChanges){
//	    for (Iterator it = PropagationSystem.flowGraph.getStatementsThatUse(leader); it.hasNext();) {
//	      AbstractStatement s = (AbstractStatement) it.next();
//	      s.evaluate();
////        if(s.getLHS() != null)
////          localMembers.add((PointsToSetVariable) s.getLHS());
////	      worklist.add(s);
//	    }
//	    localChanges = false;
//	  }
//
//    for(int i=0; i<team.size(); i++){
//      PointsToSetVariable member = team.get(i);
//      for (Iterator it = PropagationSystem.flowGraph.getStatementsThatDef(member); it.hasNext();) {
//        AbstractStatement s = (AbstractStatement) it.next();
//        byte code = s.evaluate();
//
//        if (code == 1) {
//          if(!AbstractFixedPointSolver.changes.contains(s.getLHS()))
//            AbstractFixedPointSolver.changes.add(s.getLHS());
//          localChanges = true;
//        }
//      }
//      if(localChanges){
//        for (Iterator it = PropagationSystem.flowGraph.getStatementsThatUse(member); it.hasNext();) {
//          AbstractStatement s = (AbstractStatement) it.next();
//          s.evaluate();
////          if(s.getLHS() != null)
////            localMembers.add((PointsToSetVariable) s.getLHS());
////          worklist.add(s);
//        }
//        localChanges = false;
//      }
//    }
//
////    return result;
//	}
//
//  private PointsToSetVariable processTaskForSet(WorkContentForSet work) {
//	  PointsToSetVariable v = work.getPointsToSetVariable();
//	  MutableIntSet targets = work.getTargets();
//	  synchronized (v) {
//      synchronized (targets) {
//        v.removeSome(targets);
//      }
//    }
////	  PropagationGraph flowGraph = work.getGraph();
////	  HashSet<IVariable> processedPoints = new HashSet<>();
////	  //find all second users
////	  ArrayList<PointsToSetVariable> allSecondUsers = findAllSecondUsers(leader, processedPoints);
////	  for (PointsToSetVariable secondUser : allSecondUsers) {
////	    synchronized (secondUser) {
////	      synchronized (targets) {
////	        secondUser.removeSome(targets); //sync?
////	      }
////	    }
////    }
////	  this.getContext().sender().tell(new Team(leader, allSecondUsers), getSelf());
//    return v;
//  }
//
//  private PointsToSetVariable processTaskForSingle(WorkContentForSingle work) {
//	  PointsToSetVariable ptv = work.getPointsToSetVariable();
//		int target = work.getTarget();
//		synchronized (ptv) {
////		  synchronized (target) {
//		     ptv.remove(target); //sync?
////      }
//    }
//		return ptv;
//	}
//
//  private void processTaskForFinding(WorkContentForFind work){
//    PointsToSetVariable leader = work.getPointsToSetVariable();
//    MutableIntSet targets = work.getTargets();
//    synchronized (leader) {
//      synchronized (targets) {
//        leader.removeSome(targets);
//      }
//    }
//    HashSet<IVariable> processedPoints = new HashSet<>();
//    //find all second users
//    ArrayList<PointsToSetVariable> allSecondUsers = findAllSecondUsers(leader, processedPoints);
//  }


//  private ArrayList<PointsToSetVariable> findAllSecondUsers(PointsToSetVariable L, HashSet<IVariable> processedPoints){
//    ArrayList<PointsToSetVariable> results = new ArrayList<>();
//    Iterator it = PropagationSystem.flowGraph.getStatementsThatUse(L);
//    while(it.hasNext()){
//      AbstractStatement s = (AbstractStatement) it.next();
//      IVariable iv = s.getLHS();
//      if(iv instanceof PointsToSetVariable && !processedPoints.contains(iv))
//      {
//        processedPoints.add(iv);
//        PointsToSetVariable pv = (PointsToSetVariable)iv;
////        addToWorkList(s);
//        results.add(pv);
//        results.addAll(findAllSecondUsers(pv, processedPoints));
//      }
//    }
//    return results;
//  }

}
