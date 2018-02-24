/*******************************************************************************
 * Copyright (c) 2002 - 2006 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.wala.ipa.callgraph.propagation;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import com.ibm.wala.akkaTaskScheduler.Hub;
import com.ibm.wala.akkaTaskScheduler.ResultLisenter;
import com.ibm.wala.akkaTaskScheduler.SchedulerForSpecial;
import com.ibm.wala.akkaTaskScheduler.SchedulerForResetSetAndRecompute;
import com.ibm.wala.akkaTaskScheduler.WorkStart;
import com.ibm.wala.classLoader.ArrayClass;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.fixedpoint.impl.AbstractFixedPointSolver;
import com.ibm.wala.fixedpoint.impl.DefaultFixedPointSolver;
import com.ibm.wala.fixedpoint.impl.Worklist;
import com.ibm.wala.fixpoint.AbstractOperator;
import com.ibm.wala.fixpoint.AbstractStatement;
import com.ibm.wala.fixpoint.IFixedPointSystem;
import com.ibm.wala.fixpoint.IVariable;
import com.ibm.wala.fixpoint.UnaryOperator;
import com.ibm.wala.fixpoint.UnaryStatement;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.propagation.PropagationCallGraphBuilder.FilterOperator;
import com.ibm.wala.ipa.callgraph.propagation.PropagationCallGraphBuilder.MutableBoolean;
import com.ibm.wala.ipa.callgraph.threadpool.ThreadHub;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyWarning;
import com.ibm.wala.model.java.lang.reflect.Array;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.MonitorUtil.IProgressMonitor;
import com.ibm.wala.util.collections.HashMapFactory;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.collections.Iterator2Collection;
import com.ibm.wala.util.collections.MapUtil;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.debug.VerboseAction;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.NumberedGraph;
import com.ibm.wala.util.heapTrace.HeapTracer;
import com.ibm.wala.util.intset.IntIterator;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.IntSetAction;
import com.ibm.wala.util.intset.IntSetUtil;
import com.ibm.wala.util.intset.MutableIntSet;
import com.ibm.wala.util.intset.MutableIntSetFactory;
import com.ibm.wala.util.intset.MutableMapping;
import com.ibm.wala.util.intset.MutableSharedBitVectorIntSet;
import com.ibm.wala.util.intset.MutableSharedBitVectorIntSetFactory;
import com.ibm.wala.util.intset.MutableSparseIntSet;
import com.ibm.wala.util.intset.MutableSparseIntSetFactory;
import com.ibm.wala.util.intset.OrdinalSet;
import com.ibm.wala.util.ref.ReferenceCleanser;
import com.ibm.wala.util.warnings.Warnings;
import com.sun.javafx.scene.paint.GradientUtils.Point;

import akka.actor.Actor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.pattern.Patterns;
import akka.util.Timeout;
import scala.collection.generic.BitOperations.Int;
import scala.concurrent.Await;
import scala.concurrent.Future;

/**
 * System of constraints that define propagation for call graph construction
 */
public class PropagationSystem extends DefaultFixedPointSolver<PointsToSetVariable> {

  private final static boolean DEBUG = false;

  private final static boolean DEBUG_MEMORY = false;

  private static int DEBUG_MEM_COUNTER = 0;

  private final static int DEBUG_MEM_INTERVAL = 5;

  private final static boolean TEST = true;

  /**
   * object that tracks points-to sets
   */
  public final PointsToMap pointsToMap = new PointsToMap();

  /**
   * Implementation of the underlying dataflow graph
   */
  public final PropagationGraph flowGraph = new PropagationGraph();

  /**
   * bijection from InstanceKey <=>Integer
   */
  protected final MutableMapping<InstanceKey> instanceKeys = MutableMapping.make();

  /**
   * A mapping from IClass -> MutableSharedBitVectorIntSet The range represents the instance keys that correspond to a given class.
   * This mapping is used to filter sets based on declared types; e.g., in cast constraints
   */
  final private Map<IClass, MutableIntSet> class2InstanceKey = HashMapFactory.make();

  /**
   * An abstraction of the pointer analysis result
   */
  public PointerAnalysis<InstanceKey> pointerAnalysis;

  /**
   * Meta-data regarding how pointers are modelled.
   */
  private final PointerKeyFactory pointerKeyFactory;

  /**
   * Meta-data regarding how instances are modelled.
   */
  private final InstanceKeyFactory instanceKeyFactory;

  /**
   * When doing unification, we must also updated the fixed sets in unary side effects.
   *
   * This maintains a map from PointsToSetVariable -> Set<UnarySideEffect>
   */
  final private Map<PointsToSetVariable, Set<UnarySideEffect>> fixedSetMap = HashMapFactory.make();

  /**
   * Governing call graph;
   */
  public static CallGraph cg;//bz:should be final

  private int verboseInterval = DEFAULT_VERBOSE_INTERVAL;

  private int periodicMaintainInterval = DEFAULT_PERIODIC_MAINTENANCE_INTERVAL;

  boolean useAkka = true;
//akka system
  public ActorSystem akkaSys;
  public ActorRef hub;
//  public ActorRef resultListener;
  int nrOfWorkers;

  //thread pool
  public ThreadHub threadHub;

  public PropagationSystem(CallGraph cg, PointerKeyFactory pointerKeyFactory, InstanceKeyFactory instanceKeyFactory) {
    if (cg == null) {
      throw new IllegalArgumentException("null cg");
    }
    this.cg = cg;
    this.pointerKeyFactory = pointerKeyFactory;
    this.instanceKeyFactory = instanceKeyFactory;

    // when doing paranoid checking of points-to sets, code in PointsToSetVariable needs to know about the instance key mapping
    if (PointsToSetVariable.PARANOID) {
      PointsToSetVariable.instanceKeys = instanceKeys;
    }
  }

  public void initializeAkkaSys(int nrOfWorkers){
    this.nrOfWorkers = nrOfWorkers;
    if(useAkka){
      startAkkaSys();//initialize sys
    }else{
      System.err.println("Thread pool initialized.");
      threadHub = new ThreadHub(nrOfWorkers);
    }
  }

  public PropagationGraph getPropagationGraph(){
    return flowGraph;
  }

  /**
   * @return an object which encapsulates the pointer analysis result
   */
  public PointerAnalysis<InstanceKey> makePointerAnalysis(PropagationCallGraphBuilder builder) {
    return new PointerAnalysisImpl(builder, cg, pointsToMap, instanceKeys, pointerKeyFactory, instanceKeyFactory);
  }

  protected void registerFixedSet(PointsToSetVariable p, UnarySideEffect s) {
    Set<UnarySideEffect> set = MapUtil.findOrCreateSet(fixedSetMap, p);
    try{//FIXME : JEFF
      set.add(s);
    }catch(Exception e){}//may throw NPE on hasCode()..
  }

  protected void updateSideEffects(PointsToSetVariable p, PointsToSetVariable rep) {
    Set<UnarySideEffect> set = fixedSetMap.get(p);
    if (set != null) {
      for (Iterator it = set.iterator(); it.hasNext();) {
        UnarySideEffect s = (UnarySideEffect) it.next();
        s.replaceFixedSet(rep);
      }
      Set<UnarySideEffect> s2 = MapUtil.findOrCreateSet(fixedSetMap, rep);
      s2.addAll(set);
      fixedSetMap.remove(p);
    }
  }

  /**
   * Keep this method private .. this returns the actual backing set for the class, which we do not want to expose to clients.
   */
  private MutableIntSet findOrCreateSparseSetForClass(IClass klass) {
    assert klass.getReference() != TypeReference.JavaLangObject;
    MutableIntSet result = class2InstanceKey.get(klass);
    if (result == null) {
      result = IntSetUtil.getDefaultIntSetFactory().make();
      class2InstanceKey.put(klass, result);
    }
    return result;
  }

  /**
   * @return a set of integers representing the instance keys that correspond to a given class. This method creates a new set, which
   *         the caller may bash at will.
   */
  MutableIntSet cloneInstanceKeysForClass(IClass klass) {
    assert klass.getReference() != TypeReference.JavaLangObject;
    MutableIntSet set = class2InstanceKey.get(klass);
    if (set == null) {
      return IntSetUtil.getDefaultIntSetFactory().make();
    } else {
      // return a copy.
      return IntSetUtil.getDefaultIntSetFactory().makeCopy(set);
    }
  }

  /**
   * @return a set of integers representing the instance keys that correspond to a given class, or null if there are none.
   * @throws IllegalArgumentException if klass is null
   */
  public IntSet getInstanceKeysForClass(IClass klass) {
    if (klass == null) {
      throw new IllegalArgumentException("klass is null");
    }
    assert klass != klass.getClassHierarchy().getRootClass();
    return class2InstanceKey.get(klass);
  }

  /**
   * @return the instance key numbered with index i
   */
  public InstanceKey getInstanceKey(int i) {
    return instanceKeys.getMappedObject(i);
  }

  public int getInstanceIndex(InstanceKey ik) {
    return instanceKeys.getMappedIndex(ik);
  }

  /**
   * TODO: optimize; this may be inefficient;
   *
   * @return an List of instance keys corresponding to the integers in a set
   */
  List<InstanceKey> getInstances(IntSet set) {
    LinkedList<InstanceKey> result = new LinkedList<InstanceKey>();
    for (IntIterator it = set.intIterator(); it.hasNext();) {
      int j = it.next();
      result.add(getInstanceKey(j));
    }
    return result;
  }

  @Override
  protected void initializeVariables() {
    // don't have to do anything; all variables initialized
    // by default to TOP (the empty set);
  }

  /**
   * record that a particular points-to-set is represented implicitly.
   */
  public void recordImplicitPointsToSet(PointerKey key) {
    if (key == null) {
      throw new IllegalArgumentException("null key");
    }
    if (key instanceof LocalPointerKey) {
      LocalPointerKey lpk = (LocalPointerKey) key;
      if (lpk.isParameter()) {
        System.err.println("------------------ ERROR:");
        System.err.println("LocalPointerKey: " + lpk);
        System.err.println("Constant? " + lpk.getNode().getIR().getSymbolTable().isConstant(lpk.getValueNumber()));
        System.err.println("   -- IR:");
        System.err.println(lpk.getNode().getIR());
        Assertions.UNREACHABLE("How can parameter be implicit?");
      }
    }
    pointsToMap.recordImplicit(key);
  }

  /**
   * If key is unified, returns the representative
   *
   * @param key
   * @return the dataflow variable that tracks the points-to set for key
   */
  public PointsToSetVariable findOrCreatePointsToSet(PointerKey key) {

    if (key == null) {
      throw new IllegalArgumentException("null key");
    }

    if (pointsToMap.isImplicit(key)) {//JEFF
        return null;
    }

    PointsToSetVariable result = pointsToMap.getPointsToSet(key);
    if (result == null) {
      result = new PointsToSetVariable(key);
      pointsToMap.put(key, result);
    } else {
      // check that the filter for this variable remains unique
      if (!pointsToMap.isUnified(key) && key instanceof FilteredPointerKey) {
        PointerKey pk = result.getPointerKey();
        if (!(pk instanceof FilteredPointerKey)) {
          // add a filter for all future evaluations.
          // this is tricky, but the logic is OK .. any constraints that need
          // the filter will see it ...
          // CALLERS MUST BE EXTRA CAREFUL WHEN DEALING WITH UNIFICATION!
          result.setPointerKey(key);
          pk = key;
        }
        FilteredPointerKey fpk = (FilteredPointerKey) pk;
        if (fpk == null) {
          Assertions.UNREACHABLE("fpk is null");
        }
        if (key == null) {
          Assertions.UNREACHABLE("key is null");
        }
        if (fpk.getTypeFilter() == null) {
          Assertions.UNREACHABLE("fpk.getTypeFilter() is null");
        }
        if (!fpk.getTypeFilter().equals(((FilteredPointerKey) key).getTypeFilter())) {
          Assertions.UNREACHABLE("Cannot use filter " + ((FilteredPointerKey) key).getTypeFilter() + " for " + key
              + ": previously created different filter " + fpk.getTypeFilter());
        }
      }
    }
    return result;
  }

  public int findOrCreateIndexForInstanceKey(InstanceKey key) {
    int result = instanceKeys.getMappedIndex(key);
    if (result == -1) {
      result = instanceKeys.add(key);
    }
    if (DEBUG) {
      System.err.println("getIndexForInstanceKey " + key + " " + result);
    }
    return result;
  }

  /**
   * NB: this is idempotent ... if the given constraint exists, it will not be added to the system; however, this will be more
   * expensive since it must check if the constraint pre-exits.
   *
   * @return true iff the system changes
   */
  public boolean newConstraint(PointerKey lhs, UnaryOperator<PointsToSetVariable> op, PointerKey rhs) {
    if (lhs == null) {
      throw new IllegalArgumentException("null lhs");
    }
    if (op == null) {
      throw new IllegalArgumentException("op null");
    }
    if (rhs == null) {
      throw new IllegalArgumentException("rhs null");
    }
    if (DEBUG) {
      System.err.println("Add constraint A: " + lhs + " " + op + " " + rhs);
    }
    PointsToSetVariable L = findOrCreatePointsToSet(lhs);
    if(L==null)
      return false;//JEFF
    PointsToSetVariable R = findOrCreatePointsToSet(rhs);
    if (op instanceof FilterOperator) {
      // we do not want to revert the lhs to pre-transitive form;
      // we instead want to check in the outer loop of the pre-transitive
      // solver if the value of L changes.
      pointsToMap.recordTransitiveRoot(L.getPointerKey());
      if (!(L.getPointerKey() instanceof FilteredPointerKey)) {
        Assertions.UNREACHABLE("expected filtered lhs " + L.getPointerKey() + " " + L.getPointerKey().getClass() + " " + lhs + " "
            + lhs.getClass());
      }
    }
    return newStatement(L, op, R, true, true);
  }


  public boolean delConstraintMultiInstancefromL(PointerKey lhs, MutableIntSet delset) {
    if (lhs == null) {
      throw new IllegalArgumentException("null lhs");
    }
    PointsToSetVariable L = findOrCreatePointsToSet(lhs);
    if(L == null)
      return false;//JEFF

    try{//FIXME: JEFF NPE
      if(lhs instanceof LocalPointerKey){
        LocalPointerKey LocalPK = (LocalPointerKey)L.getPointerKey();
        if(LocalPK.getNode().getMethod().isInit() || LocalPK.getNode().getMethod().isClinit())
          return false;
      }}catch(Exception e){return false;}//need to handle invocation

    if(delset == null)
      return false;
    //pack all instance and delete
//    if(!this.isOptimize)
      corePointsToDelWholeSet(L,delset);
//    processedPoints.clear();
    return true;
  }


//  public boolean addConstraintMultiInstancetoL(PointerKey lhs, MutableIntSet addset) {
//    if (lhs == null) {
//      throw new IllegalArgumentException("null lhs");
//    }
//    PointsToSetVariable L = findOrCreatePointsToSet(lhs);
//    if(L == null)
//      return false;//JEFF
//
//    try{//FIXME: JEFF NPE
//      if(lhs instanceof LocalPointerKey){
//        LocalPointerKey LocalPK = (LocalPointerKey)L.getPointerKey();
//        if(LocalPK.getNode().getMethod().isInit() || LocalPK.getNode().getMethod().isClinit())
//          return false;
//      }}catch(Exception e){return false;}//need to handle invocation
//
//    if(addset == null)
//      return false;
//    boolean code = L.addAll(addset);
//    if(!code)
//      return false;
//    //pack all instance and add
//    ArrayList<PointsToSetVariable> lhss = findFirstUsers(L);
//    if(lhss.size() == 0)
//      return false;
//    addOrDelASetFromMultiLhs(lhss, addset, true);
//    return true;
//  }

/**
 * 1 lhs <= multi rhs : Mode: delConstraintMultiR; modify it to another mode: sync(lhs) and add pts(rhs) in parallel
 */
  public boolean delConstraintHasMultiR(PointerKey lhs, AssignOperator op,
      ArrayList<PointsToSetVariable> rhss, MutableIntSet delset, PointsToSetVariable notouch) {
    if (lhs == null) {
      throw new IllegalArgumentException("null lhs");
    }
    if (op == null) {
      throw new IllegalArgumentException("op null");
    }
    PointsToSetVariable L = findOrCreatePointsToSet(lhs);
    if(L == null)
      return false;//JEFF

    if(getFirstDel()){
      setTheRoot(notouch);
    }

    try{//FIXME: JEFF NPE
      if(lhs instanceof LocalPointerKey){
        LocalPointerKey LocalPK = (LocalPointerKey)L.getPointerKey();
        if(LocalPK.getNode().getMethod().isInit() || LocalPK.getNode().getMethod().isClinit())
          return false;
      }}catch(Exception e){return false;}//need to handle invocation

    //remove edges
    for (PointsToSetVariable rhs : rhss) {
      delStatementS(L, op, rhs, true, true);
    }

    if(delset == null)
      return false;
    //pack all instance and delete
//    if(!this.isOptimize)
      corePointsToDelWholeSet(L, delset);
//    processedPoints.clear();
    return true;
  }



  public boolean addConstraintHasMultiR(PointerKey lhs, AssignOperator op, ArrayList<PointsToSetVariable> rhss){
    if (lhs == null) {
      throw new IllegalArgumentException("null lhs");
    }
    if (op == null) {
      throw new IllegalArgumentException("op null");
    }
    final PointsToSetVariable L = findOrCreatePointsToSet(lhs);
    if(L == null)
      return false;//JEFF

    try{//FIXME: JEFF NPE
      if(lhs instanceof LocalPointerKey){
        LocalPointerKey LocalPK = (LocalPointerKey)L.getPointerKey();
        if(LocalPK.getNode().getMethod().isInit() || LocalPK.getNode().getMethod().isClinit())
          return false;
      }}catch(Exception e){return false;}//need to handle invocation
  //add edges
    final MutableIntSet targets = IntSetUtil.getDefaultIntSetFactory().make();
    for (PointsToSetVariable rhs : rhss) {
      addStatement(L, op, rhs, true, true);
      MutableIntSet set = rhs.getValue();
      if(set != null){
        IntSetAction action = new IntSetAction() {
          @Override
          public void act(int x) {
            if(!L.contains(x)){
              targets.add(x);
            }
          }
        };
        set.foreachExcluding(L.getValue(), action);
      }
    }
    if(targets.isEmpty())
      return false;
    L.addAll(targets);
    ArrayList<PointsToSetVariable> lhss = findFirstUsers(L);
    if(lhss.size() == 0)
      return false;
    //propagate
//    System.out.println("Start AkkaSys for addConstraintHasMultiR: ---- nrOfWorks = " + lhss.size());
    addOrDelASetFromMultiLhs(lhss, targets, true);
    return true;
  }

  private void addOrDelASetFromMultiLhs(ArrayList<PointsToSetVariable> lhss, MutableIntSet targets, boolean isAddition){
    if(useAkka){
//      System.out.println("Start AkkaSys for multi l: ---- nrOfWorks = " + lhss.size());
      hub.tell(new SchedulerForSpecial(lhss, targets, isAddition, this), hub);
      awaitHubComplete();
    }else{
      try {
        threadHub.initialSpecialTasks(lhss, targets, isAddition, this);
      } catch (InterruptedException | ExecutionException e) {
        e.printStackTrace();
      }
    }
  }

  public boolean addConstraintHasMultiL(ArrayList<PointsToSetVariable> lhss, AssignOperator assignoperator,
      PointsToSetVariable rhs, MutableIntSet targets) {
    if(rhs == null)
      return false;
    for (PointsToSetVariable lhs : lhss) {
      if(lhs != null){
        addStatement(lhs, assignoperator, rhs, true, true);
      }
    }
    if(targets.isEmpty())
      return false;

    int nrOfWorks = lhss.size();
    if(nrOfWorks == 0){
      return false;
    }
//    System.out.println("Start AkkaSys for addConstraintHasMultiL: ---- nrOfWorks = " + lhss.size());
    addOrDelASetFromMultiLhs(lhss, targets, true);
    return true;
  }

  public boolean delConstraintHasMultiL(ArrayList<PointsToSetVariable> lhss, AssignOperator op,
   PointsToSetVariable rhs, final MutableIntSet targets) {
    if(rhs == null)
      return false;
    else{
      for (PointsToSetVariable lhs : lhss) {
        if(lhs != null)
          delStatementS(lhs, op, rhs, true, true);
      }

      int nrOfWorks = lhss.size();
      if(nrOfWorks == 0){
        return false;
      }

      if(getFirstDel()){
        setTheRoot(rhs);
      }
//      System.out.println("Start AkkaSys for delConstraintHasMultiL: ---- nrOfWorks = " + lhss.size());
      addOrDelASetFromMultiLhs(lhss, targets, false);
      return true;
    }
  }

  public boolean delStatementS(PointsToSetVariable lhs, UnaryOperator operator, PointsToSetVariable rhs, boolean toWorkList, boolean eager) {
    if (operator == null) {
      throw new IllegalArgumentException("operator is null");
    }
    UnaryStatement s = operator.makeEquation(lhs, rhs);
    if (!getFixedPointSystem().containsStatement(s)) {
      return false;
    }else{
      if(getFirstDel()){
        getFixedPointSystem().delStatement(s);
        return true;
      }
      return false;
    }
  }


  public boolean delConstraintMultiInstanceFromL(ArrayList<PointsToSetVariable> lhss, MutableIntSet targets, PointsToSetVariable notouch) {
    int nrOfWorks = lhss.size();
    if(lhss.size() ==0)
      return false;
    if(targets == null)
      return false;
    if(getFirstDel()){
      setTheRoot(notouch);
    }
//    System.out.println("Start AkkaSys for delConstraintMultiInstanceFromL ---- nrOfWorks = " + nrOfWorks);
    addOrDelASetFromMultiLhs(lhss, targets, false);
    return true;
  }


//  public boolean addConstraintHasMultiLSeperate(ArrayList<PointsToSetVariable> lhssAssign, ArrayList<PointsToSetVariable> lhssFilter,
//      AssignOperator assignoperator, FilterOperator filterOperator, PointsToSetVariable rhs, MutableIntSet targets) {
//    if(rhs == null )
//      return false;
//    if(lhssAssign.size() == 0 && lhssFilter.size() == 0)
//      return false;
//    for (PointsToSetVariable lF : lhssFilter) {
//      if(lF != null){
//        pointsToMap.recordTransitiveRoot(lF.getPointerKey());
//        if (!(lF.getPointerKey() instanceof FilteredPointerKey)) {
//          Assertions.UNREACHABLE("expected filtered lhs " + lF.getPointerKey() + " " + lF.getPointerKey().getClass() + " " + lF.getPointerKey() + " "
//              + lF.getPointerKey().getClass());
//        }
//        addStatement(lF, filterOperator, rhs, true, true);
//      }
//    }
//
//    for (PointsToSetVariable lA : lhssAssign) {
//      if(lA != null){
//        addStatement(lA, assignoperator, rhs, true, true);
//      }
//    }
//
//    ArrayList<PointsToSetVariable> lhss = new ArrayList<>();
//    lhss.addAll(lhssAssign);
//    lhss.addAll(lhssFilter);
//    int nrOfWorks = lhss.size();
//    System.out.println("addConstraintHasMultiLSeperate ---- nrOfWorks = " + nrOfWorks);
//    addOrDelASetFromMultiLhs(lhss, targets, true);
//    return true;
//  }


  //sz
  public boolean delConstraint(PointerKey lhs, UnaryOperator<PointsToSetVariable> op, PointerKey rhs) {
    if (lhs == null) {
      throw new IllegalArgumentException("null lhs");
    }
    if (op == null) {
      throw new IllegalArgumentException("op null");
    }
    if (DEBUG) {
      System.err.println("Delete constraint A: " + lhs + " " + op + " " + rhs);
    }
    PointsToSetVariable L = findOrCreatePointsToSet(lhs);
    if(L==null)
      return false;//JEFF

    PointsToSetVariable R = findOrCreatePointsToSet(rhs);
    if(R == null)
      return false;

    if(getFirstDel()){
      setTheRoot(R);
    }

    if (op instanceof FilterOperator) {
      if (!(L.getPointerKey() instanceof FilteredPointerKey)) {
        Assertions.UNREACHABLE("expected filtered lhs " + L.getPointerKey() + " " + L.getPointerKey().getClass() + " " + lhs + " "
            + lhs.getClass());
      }
    }

    try{//FIXME: JEFF NPE
      if(lhs instanceof LocalPointerKey){
        LocalPointerKey LocalPK = (LocalPointerKey)L.getPointerKey();
        if(LocalPK.getNode().getMethod().isInit() || LocalPK.getNode().getMethod().isClinit())
          return false;
      }}catch(Exception e){return false;}//need to handle invocation

    if(op instanceof AssignOperator){
      IntSet delSet = R.getValue();
      if(delSet==null)
        return false;//JEFF

      //remove the statement first
      delStatementS(L, op, R, true, true);

//      if(!this.isOptimize)
        corePointsToDelWholeSet(L, delSet);
//      processedPoints.clear();
      return true;
    }
    else
      return delStatement(L, op, R, true, true);
  }



  private void transitiveDelete(PointsToSetVariable L, final IntSet delSet)
  {
    boolean changed = false;
    IntIterator isetit = delSet.intIterator();
    while(isetit.hasNext())
    {
      int index = isetit.next();
      if (L.contains(index)) {
        L.remove(index);
        changed = true;
      }
    }

    if (changed
        &&L.getGraphNodeId() > -1) {

      for (Iterator it = getFixedPointSystem().getStatementsThatUse(L); it.hasNext();) {
        AbstractStatement s = (AbstractStatement) it.next();
        IVariable iv = s.getLHS();
        if(iv instanceof PointsToSetVariable)
        {
          PointsToSetVariable pv = (PointsToSetVariable)iv;
          transitiveDelete(pv,delSet);
        }
      }

    }
  }

//  HashSet<IVariable> processedPoints = new HashSet<>();
  /**
   * Key function for handling deletion
   * 1. delete instance key id from points to set
   * 2. add the node to reset
   * 3. add all affected statements to worklist
   * @param L
   * @param index
   */
//  private void corePointsToDel(PointsToSetVariable L, final int delIndex)
//  {
//    if (L.contains(delIndex)) {
//      L.remove(delIndex);
////      if(!changes.contains(L))
////        changes.add(L);
//
//      if(L.getGraphNodeId() > -1) {
//
//        addToResetNodes(L);
//        for (Iterator it = getFixedPointSystem().getStatementsThatUse(L); it.hasNext();) {
//          AbstractStatement s = (AbstractStatement) it.next();
//          IVariable iv = s.getLHS();
//          if(iv instanceof PointsToSetVariable && !processedPoints.contains(iv))
//          {
//            processedPoints.add(iv);
////            System.out.println(iv.toString());
////            counter++;
//            PointsToSetVariable pv = (PointsToSetVariable)iv;
//            corePointsToDel(pv,delIndex);
//            addToWorkList(s);
//          }
//        }
//
//      }
//    }
//  }

//  public static HashMap<PointsToSetVariable, ArrayList<PointsToSetVariable>> resetTeams = new HashMap<PointsToSetVariable, ArrayList<PointsToSetVariable>>();
//
//  private ArrayList<PointsToSetVariable> findAllUsers(PointsToSetVariable L){
//    ArrayList<PointsToSetVariable> results = new ArrayList<>();
//    Iterator it = getFixedPointSystem().getStatementsThatUse(L);
//    while(it.hasNext()){
//      AbstractStatement s = (AbstractStatement) it.next();
//      IVariable iv = s.getLHS();
//      if(iv instanceof PointsToSetVariable && !processedPoints.contains(iv))
//      {
//        processedPoints.add(iv);
//        PointsToSetVariable pv = (PointsToSetVariable)iv;
////        addToWorkList(s);
//        results.add(pv);
//        ArrayList<PointsToSetVariable> secondUsers = findAllSecondUsers(pv);
//        results.addAll(secondUsers);
//        resetTeams.putIfAbsent(pv, secondUsers);
//      }
//    }
//    return results;
//  }

  ArrayList<PointsToSetVariable> findFirstUsers(PointsToSetVariable L) {
    ArrayList<PointsToSetVariable> results = new ArrayList<>();
    Iterator it = getFixedPointSystem().getStatementsThatUse(L);
    while(it.hasNext()){
      AbstractStatement s = (AbstractStatement) it.next();
      AbstractOperator op = s.getOperator();
      if(op instanceof AssignOperator){
        IVariable iv = s.getLHS();
        PointsToSetVariable pv = (PointsToSetVariable)iv;
        results.add(pv);
      }else
        addToWorklistAkka(s);
    }
    return results;
  }

  private ArrayList<PointsToSetVariable> findAllSecondUsers(PointsToSetVariable L){
    ArrayList<PointsToSetVariable> results = new ArrayList<>();
    Iterator it = getFixedPointSystem().getStatementsThatUse(L);
    while(it.hasNext()){
      AbstractStatement s = (AbstractStatement) it.next();
      IVariable iv = s.getLHS();
      if(iv instanceof PointsToSetVariable)// && !processedPoints.contains(iv)
      {
//        processedPoints.add(iv);
        PointsToSetVariable pv = (PointsToSetVariable)iv;
//        addToWorkList(s);
        results.add(pv);
        results.addAll(findAllSecondUsers(pv));
      }
    }
    return results;
  }

//  private void corePointsToDelSingle(PointsToSetVariable L, final int delIndex) {
//    long start = System.currentTimeMillis();
//    if(L.contains(delIndex)){
//      //recompute L
//      boolean reached = recomputeSingle(L, delIndex);
//      //check change
//      if(!reached){
//        L.remove(delIndex);
//        MutableIntSet delSet = IntSetUtil.make();
//        delSet.add(delIndex);
//        AbstractFixedPointSolver.changes.add(L);
//        if(L.getGraphNodeId() > -1){
//          final ArrayList<PointsToSetVariable> firstUsers = findFirstUsers(L);
//          final int nrOfWorks = firstUsers.size();
//          if(nrOfWorks == 0){
//            System.out.println("---- nrOfWorks = 0");
//          }else if(nrOfWorks == 1){
//            PointsToSetVariable first = firstUsers.get(0);
//            simpleCorePointsToDelSingle(first, delSet);
//          }else {
//            System.out.println("Start AkkaSys for Deleting (re & re) single ---- nrOfWorks = " + nrOfWorks);
////            long sysStart = System.currentTimeMillis();
//            hub.tell(new SchedulerForResetSetAndRecompute(delSet, firstUsers), hub);
//            boolean goon = true;
//            while(goon){
//              try {
//                Thread.sleep(10);
//              } catch (InterruptedException e) {
//                e.printStackTrace();
//              }
//              goon = Hub.askstatus();
//            }
//          }
//        }
//      }
//    }
//  }

  private void simpleCorePointsToDelSingle(final PointsToSetVariable L, final MutableIntSet targets){
    if(theRoot.contains(L))
      return;
    if(flowGraph.getNumberOfStatementsThatDef(L) == 0)//root
      return;
    final MutableSharedBitVectorIntSet remaining = new MutableSharedBitVectorIntSetFactory().makeCopy(targets);
    for (Iterator it = flowGraph.getStatementsThatDef(L); it.hasNext();) {
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
          pv.getValue().foreach(action);
        }
      }
    }
    //if not reachable, deleting, and continue for other nodes
    if(!remaining.isEmpty()){
      MutableSharedBitVectorIntSet removed = L.removeSome(remaining);
      if(removed.size() > 0){
        if(!changes.contains(L)){
          changes.add(L);
        }
        inner2CorePointsToDel2Better(L, removed);
      }
    }else{//all included, early return
      return;
    }
  }

  private void inner2CorePointsToDel2Better(PointsToSetVariable L, final MutableIntSet targets){
    for (Iterator it = flowGraph.getStatementsThatUse(L); it.hasNext();) {
      AbstractStatement s = (AbstractStatement) it.next();
      AbstractOperator op = s.getOperator();
      if(op instanceof AssignOperator){
        PointsToSetVariable pv = (PointsToSetVariable) s.getLHS();
        if(pv.getValue() != null)
          simpleCorePointsToDelSingle(pv, targets);
      }else if(op instanceof FilterOperator){
        FilterOperator filter = (FilterOperator) op;
        PointsToSetVariable pv = (PointsToSetVariable) s.getLHS();
        if(theRoot.contains(pv))
          continue;
        byte mark = filter.evaluateDel(pv, L);
        if(mark == 1){
          if(!changes.contains(pv)){
            changes.add(pv);
          }
          inner2CorePointsToDel2Better(pv, targets);
        }
      }else{// if(s instanceof UnaryStatement && iv == null)
        addToWorklistAkka(s);
      }
    }
  }

//  private MutableSparseIntSet recomputeSet(PointsToSetVariable L, final MutableIntSet targets){
//    final MutableSparseIntSet remaining = new MutableSparseIntSetFactory().makeCopy(targets);
//    for (Iterator it = flowGraph.getStatementsThatDef(L); it.hasNext();) {
//      UnaryStatement s = (UnaryStatement) it.next();
//      IVariable iv = s.getRightHandSide();
//      if(iv instanceof PointsToSetVariable){
//        PointsToSetVariable pv = (PointsToSetVariable)iv;
//        if(pv.getValue() != null){
//          IntSetAction action = new IntSetAction() {
//            @Override
//            public void act(int i) {
//              if(remaining.isEmpty())
//                return;
//              if(targets.contains(i)){
//                remaining.remove(i);
//              }
//            }
//          };
//          pv.getValue().foreach(action);
//        }
//      }
//    }
//    //reachability user
////    boolean isReachable = false;
////    IntIterator intIterator = targets.intIterator();
////    while(intIterator.hasNext()){
////      int delIndex = intIterator.next();
////      InstanceKey instKey = PropagationCallGraphBuilder.system.getInstanceKey(delIndex);
////      Iterator<Pair<CGNode, NewSiteReference>> pairIt = instKey.getCreationSites(PropagationSystem.cg);
////      while(pairIt.hasNext()){//should be unique??
////        Pair<CGNode, NewSiteReference> pair = pairIt.next();
////        CGNode n = pair.fst;
////        NewSiteReference site = pair.snd;
////        SSAInstruction inst2;
////        if(n.getIR().existNew(site)){
////          inst2 = n.getIR().getNew(site);
////        }else{
////          continue;
////        }
////        Iterator<SSAInstruction> useIt =n.getDU().getUses(inst2.getDef());
////        while(useIt.hasNext()){//may have multiple
////          SSAInstruction useInstruction = useIt.next();
////          int defIndex = useInstruction.getDef();
////          if(defIndex==-1) continue;
////          PointerKey basePointerKey = PropagationCallGraphBuilder.system.pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, defIndex);
////          PointsToSetVariable baseVar = PropagationCallGraphBuilder.system.findOrCreatePointsToSet(basePointerKey);
////          //the statement should have already been removed from the graph
////          if(baseVar!=null){
////            isReachable = isReachableInFlowGraph(baseVar, L);//isReachableWithoutEdgeR2L(baseVar,L,R);
////            if(isReachable) {
////              localtargets.remove(delIndex);
////              store.clear();
////              break;
////            }
////          }
////          store.clear();
////        }
////      }
////    }
//    return remaining;
//  }

//  private boolean recomputeSingle(PointsToSetVariable L, int delIndex){
//    //reachability user
//    boolean isReachable = false;
//    InstanceKey instKey = PropagationCallGraphBuilder.system.getInstanceKey(delIndex);
//    Iterator<Pair<CGNode, NewSiteReference>> pairIt = instKey.getCreationSites(PropagationSystem.cg);
//    while(pairIt.hasNext()){//should be unique??
//      Pair<CGNode, NewSiteReference> pair = pairIt.next();
//      CGNode n = pair.fst;
//      NewSiteReference site = pair.snd;
//      SSAInstruction inst2;
//      if(n.getIR().existNew(site)){
//        inst2 = n.getIR().getNew(site);
//      }else{
//        continue;
//      }
//      Iterator<SSAInstruction> useIt =n.getDU().getUses(inst2.getDef());
//      while(useIt.hasNext()){//may have multiple
//        SSAInstruction useInstruction = useIt.next();
//        int defIndex = useInstruction.getDef();
//        if(defIndex==-1) continue;
//        PointerKey basePointerKey = PropagationCallGraphBuilder.system.pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, defIndex);
//        PointsToSetVariable baseVar = PropagationCallGraphBuilder.system.findOrCreatePointsToSet(basePointerKey);
//        //the statement should have already been removed from the graph
//        if(baseVar!=null){
//          isReachable = isReachableInFlowGraph(baseVar, L);//isReachableWithoutEdgeR2L(baseVar,L,R);
//          if(isReachable) {
//            store.clear();
//            return isReachable;
//          }
//        }
//        store.clear();
//      }
//    }
//    return isReachable;
//  }



//  public boolean reConstruct3(IProgressMonitor monitor){
//    if(!resetTeams.keySet().isEmpty()){
//        final int nrOfWorks = resetTeams.keySet().size();
//        //create akka reset system
//        System.out.println("Start AkkaSys for Reset ---- nrOfWorks: "+ nrOfWorks);
//        long sysStart = System.currentTimeMillis();
//        akkaSys = ActorSystem.create();
//        resultListener = akkaSys.actorOf(Props.create(ResultLisenter.class), "listener");
//            //akkaSys.actorOf(new Props(ResultLisenter.class), "listener");
//        ActorRef scheduler = akkaSys.actorOf(Props.create(SchedulerForRecompute.class, nrOfWorks, nrOfWorkers, resetTeams, resultListener), "SchedulerForRcompute" + (numOfAkkaSys++));
//            //akkaSys.actorOf(new Props(new UntypedActorFactory() {
////          @Override
////          public Actor create() {
////            return new SchedulerForReset(nrOfWorks, nrOfWorkers, resetTeams, resultListener);
////          }
////        }), "SchedulerForReset" + (numOfAkkaSys++));
//        scheduler.tell(new WorkStart(), scheduler);
////        scheduler.tell(new WorkStart());
//        akkaSys.awaitTermination();
//        System.out.println("AKKA SYSTEM TIME: " + (System.currentTimeMillis() - sysStart) + "\t");
//      }
//    resetTeams.clear();
//    return true;
//  }

//  private void findAllVariable(PointsToSetVariable root){
//    if(root.getGraphNodeId() > -1){
//      addToResetNodes(root);
//      for(Iterator it = getFixedPointSystem().getStatementsThatUse(root); it.hasNext();){
//        AbstractStatement s = (AbstractStatement) it.next();
//        IVariable iv = s.getLHS();
//        if(iv instanceof PointsToSetVariable)
//        {
////          processedPoints.add(iv);
//          PointsToSetVariable pv = (PointsToSetVariable)iv;
//          findAllVariable(root);
//          addToWorkList(s);
//        }
//      }
//    }
//  }

  private void corePointsToDelWholeSet(PointsToSetVariable L, final IntSet delSet) {
    if(theRoot.contains(L))
      return;
    if(flowGraph.getNumberOfStatementsThatDef(L) == 0)//root
      return;
    //recompute L
    final MutableSharedBitVectorIntSet remaining = new MutableSharedBitVectorIntSetFactory().makeCopy(delSet);
    for (Iterator it = flowGraph.getStatementsThatDef(L); it.hasNext();) {
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
              if(delSet.contains(i)){
                remaining.remove(i);
              }
            }
          };
          pv.getValue().foreach(action);
        }
      }
    }
    //schedule task if changes
      if(!remaining.isEmpty()){
        MutableSharedBitVectorIntSet removed = L.removeSome(remaining);
        if(removed.size() > 0){
          if(!changes.contains(L)){
            changes.add(L);
          }
          final ArrayList<PointsToSetVariable> firstUsers = findFirstUsers(L);
          final int nrOfWorks = firstUsers.size();
          if(nrOfWorks == 0){
            return;
//            System.out.println("---- nrOfWorks = 0");
          }else if(nrOfWorks == 1){
            PointsToSetVariable first = firstUsers.get(0);
            simpleCorePointsToDelSingle(first, (MutableIntSet) delSet);
          }else{
            if(useAkka){
//              System.out.println("Start AkkaSys for Deleting (re & re) set ---- nrOfWorks = " + nrOfWorks);
              hub.tell(new SchedulerForResetSetAndRecompute(removed, firstUsers, this), hub);
              awaitHubComplete();
            }else{
              try {
                threadHub.initialRRTasks(removed, firstUsers, this);
              } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
              }
            }
          }
        }
      }else{
        return;
      }
  }


  public void awaitHubComplete(){
    boolean goon = true;
    while(goon){
      try {
        Thread.sleep(10);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      goon = Hub.askstatus();
    }
    return;
  }
//long comb = System.currentTimeMillis();
//System.out.println("finish combine: "+(comb-wait));


  HashSet<PointsToSetVariable> store = new HashSet<>();// for isreachable
//  HashSet<PointsToSetVariable> already = new HashSet<>();

//  private void corePointsToDel2(PointsToSetVariable L, final int delIndex)
//  {
//    if (L.contains(delIndex)) {
////             System.out.println();
////             System.out.println("start opt:   "+L.toString());
//      //an alternative -- optimization
//      //if(delOptimal)
//      {
//        //should have already removed the edge from points to graph
//
//        //for each id in delSet, find out the points-to-set variable corresponding to this id
//        boolean isReachable = false;
//        //TODO:
//        //1. find root node for this id  -- HOW??
//        //2. check reachability -- if not reachable, delete and repeat, otherwise, stop
//
//        InstanceKey instKey = this.getInstanceKey(delIndex);
//        Iterator<Pair<CGNode, NewSiteReference>> pairIt = instKey.getCreationSites(cg);
////        int sizeOfNSR = 0;
//        while(pairIt.hasNext())//should be unique??
//        {
//          Pair<CGNode, NewSiteReference> pair = pairIt.next();
//          CGNode n = pair.fst;
//          NewSiteReference site = pair.snd;
//          SSAInstruction inst2;
//          if(n.getIR().existNew(site)){
//            inst2 = n.getIR().getNew(site);
//          }else{
//            continue;
//          }
////          sizeOfNSR++;
//          Iterator<SSAInstruction> useIt =n.getDU().getUses(inst2.getDef());
//          while(useIt.hasNext())
//          {//may have multiple
//            SSAInstruction useInstruction = useIt.next();
//            //consider different types of instructions
//            //return, field, call, array
//            int defIndex = useInstruction.getDef();
//            if(defIndex==-1) continue;
//            PointerKey basePointerKey = this.pointerAnalysis.getHeapModel().getPointerKeyForLocal(n, defIndex);
//            PointsToSetVariable baseVar = findOrCreatePointsToSet(basePointerKey);
//            //the statement should have already been removed from the graph
//            if(baseVar!=null)
//            {
//              //is there a path in the points-to graph from baseVar to L, without going through R->L
//              //              System.out.println("test reachable:   "+baseVar.toString());
//              isReachable = isReachableInFlowGraph(baseVar,L);//isReachableWithoutEdgeR2L(baseVar,L,R);
////              counter++;
//              if(isReachable) {
//                //                System.out.println("is reachable:   "+baseVar.toString());
//                store.clear();
//                return;
//              }
//            }
//            store.clear();
//          }
////          if(sizeOfNSR>1)
////            System.out.println("NSR >1");
//        }
//        //         if(!(instKey instanceof NormalAllocationInNode))
//        //         {
//        //           L.remove(index);continue;//we only care about AllocationSiteInNode
//        //         }
//        //         CGNode n = ((AllocationSiteInNode) instKey).getNode();
//        //         NewSiteReference site = ((AllocationSiteInNode) instKey).getSite();
////        System.out.println("nsr num: "+sizeOfNSR);
//
//        //if not reachable, deleting, and continue for other nodes
//        {
//          L.remove(delIndex);
////          System.out.println("remove del index:   " + delIndex);
//          if(!changes.contains(L))
//            changes.add(L);
//
//          for (Iterator it = flowGraph.getStatementsThatUse(L); it.hasNext();) {
//            AbstractStatement s = (AbstractStatement) it.next();
//            IVariable iv = s.getLHS();
//            if(iv instanceof PointsToSetVariable && !processedPoints.contains(iv))
//            {
//              processedPoints.add(iv);
//              PointsToSetVariable pv = (PointsToSetVariable)iv;
//              corePointsToDel2(pv,delIndex);
//              //               System.out.println("recursive callback:   "+pv.toString());
//            }
//          }
//        }
//      }
//
//    }
//
//
//  }

  private boolean isReachableInFlowGraph(PointsToSetVariable startVar, PointsToSetVariable endVar) {

    //TODO: handle loops
    for (Iterator it = flowGraph.getStatementsThatUse(startVar); it.hasNext();) {
      AbstractStatement s = (AbstractStatement) it.next();
      IVariable iv = s.getLHS();
      if(store.contains(startVar)){
        return false;
      }else{
        store.add(startVar);
      }
      //     System.out.println("start var:   "+startVar.toString());
      if(iv==endVar) return true;
      else if(iv instanceof PointsToSetVariable)
      {
        //Skip libraries
        if(!iv.toString().contains("< Application"))
          continue;

        boolean isReachable =isReachableInFlowGraph((PointsToSetVariable)iv,endVar);
        if(isReachable) return true;

      }
    }
    return false;
  }

  private boolean isReachableWithoutEdgeR2L(PointsToSetVariable startVar, PointsToSetVariable endVar, PointsToSetVariable rPoint) {

    //TODO: handle loops
    if(startVar==rPoint) return false;

    for (Iterator it = flowGraph.getStatementsThatUse(startVar); it.hasNext();) {
      AbstractStatement s = (AbstractStatement) it.next();
      IVariable iv = s.getLHS();
      if(iv instanceof PointsToSetVariable)
      {
        PointsToSetVariable pv = (PointsToSetVariable)iv;
        if(pv==endVar)
          return true;
        if(pv!=rPoint)
        {
          boolean isReachable =isReachableWithoutEdgeR2L(pv,endVar,rPoint);
          if(isReachable) return true;
        }
      }
    }
    return false;
  }

  //sz: delete a constraint from data flow grpah
  public boolean delConstraint(PointerKey lhs, InstanceKey value){

    if (DEBUG) {
      System.err.println("Delete constraint B: " + lhs + " U= " + value);
    }

    PointsToSetVariable L = findOrCreatePointsToSet(lhs);
    int index = findOrCreateIndexForInstanceKey(value);
    MutableIntSet delSet = IntSetUtil.make();
    delSet.add(index);
//    L.remove(index);
//    if(isChange && !changes.contains(L)){
//      changes.add(L);
//    }

//    if(!this.isOptimize)
      corePointsToDelWholeSet(L, delSet);
//    processedPoints.clear();

    return true;
  }


  //sz
  public void delSideEffect(UnaryOperator<PointsToSetVariable> op, PointerKey arg0) {
    if (arg0 == null) {
      throw new IllegalArgumentException("null arg0");
    }
    if (DEBUG) {
      System.err.println("delete constraint D: " + op + " --- to  " + arg0);
    }
    assert !pointsToMap.isUnified(arg0);
    PointsToSetVariable v1 = findOrCreatePointsToSet(arg0);
    delStatement(null, op, v1, true, true);
  }

  // sz
  public void delSideEffect(AbstractOperator<PointsToSetVariable> op, PointerKey[] arg0) {
    if (arg0 == null) {
      throw new IllegalArgumentException("null arg0");
    }
    if (DEBUG) {
      System.err.println("delete constraint D: " + op + " " + arg0);
    }
    PointsToSetVariable[] vs = new PointsToSetVariable[ arg0.length ];
    for(int i = 0; i < arg0.length; i++) {
      assert !pointsToMap.isUnified(arg0[i]);
      vs[i] = findOrCreatePointsToSet(arg0[i]);
    }
    delStatement(null, op, vs, true, true);
  }
  //*** Just in case, for ast SSA
  // sz
  public void delSideEffect(AbstractOperator<PointsToSetVariable> op, PointerKey arg0, PointerKey arg1) {
    if (DEBUG) {
      System.err.println("delete constraint D: " + op + " " + arg0);
    }
    assert !pointsToMap.isUnified(arg0);
    assert !pointsToMap.isUnified(arg1);
    PointsToSetVariable v1 = findOrCreatePointsToSet(arg0);
    PointsToSetVariable v2 = findOrCreatePointsToSet(arg1);
    delStatement(null, op, v1, v2, true, true);
  }
  public boolean newConstraint(PointerKey lhs, AbstractOperator<PointsToSetVariable> op, PointerKey rhs) {
    if (lhs == null) {
      throw new IllegalArgumentException("lhs null");
    }
    if (op == null) {
      throw new IllegalArgumentException("op null");
    }
    if (rhs == null) {
      throw new IllegalArgumentException("rhs null");
    }
    if (DEBUG) {
      System.err.println("Add constraint A: " + lhs + " " + op + " " + rhs);
    }
    assert !pointsToMap.isUnified(lhs);
    assert !pointsToMap.isUnified(rhs);
    PointsToSetVariable L = findOrCreatePointsToSet(lhs);
    PointsToSetVariable R = findOrCreatePointsToSet(rhs);
    return newStatement(L, op, new PointsToSetVariable[] { R }, true, true);
  }

  public boolean newConstraint(PointerKey lhs, AbstractOperator<PointsToSetVariable> op, PointerKey rhs1, PointerKey rhs2) {
    if (lhs == null) {
      throw new IllegalArgumentException("null lhs");
    }
    if (op == null) {
      throw new IllegalArgumentException("null op");
    }
    if (rhs1 == null) {
      throw new IllegalArgumentException("null rhs1");
    }
    if (rhs2 == null) {
      throw new IllegalArgumentException("null rhs2");
    }
    if (DEBUG) {
      System.err.println("Add constraint A: " + lhs + " " + op + " " + rhs1 + ", " + rhs2);
    }
    assert !pointsToMap.isUnified(lhs);
    assert !pointsToMap.isUnified(rhs1);
    assert !pointsToMap.isUnified(rhs2);
    PointsToSetVariable L = findOrCreatePointsToSet(lhs);
    PointsToSetVariable R1 = findOrCreatePointsToSet(rhs1);
    PointsToSetVariable R2 = findOrCreatePointsToSet(rhs2);
    return newStatement(L, op, R1, R2, true, true);
  }

  /**
   * @return true iff the system changes
   */
  public boolean newFieldWrite(PointerKey lhs, UnaryOperator<PointsToSetVariable> op, PointerKey rhs, PointerKey container) {
    return newConstraint(lhs, op, rhs);
  }

  /**
   * @return true iff the system changes
   */
  public boolean newFieldRead(PointerKey lhs, UnaryOperator<PointsToSetVariable> op, PointerKey rhs, PointerKey container) {
    return newConstraint(lhs, op, rhs);
  }

  /**
   * @return true iff the system changes
   */
  public boolean newConstraint(PointerKey lhs, InstanceKey value) {
    if (DEBUG) {
      System.err.println("Add constraint B: " + lhs + " U= " + value);
    }
    pointsToMap.recordTransitiveRoot(lhs);

    // we don't actually add a constraint.
    // instead, we immediately add the value to the points-to set.
    // This works since the solver is monotonic with TOP = {}
    PointsToSetVariable L = findOrCreatePointsToSet(lhs);
    int index = findOrCreateIndexForInstanceKey(value);
    if(L == null)
      return false;
    if (L.contains(index)) {
      // a no-op
      return false;
    } else {
      L.add(index);
      if(isChange &&!changes.contains(L))
        changes.add(L);

      // also register that we have an instanceKey for the klass
      assert value.getConcreteType() != null;

      if (!value.getConcreteType().getReference().equals(TypeReference.JavaLangObject)) {
        registerInstanceOfClass(value.getConcreteType(), index);
      }

      // we'd better update the worklist appropriately
      // if graphNodeId == -1, then there are no equations that use this
      // variable.
      if (L.getGraphNodeId() > -1) {
        changedVariable(L);
//        corePointsToDelSingle(L, index, true); //bz:coretodeledition for addition
      }
      return true;
    }

  }

  /**
   * Record that we have a new instanceKey for a given declared type.
   */
  private void registerInstanceOfClass(IClass klass, int index) {

    if (DEBUG) {
      System.err.println("registerInstanceOfClass " + klass + " " + index);
    }

    assert !klass.getReference().equals(TypeReference.JavaLangObject);

    try {
      IClass T = klass;
      registerInstanceWithAllSuperclasses(index, T);
      registerInstanceWithAllInterfaces(klass, index);

      if (klass.isArrayClass()) {
        ArrayClass aClass = (ArrayClass) klass;
        int dim = aClass.getDimensionality();
        registerMultiDimArraysForArrayOfObjectTypes(dim, index, aClass);

        IClass elementClass = aClass.getInnermostElementClass();
        if (elementClass != null) {
          registerArrayInstanceWithAllSuperclassesOfElement(index, elementClass, dim);
          registerArrayInstanceWithAllInterfacesOfElement(index, elementClass, dim);
        }
      }
    } catch (ClassHierarchyException e) {
      Warnings.add(ClassHierarchyWarning.create(e.getMessage()));
    }
  }

  private int registerMultiDimArraysForArrayOfObjectTypes(int dim, int index, ArrayClass aClass) {

    for (int i = 1; i < dim; i++) {
      TypeReference jlo = makeArray(TypeReference.JavaLangObject, i);
      IClass jloClass = null;
      jloClass = aClass.getClassLoader().lookupClass(jlo.getName());
      MutableIntSet set = findOrCreateSparseSetForClass(jloClass);
      set.add(index);
    }
    return dim;
  }

  private void registerArrayInstanceWithAllInterfacesOfElement(int index, IClass elementClass, int dim) {
    Collection ifaces = null;
    ifaces = elementClass.getAllImplementedInterfaces();
    for (Iterator it = ifaces.iterator(); it.hasNext();) {
      IClass I = (IClass) it.next();
      TypeReference iArrayRef = makeArray(I.getReference(), dim);
      IClass iArrayClass = null;
      iArrayClass = I.getClassLoader().lookupClass(iArrayRef.getName());
      MutableIntSet set = findOrCreateSparseSetForClass(iArrayClass);
      set.add(index);
      if (DEBUG) {
        System.err.println("dense filter for interface " + iArrayClass + " " + set);
      }
    }
  }

  private TypeReference makeArray(TypeReference element, int dim) {
    TypeReference iArrayRef = element;
    for (int i = 0; i < dim; i++) {
      iArrayRef = TypeReference.findOrCreateArrayOf(iArrayRef);
    }
    return iArrayRef;
  }

  private void registerArrayInstanceWithAllSuperclassesOfElement(int index, IClass elementClass, int dim)
      throws ClassHierarchyException {
    IClass T;
    // register the array with each supertype of the element class
    T = elementClass.getSuperclass();
    while (T != null) {
      TypeReference tArrayRef = makeArray(T.getReference(), dim);
      IClass tArrayClass = null;
      tArrayClass = T.getClassLoader().lookupClass(tArrayRef.getName());
      MutableIntSet set = findOrCreateSparseSetForClass(tArrayClass);
      set.add(index);
      if (DEBUG) {
        System.err.println("dense filter for class " + tArrayClass + " " + set);
      }
      T = T.getSuperclass();
    }
  }

  /**
   * @param klass
   * @param index
   * @throws ClassHierarchyException
   */
  private void registerInstanceWithAllInterfaces(IClass klass, int index) throws ClassHierarchyException {
    Collection ifaces = klass.getAllImplementedInterfaces();
    for (Iterator it = ifaces.iterator(); it.hasNext();) {
      IClass I = (IClass) it.next();
      MutableIntSet set = findOrCreateSparseSetForClass(I);
      set.add(index);
      if (DEBUG) {
        System.err.println("dense filter for interface " + I + " " + set);
      }
    }
  }

  /**
   * @param index
   * @param T
   * @throws ClassHierarchyException
   */
  private void registerInstanceWithAllSuperclasses(int index, IClass T) throws ClassHierarchyException {
    while (T != null && !T.getReference().equals(TypeReference.JavaLangObject)) {
      MutableIntSet set = findOrCreateSparseSetForClass(T);
      set.add(index);
      if (DEBUG) {
        System.err.println("dense filter for class " + T + " " + set);
      }
      T = T.getSuperclass();
    }
  }

  public void newSideEffect(UnaryOperator<PointsToSetVariable> op, PointerKey arg0) {
    if (arg0 == null) {
      throw new IllegalArgumentException("null arg0");
    }
    if (DEBUG) {
      System.err.println("add constraint D: " + op + " " + arg0);
    }
    assert !pointsToMap.isUnified(arg0);
    PointsToSetVariable v1 = findOrCreatePointsToSet(arg0);
    newStatement(null, op, v1, true, true);
  }

  public void newSideEffect(AbstractOperator<PointsToSetVariable> op, PointerKey[] arg0) {
    if (arg0 == null) {
      throw new IllegalArgumentException("null arg0");
    }
    if (DEBUG) {
      System.err.println("add constraint D: " + op + " " + arg0);
    }
    PointsToSetVariable[] vs = new PointsToSetVariable[ arg0.length ];
    for(int i = 0; i < arg0.length; i++) {
      assert !pointsToMap.isUnified(arg0[i]);
      vs[i] = findOrCreatePointsToSet(arg0[i]);
    }
    newStatement(null, op, vs, true, true);
  }

  public void newSideEffect(AbstractOperator<PointsToSetVariable> op, PointerKey arg0, PointerKey arg1) {
    if (DEBUG) {
      System.err.println("add constraint D: " + op + " " + arg0);
    }
    assert !pointsToMap.isUnified(arg0);
    assert !pointsToMap.isUnified(arg1);
    PointsToSetVariable v1 = findOrCreatePointsToSet(arg0);
    PointsToSetVariable v2 = findOrCreatePointsToSet(arg1);
    newStatement(null, op, v1, v2, true, true);
  }

  @Override
  protected void initializeWorkList() {
    addAllStatementsToWorkList();
  }

  /**
   * @return an object that encapsulates the pointer analysis results
   */
  public PointerAnalysis<InstanceKey> extractPointerAnalysis(PropagationCallGraphBuilder builder) {
    if (pointerAnalysis == null) {
      pointerAnalysis = makePointerAnalysis(builder);
    }
    return pointerAnalysis;
  }

  @Override
  public void performVerboseAction() {
    super.performVerboseAction();
    if (DEBUG_MEMORY) {
      DEBUG_MEM_COUNTER++;
      if (DEBUG_MEM_COUNTER % DEBUG_MEM_INTERVAL == 0) {
        DEBUG_MEM_COUNTER = 0;
        ReferenceCleanser.clearSoftCaches();

        System.err.println(flowGraph.spaceReport());

        System.err.println("Analyze leaks..");
        HeapTracer.traceHeap(Collections.singleton(this), true);
        System.err.println("done analyzing leaks");
      }
    }
    if (getFixedPointSystem() instanceof VerboseAction) {
      ((VerboseAction) getFixedPointSystem()).performVerboseAction();
    }
    AbstractStatement s = workList.takeStatement();
    System.err.println(printRHSInstances(s));
    workList.insertStatement(s);
    System.err.println("CGNodes: " + cg.getNumberOfNodes());

  }

  private String printRHSInstances(AbstractStatement s) {
    if (s instanceof UnaryStatement) {
      UnaryStatement u = (UnaryStatement) s;
      PointsToSetVariable rhs = (PointsToSetVariable) u.getRightHandSide();
      IntSet value = rhs.getValue();
      final int[] topFive = new int[5];
      value.foreach(new IntSetAction() {
        @Override
        public void act(int x) {
          for (int i = 0; i < 4; i++) {
            topFive[i] = topFive[i + 1];
          }
          topFive[4] = x;
        }
      });
      StringBuffer result = new StringBuffer();
      for (int i = 0; i < 5; i++) {
        int p = topFive[i];
        if (p != 0) {
          InstanceKey ik = getInstanceKey(p);
          result.append(p).append("  ").append(ik).append("\n");
        }
      }
      return result.toString();
    } else {
      return s.getClass().toString();
    }
  }

  @Override
  public IFixedPointSystem<PointsToSetVariable> getFixedPointSystem() {
    return flowGraph;
  }
  // sz
  @Override
  public boolean isImplicitQ(AbstractStatement s){
    IVariable lhs = s.getLHS();
    if(lhs instanceof PointsToSetVariable){
      PointerKey pk = ((PointsToSetVariable) lhs).getPointerKey();
      return pointsToMap.isImplicit(pk);
    }
    return false;

  }
  // sz
  @Override
  public boolean isTransitiveRoot(AbstractStatement s){
    IVariable lhs = s.getLHS();
    if(lhs instanceof PointsToSetVariable){
      PointerKey pk = ((PointsToSetVariable) lhs).getPointerKey();
      return pointsToMap.isTransitiveRoot(pk);
    }
    return false;


  }
  /*
   * @see com.ibm.wala.ipa.callgraph.propagation.HeapModel#iteratePointerKeys()
   */
  public Iterator<PointerKey> iteratePointerKeys() {
    return pointsToMap.iterateKeys();
  }

  /**
   * warning: this is _real_ slow; don't use it anywhere performance critical
   */
  public int getNumberOfPointerKeys() {
    return pointsToMap.getNumberOfPointerKeys();
  }

  /**
   * Use with care.
   */
  Worklist getWorklist() {
    return workList;
  }

  public Iterator<AbstractStatement> getStatementsThatUse(PointsToSetVariable v) {
    return flowGraph.getStatementsThatUse(v);
  }

  public Iterator<AbstractStatement> getStatementsThatDef(PointsToSetVariable v) {
    return flowGraph.getStatementsThatDef(v);
  }

  public NumberedGraph<PointsToSetVariable> getAssignmentGraph() {
    return flowGraph.getAssignmentGraph();
  }

  public Graph<PointsToSetVariable> getFilterAsssignmentGraph() {
    return flowGraph.getFilterAssignmentGraph();
  }

  /**
   * NOTE: do not use this method unless you really know what you are doing. Functionality is fragile and may not work in the
   * future.
   */
  public Graph<PointsToSetVariable> getFlowGraphIncludingImplicitConstraints() {
    return flowGraph.getFlowGraphIncludingImplicitConstraints();
  }

  /**
   *
   */
  public void revertToPreTransitive() {
    pointsToMap.revertToPreTransitive();
  }

  public Iterator getTransitiveRoots() {
    return pointsToMap.getTransitiveRoots();
  }

  public boolean isTransitiveRoot(PointerKey key) {
    return pointsToMap.isTransitiveRoot(key);
  }

  @Override
  protected void periodicMaintenance() {
    super.periodicMaintenance();
    ReferenceCleanser.clearSoftCaches();
  }

  @Override
  public int getVerboseInterval() {
    return verboseInterval;
  }

  /**
   * @param verboseInterval The verboseInterval to set.
   */
  public void setVerboseInterval(int verboseInterval) {
    this.verboseInterval = verboseInterval;
  }

  @Override
  public int getPeriodicMaintainInterval() {
    return periodicMaintainInterval;
  }

  /**
   * @param periodicMaintainInteval
   */
  public void setPeriodicMaintainInterval(int periodicMaintainInteval) {
    this.periodicMaintainInterval = periodicMaintainInteval;
  }

  /**
   * Unify the points-to-sets for the variables identified by the set s
   *
   * @param s numbers of points-to-set variables
   * @throws IllegalArgumentException if s is null
   */
  public void unify(IntSet s) {
    if (s == null) {
      throw new IllegalArgumentException("s is null");
    }
    // cache the variables represented
    HashSet<PointsToSetVariable> cache = HashSetFactory.make(s.size());
    for (IntIterator it = s.intIterator(); it.hasNext();) {
      int i = it.next();
      cache.add(pointsToMap.getPointsToSet(i));
    }

    // unify the variables
    pointsToMap.unify(s);
    int rep = pointsToMap.getRepresentative(s.intIterator().next());

    // clean up the equations
    updateEquationsForUnification(cache, rep);

    // special logic to clean up side effects
    updateSideEffectsForUnification(cache, rep);
  }

  /**
   * Update side effect after unification
   *
   * @param s set of PointsToSetVariables that have been unified
   * @param rep number of the representative variable for the unified set.
   */
  private void updateSideEffectsForUnification(HashSet<PointsToSetVariable> s, int rep) {
    PointsToSetVariable pRef = pointsToMap.getPointsToSet(rep);
    for (Iterator<PointsToSetVariable> it = s.iterator(); it.hasNext();) {
      PointsToSetVariable p = it.next();
      updateSideEffects(p, pRef);
    }
  }

  /**
   * Update equation def/uses after unification
   *
   * @param s set of PointsToSetVariables that have been unified
   * @param rep number of the representative variable for the unified set.
   */
  @SuppressWarnings("unchecked")
  private void updateEquationsForUnification(HashSet<PointsToSetVariable> s, int rep) {
    PointsToSetVariable pRef = pointsToMap.getPointsToSet(rep);
    for (Iterator<PointsToSetVariable> it = s.iterator(); it.hasNext();) {
      PointsToSetVariable p = it.next();

      if (p != pRef) {
        // pRef is the representative for p.
        // be careful: cache the defs before mucking with the underlying system
        for (Iterator d = Iterator2Collection.toSet(getStatementsThatDef(p)).iterator(); d.hasNext();) {
          AbstractStatement as = (AbstractStatement) d.next();

          if (as instanceof AssignEquation) {
            AssignEquation assign = (AssignEquation) as;
            PointsToSetVariable rhs = assign.getRightHandSide();
            int rhsRep = pointsToMap.getRepresentative(pointsToMap.getIndex(rhs.getPointerKey()));
            if (rhsRep == rep) {
              flowGraph.removeStatement(as);
            } else {
              replaceLHS(pRef, p, as);
            }
          } else {
            replaceLHS(pRef, p, as);
          }
        }
        // be careful: cache the defs before mucking with the underlying system
        for (Iterator u = Iterator2Collection.toSet(getStatementsThatUse(p)).iterator(); u.hasNext();) {
          AbstractStatement as = (AbstractStatement) u.next();
          if (as instanceof AssignEquation) {
            AssignEquation assign = (AssignEquation) as;
            PointsToSetVariable lhs = assign.getLHS();
            int lhsRep = pointsToMap.getRepresentative(pointsToMap.getIndex(lhs.getPointerKey()));
            if (lhsRep == rep) {
              flowGraph.removeStatement(as);
            } else {
              replaceRHS(pRef, p, as);
            }
          } else {
            replaceRHS(pRef, p, as);
          }
        }
        if (flowGraph.getNumberOfStatementsThatDef(p) == 0 && flowGraph.getNumberOfStatementsThatUse(p) == 0) {
          flowGraph.removeVariable(p);
        }
      }
    }
  }

  /**
   * replace all occurrences of p on the rhs of a statement with pRef
   *
   * @param as a statement that uses p in it's right-hand side
   */
  private void replaceRHS(PointsToSetVariable pRef, PointsToSetVariable p,
      AbstractStatement<PointsToSetVariable, AbstractOperator<PointsToSetVariable>> as) {
    if (as instanceof UnaryStatement) {
      assert ((UnaryStatement) as).getRightHandSide() == p;
      newStatement(as.getLHS(), (UnaryOperator<PointsToSetVariable>) as.getOperator(), pRef, false, false);
    } else {
      IVariable[] rhs = as.getRHS();
      PointsToSetVariable[] newRHS = new PointsToSetVariable[rhs.length];
      for (int i = 0; i < rhs.length; i++) {
        if (rhs[i].equals(p)) {
          newRHS[i] = pRef;
        } else {
          newRHS[i] = (PointsToSetVariable) rhs[i];
        }
      }
      newStatement(as.getLHS(), as.getOperator(), newRHS, false, false);
    }
    flowGraph.removeStatement(as);
  }

  /**
   * replace all occurences of p on the lhs of a statement with pRef
   *
   * @param as a statement that defs p
   */
  private void replaceLHS(PointsToSetVariable pRef, PointsToSetVariable p,
      AbstractStatement<PointsToSetVariable, AbstractOperator<PointsToSetVariable>> as) {
    assert as.getLHS() == p;
    if (as instanceof UnaryStatement) {
      newStatement(pRef, (UnaryOperator<PointsToSetVariable>) as.getOperator(), (PointsToSetVariable) ((UnaryStatement) as)
          .getRightHandSide(), false, false);
    } else {
      newStatement(pRef, as.getOperator(), as.getRHS(), false, false);
    }
    flowGraph.removeStatement(as);
  }

  public boolean isUnified(PointerKey result) {
    return pointsToMap.isUnified(result);
  }

  public int getNumber(PointerKey p) {
    return pointsToMap.getIndex(p);
  }


  @Override
  protected PointsToSetVariable[] makeStmtRHS(int size) {
    return new PointsToSetVariable[size];
  }


  public void checkScenarioAmongEdges(ArrayList<PointerKey> lhss, ArrayList<PointerKey> rhss) {
//    flowGraph.findPaths(lhss, rhss);
  }

  private static ClassLoader ourClassLoader = ActorSystem.class.getClassLoader();
  //start akka system
  private void startAkkaSys(){
//    resultListener = akkaSys.actorOf(Props.create(ResultLisenter.class), "listener");
    Thread.currentThread().setContextClassLoader(ourClassLoader);
    akkaSys = ActorSystem.create("pta");
    hub = akkaSys.actorOf(Props.create(Hub.class, nrOfWorkers), "hub");
//    System.err.println("Akka sys initialized. ");
  }


}
