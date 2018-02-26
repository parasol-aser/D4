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
package com.ibm.wala.fixedpoint.impl;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

import com.ibm.wala.fixpoint.AbstractOperator;
import com.ibm.wala.fixpoint.AbstractStatement;
import com.ibm.wala.fixpoint.FixedPointConstants;
import com.ibm.wala.fixpoint.IFixedPointSolver;
import com.ibm.wala.fixpoint.IFixedPointStatement;
import com.ibm.wala.fixpoint.IVariable;
import com.ibm.wala.fixpoint.UnaryOperator;
import com.ibm.wala.fixpoint.UnaryStatement;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.MonitorUtil;
import com.ibm.wala.util.MonitorUtil.IProgressMonitor;
import com.ibm.wala.util.debug.VerboseAction;
import com.ibm.wala.util.intset.MutableIntSet;
/**
 * Represents a set of {@link IFixedPointStatement}s to be solved by a {@link IFixedPointSolver}
 *
 * <p>
 * Implementation Note:
 *
 * The set of steps and variables is internally represented as a graph. Each step and each variable is a node in the graph. If a
 * step produces a variable that is used by another step, the graph has a directed edge from the producer to the consumer.
 * Fixed-point iteration proceeds in a topological order according to these edges.
 */
@SuppressWarnings("rawtypes")
public abstract class AbstractFixedPointSolver<T extends IVariable<?>> implements IFixedPointSolver<T>, FixedPointConstants,
    VerboseAction {


  static final boolean DEBUG = false;

  static public final boolean verbose = "true".equals(System.getProperty("com.ibm.wala.fixedpoint.impl.verbose"));

  static public final int DEFAULT_VERBOSE_INTERVAL = 100000;

  static final boolean MORE_VERBOSE = true;

  static public final int DEFAULT_PERIODIC_MAINTENANCE_INTERVAL = 100000;

  // sz

  //jeff
  public boolean isOptimize = false;
  public void setOptimize(boolean opt)
  {
    this.isOptimize = opt;
  }
  /**
   * A tuning parameter; how may new IStatementDefinitionss must be added before doing a new topological sort? TODO: Tune this
   * empirically.
   */
  private int minSizeForTopSort = 0;

  /**
   * A tuning parameter; by what percentage must the number of equations grow before we perform a topological sort?
   */
  private double topologicalGrowthFactor = 0.1;

  /**
   * A tuning parameter: how many evaluations are allowed to take place between topological re-orderings. The idea is that many
   * evaluations may be a sign of a bad ordering, even when few new equations are being added.
   *
   * A number less than zero mean infinite.
   */
  private int maxEvalBetweenTopo = 500000;

  private int evaluationsAtLastOrdering = 0;

  /**
   * How many equations have been added since the last topological sort?
   */
  int topologicalCounter = 0;

  /**
   * The next order number to assign to a new equation
   */
  int nextOrderNumber = 1;

  /**
   * During verbose evaluation, holds the number of dataflow equations evaluated
   */
  private int nEvaluated = 0;

  /**
   * During verbose evaluation, holds the number of dataflow equations created
   */
  private int nCreated = 0;

  /**
   * worklist for the iterative solver
   */
  protected Worklist workList = new Worklist();
  protected Worklist workListIR = new Worklist();
  protected Worklist workListAkka = new Worklist();

  // sz

  /**
   * all effected nodes, which have been reseted and require reconstruction
   */
  //protected Set<T> resetNodes = HashSetFactory.make();
  public LinkedList<T> resetNodes = new LinkedList<T>();


  /**
   * A boolean which is initially true, but set to false after the first call to solve();
   */
  private boolean firstSolve = true;

  protected abstract T[] makeStmtRHS(int size);

  /**
   * Some setup which occurs only before the first solve
   */
  public void initForFirstSolve() {
    orderStatements();
    initializeVariables();
    initializeWorkList();
    firstSolve = false;
  }

  /**
   * @return true iff work list is empty
   */
  public boolean emptyWorkList() {
    return workList.isEmpty();
  }

  public static int countforTotalWL = 0;
  /**
   * Solve the set of dataflow graph.
   * <p>
   * PRECONDITION: graph is set up
   *
   * @return true iff the evaluation of some equation caused a change in the value of some variable.
   */
  @Override
  @SuppressWarnings("unchecked")
  public boolean solve(IProgressMonitor monitor) throws CancelException {

    boolean globalChange = false;

    if (firstSolve) {
      initForFirstSolve();
    }

    while (!workList.isEmpty()) {
      MonitorUtil.throwExceptionIfCanceled(monitor);
      orderStatements();

      // duplicate insertion detection
      AbstractStatement s = workList.takeStatement();
//        countforTotalWL++;
//      counterWL++;
      if (DEBUG) {
        System.err.println(("Before evaluation " + s));
      }
//      try{
//      if(s.toString().contains("Application"))
//        System.out.println();
//      }catch(Exception e){}
      byte code = s.evaluate();
      if (verbose) {
        nEvaluated++;
        if (nEvaluated % getVerboseInterval() == 0) {
          performVerboseAction();
        }
        if (nEvaluated % getPeriodicMaintainInterval() == 0) {
          periodicMaintenance();
        }

      }
      if (DEBUG) {
        System.err.println(("After evaluation  " + s + " " + isChanged(code)));
      }
      if (isChanged(code)) {
//        if(isChange&&!changes.contains(s.getLHS())){
//          changes.add(s.getLHS());
//        }
        globalChange = true;
        updateWorkList(s);
      }

   // sz: since I didn't see it's called anywhere
      //*** do NOT delete any infor inside data flow graph
//      if (isFixed(code)) {
//        removeStatement(s);
//      }


      if (isFixed(code)) {
        removeStatement(s);
      }
    }
//    System.out.println("worklist num: " + counterWL);
    return globalChange;
  }

  public boolean solveIR(IProgressMonitor monitor) throws CancelException {
    boolean globalChange = false;
    if (firstSolve) {
      initForFirstSolve();
    }

    while (!workListIR.isEmpty()) {
      MonitorUtil.throwExceptionIfCanceled(monitor);
      orderStatements();

      // duplicate insertion detection
      AbstractStatement s = workListIR.takeStatement();
      if (DEBUG) {
        System.err.println(("Before evaluation " + s));
      }
      byte code = s.evaluate();
      if (verbose) {
        nEvaluated++;
        if (nEvaluated % getVerboseInterval() == 0) {
          performVerboseAction();
        }
        if (nEvaluated % getPeriodicMaintainInterval() == 0) {
          periodicMaintenance();
        }

      }
      if (DEBUG) {
        System.err.println(("After evaluation  " + s + " " + isChanged(code)));
      }
      if (isChanged(code)) {
//        if(isChange&&!changes.contains(s.getLHS())){
//          changes.add(s.getLHS());
//        }
        globalChange = true;
        updateWorkListIR(s);
      }

      if (isFixed(code)) {
        removeStatement(s);
      }
    }
//    System.out.println("worklist num: " + counterWL);
    return globalChange;
  }

//  public boolean solveAkka(IProgressMonitor monitor) throws CancelException {
//
//  boolean globalChange = false;
//
////  System.out.println("Num of Statement in Worklist: " + workListAkka.size());
//  while (!workListAkka.isEmpty()) {
//    MonitorUtil.throwExceptionIfCanceled(monitor);
//    orderStatements();
//
//    // duplicate insertion detection
//    AbstractStatement s = workListAkka.takeStatement();
//    if (DEBUG) {
//      System.err.println(("Before evaluation " + s));
//    }
////    try{
////    if(s.toString().contains("Application"))
////      System.out.println();
////    }catch(Exception e){}
//
//    byte code = s.evaluate();
////    System.err.println(i++);
//    if (verbose) {
//      nEvaluated++;
//      if (nEvaluated % getVerboseInterval() == 0) {
//        performVerboseAction();
//      }
//      if (nEvaluated % getPeriodicMaintainInterval() == 0) {
//        periodicMaintenance();
//      }
//
//    }
//    if (DEBUG) {
//      System.err.println(("After evaluation  " + s + " " + isChanged(code)));
//    }
//    if (isChanged(code)) {
//      if(isChange&&!changes.contains(s.getLHS())){
//        changes.add(s.getLHS());
////        System.out.println("---------"+s.toString());
//      }
//      globalChange = true;
//      updateWorkListAkka(s);
//    }
//    if (isFixed(code)) {
//      removeStatement(s);
//    }
//  }
//  return globalChange;
//}


  @Override
  public void performVerboseAction() {
    System.err.println("Evaluated " + nEvaluated);
    System.err.println("Created   " + nCreated);
    System.err.println("Worklist  " + workList.size());
    if (MORE_VERBOSE) {
      if (!workList.isEmpty()) {
        AbstractStatement s = workList.takeStatement();
        System.err.println("Peek      " + lineBreak(s.toString(), 132));
        if (s instanceof VerboseAction) {
          ((VerboseAction) s).performVerboseAction();
        }
        workList.insertStatement(s);
      }
    }
  }
//  public static int incresize = 0;
  // sz: find all effected edges and nodes
  public boolean solveDel(IProgressMonitor monitor) throws CancelException {
    boolean globalChange = false;
    int counterEffectNodes = 0;
    int counterWL = 0;
//    int counterChanged = 0;
//    System.out.println("wl start:"+ System.currentTimeMillis());
//    System.out.println("num of reset: "+resetNodes.size());
//    System.out.println("wl num at start: "+workList.size());
    while (!workList.isEmpty()) {
      MonitorUtil.throwExceptionIfCanceled(monitor);
      orderStatements();
      AbstractStatement s = workList.takeStatement();
//      System.out.println(s.toString());
//      if(s.getLHS()!=null){
//        if(s.getLHS().toString().contains("[Node: < Primordial, Ljava/lang/String, length()I > Context: Everywhere, v1]"))
//          System.out.println(s.toString());
//      }

      if(DEBUG){
        System.out.println("--- analyze statement: "+s.toString());
      }
      //*** avoid dead loop
      if(workList.contains(s))
        continue;
      T v = (T) s.getLHS();
      if(v == null) continue;
      for (Iterator it = getFixedPointSystem().getStatementsThatUse(v); it.hasNext();) {
        AbstractStatement effectS = (AbstractStatement) it.next();
        if(DEBUG){
          System.out.println("--- effected statement: "+effectS.toString());
        }
        T effectT = (T) effectS.getLHS();
//        System.out.println(effectS.toString());

        //*** avoid dead loop *2
        if(effectT != null && !resetNodes.contains(effectT)){
          resetNodes.add(effectT);
//          System.out.println("add to resetnode: "+effectS.toString());
          addToWorkList(effectS);
//          counterEffectNodes++;
        }
      }
    counterEffectNodes++;

      //*** since evaluateDel would reset lhs, conservatively, always assume change happens
      //*** therefore, adding its successor statements and nodes in worklist and resetnodes first is safe


//      if(isImplicitQ(s)){
//        continue;
//      }
      if(isTransitiveRoot(s)){
        continue;
      }
//      byte code = s.evaluateDel();
//      counterWL++;
////      incresize++;
//      //*** TODO: isFixed?
//      if (isChanged(code)) {
//        if(!changes.contains(s.getLHS()))
//          changes.add(s.getLHS());
////        System.out.println("changed: "+s.toString());
//        globalChange = true;
////        counterChanged++;
//      }
    }
//    System.out.println("num of reset: "+resetNodes.size());
//    System.err.println("num of WL: " + counterWL + ", num of effect: " + counterEffectNodes);//+", num of changed: " +counterChanged
    return globalChange;

  }

  public boolean updatechange = false;
  public void setUpdateChange(boolean p){
    updatechange = p;
  }

  public boolean isChange = false;
  public void setChange(boolean p){
    isChange = p;
  }

  //for test
  public static HashMap<IVariable, MutableIntSet> var_pts_map = new HashMap<IVariable, MutableIntSet>();

  public static HashSet<IVariable> changes = new HashSet<IVariable>();
  public void clearChanges(){
    changes.clear();
  }

  public static void addToChanges(IVariable tar){
    synchronized(changes){
      if(!changes.contains(tar))
        changes.add(tar);
    }
  }

  public static HashSet<IVariable> theRoot = new HashSet<IVariable>();
  public void setTheRoot(IVariable root){
    this.theRoot.add(root);
  }

  public void clearTheRoot(){
    this.theRoot.clear();
  }

  // sz: based on data flow information, reconstruct the effected part of call graph
//  public boolean reConstruct(IProgressMonitor monitor) throws CancelException {
//    boolean globalChange = false;
////    int counterReConstruct = 0;
//      //for(Iterator itt = resetNodes.iterator(); itt.hasNext();){
//        //T cur = (T) itt.next();
////    System.out.println("recon start:"+ System.currentTiceMillis());
//        while(!resetNodes.isEmpty()){
//          T peek = resetNodes.peek();
//          if(peek == null)
//            break;
//          T cur = resetNodes.removeFirst();
////          if(cur.toString().contains("[Node: < Primordial, Ljava/lang/StringBuilder, append(Ljava/lang/String;)Ljava/lang/StringBuilder; > Context: Everywhere, v2]"))
////            System.out.println();
//
//          if(DEBUG){
//            System.out.println("--- reconstruct PTSV: "+cur.toString());
//          }
////          System.out.println("--- reconstruct PTSV: "+cur.toString());
//
//          for (Iterator it = getFixedPointSystem().getStatementsThatDef(cur); it.hasNext();) {
//            AbstractStatement s = (AbstractStatement) it.next();
//            if(DEBUG){
//              System.out.println("--- Predecessor statements: "+s.toString());
//            }
//
//            byte code = s.evaluate();
//
////            incresize++;
////            counterReConstruct++;
//
//            if (isChanged(code)) {
//              if(!changes.contains(s.getLHS()))
//                changes.add(s.getLHS());
////              System.out.println("changed in reconstruct: "+s.toString());
//              globalChange = true;
//            }
//          }
//       // if(localChange){
//          //*** assume cur has changed in any case
////          changedVariable(cur);
//        //}
//          for (Iterator it = getFixedPointSystem().getStatementsThatUse(cur); it.hasNext();) {
//            AbstractStatement s = (AbstractStatement) it.next();
//            addToWorkList(s);
////            counterNextEffect++;
//          }
//
//    }
////    System.out.println("reconstruct num: " + counterReConstruct);
////    System.out.println("reconstruct end"+ System.currentTimeMillis());
//    return globalChange;
//  }


//  public boolean reConstruct2(IProgressMonitor monitor) throws CancelException {
//    boolean globalChange = false;
////    int counterReConstruct = 0;
//      //for(Iterator itt = resetNodes.iterator(); itt.hasNext();){
//        //T cur = (T) itt.next();
////    System.out.println("recon start:"+ System.currentTiceMillis());
////    System.out.println("num of reset: "+resetNodes.size());
//
//        while(!resetNodes.isEmpty()){
//          T peek = resetNodes.peek();
//          if(peek == null)
//            break;
//          T cur = resetNodes.removeFirst();
////          if(cur.toString().contains("[Node: < Primordial, Ljava/lang/StringBuilder, append(Ljava/lang/String;)Ljava/lang/StringBuilder; > Context: Everywhere, v2]"))
////            System.out.println();
//
//          if(DEBUG){
//            System.out.println("--- reconstruct PTSV: "+cur.toString());
//          }
//
//          for (Iterator it = getFixedPointSystem().getStatementsThatDef(cur); it.hasNext();) {
//            AbstractStatement s = (AbstractStatement) it.next();
//            if(DEBUG){
//              System.out.println("--- Predecessor statements: "+s.toString());
//            }
//
////            incresize++;
////            counterReConstruct++;
//            byte code = s.evaluate();
//
////          incresize++;
////          counterReConstruct++;
//
//            if (isChanged(code)) {
//              if(!changes.contains(s.getLHS()))
//                changes.add(s.getLHS());
////            System.out.println("changed in reconstruct: "+s.toString());
//              globalChange = true;
//            }
//          }
//
////          for (Iterator it = getFixedPointSystem().getStatementsThatUse(cur); it.hasNext();) {
////            AbstractStatement s = (AbstractStatement) it.next();
////            addToWorkList(s);
//////            counterNextEffect++;
////          }
//
//    }
////    System.out.println("reconstruct num: " + counterReConstruct);
////    System.out.println("reconstruct end"+ System.currentTimeMillis());
//    return globalChange;
//  }



  public static String lineBreak(String string, int wrap) {
    if (string == null) {
      throw new IllegalArgumentException("string is null");
    }
    if (string.length() > wrap) {
      StringBuffer result = new StringBuffer();
      int start = 0;
      while (start < string.length()) {
        int end = Math.min(start + wrap, string.length());
        result.append(string.substring(start, end));
        result.append("\n  ");
        start = end;
      }
      return result.toString();
    } else {
      return string;
    }
  }

  public void removeStatement(AbstractStatement<T, ?> s) {
    getFixedPointSystem().removeStatement(s);
  }

  @Override
  public String toString() {
    StringBuffer result = new StringBuffer("Fixed Point System:\n");
    for (Iterator it = getStatements(); it.hasNext();) {
      result.append(it.next()).append("\n");
    }
    return result.toString();
  }

  public Iterator getStatements() {
    return getFixedPointSystem().getStatements();
  }

  /**
   * Add a step to the work list.
   *
   * @param s the step to add
   */
  public void addToWorkList(AbstractStatement s) {
    workList.insertStatement(s);
//    try{
//      if(s.toString().contains("Ltest/"))
//        System.err.println(s);
//    }catch(Exception e){}
  }

  public void addToWorkListN(AbstractStatement s) {
    synchronized (workList) {
      workList.insertStatement(s);
    }
  }


  /**
   * Add all to the work list.
   */
  public void addAllStatementsToWorkList() {
    for (Iterator i = getStatements(); i.hasNext();) {
      AbstractStatement eq = (AbstractStatement) i.next();
      addToWorkList(eq);
    }
  }

  /**
   * Call this method when the contents of a variable changes. This routine adds all graph using this variable to the set of new
   * graph.
   *
   * @param v the variable that has changed
   */
  public void changedVariable(T v) {
    for (Iterator it = getFixedPointSystem().getStatementsThatUse(v); it.hasNext();) {
      AbstractStatement s = (AbstractStatement) it.next();
      if(isChange)
        addToWorklistAkka(s);
      else
        addToWorkList(s);
    }
  }

  public void changedVariableIR(T v) {
    for (Iterator it = getFixedPointSystem().getStatementsThatUse(v); it.hasNext();) {
      AbstractStatement s = (AbstractStatement) it.next();
      addToWorkListIR(s);
    }
  }

//  public void changedVariableSe(T v) {
//    for (Iterator it = getFixedPointSystem().getStatementsThatUse(v); it.hasNext();) {
//      AbstractStatement s = (AbstractStatement) it.next();
//      addToWorkList(s);
////      parallelEvaluate(s);
//
//    }
//  }

//  protected void parallelEvaluate(AbstractStatement s) {
//    byte code = s.evaluate();
//    if (verbose) {
//      nEvaluated++;
//      if (nEvaluated % getVerboseInterval() == 0) {
//        performVerboseAction();
//      }
//      if (nEvaluated % getPeriodicMaintainInterval() == 0) {
//        periodicMaintenance();
//      }
//
//    }
//    if (DEBUG) {
//      System.err.println(("After evaluation  " + s + " " + isChanged(code)));
//    }
//    if (isChanged(code)) {
//      if(isChange&&!changes.contains(s.getLHS())){
//        changes.add(s.getLHS());
////        System.out.println("---------"+s.toString());
//      }
//      updateWorkListSe(s);
//    }
//
//  }

  public void addToWorkListIR(AbstractStatement s) {
    workListIR.insertStatement(s);
  }

//  public void changedVariableDel(T v) {
//    for (Iterator it = getFixedPointSystem().getStatementsThatUse(v); it.hasNext();) {
//      AbstractStatement s = (AbstractStatement) it.next();
//
//      T iv = (T) s.getLHS();
//
//      addToResetNodes(iv);
//      //if(iv instanceof PointsToSetVariable)
//
//      addToWorkList(s);
//    }
//  }
  // sz
  public void addToResetNodes(T v){
//    if(v != null&&!resetNodes.contains(v))
//      resetNodes.add(v);
    synchronized (resetNodes) {
      if(v != null&&!resetNodes.contains(v))
        resetNodes.add(v);
    }
  }

  public void addAllToResetNodes(Collection<T> list){
    synchronized (resetNodes) {
      for (T t : list) {
        if(t !=null && !resetNodes.contains(t)){
          resetNodes.add(t);
        }
      }
    }
  }
  /**
   * Add a step with zero operands on the right-hand side.
   *
   * TODO: this is a little odd, in that this equation will never fire unless explicitly added to a work list. I think in most cases
   * we shouldn't be creating this nullary form.
   *
   * @param lhs the variable set by this equation
   * @param operator the step operator
   * @throws IllegalArgumentException if lhs is null
   */
  public boolean newStatement(final T lhs, final NullaryOperator<T> operator, final boolean toWorkList, final boolean eager) {
    if (lhs == null) {
      throw new IllegalArgumentException("lhs is null");
    }
    // add to the list of graph
    lhs.setOrderNumber(nextOrderNumber++);
    final NullaryStatement<T> s = new BasicNullaryStatement<T>(lhs, operator);
    if (getFixedPointSystem().containsStatement(s)) {
      return false;
    }
    nCreated++;
    getFixedPointSystem().addStatement(s);
    incorporateNewStatement(toWorkList, eager, s);
    topologicalCounter++;
    return true;
  }

  @SuppressWarnings("unchecked")
  private void incorporateNewStatement(boolean toWorkList, boolean eager, AbstractStatement s) {
    if (eager) {
      byte code = s.evaluate();
      if (verbose) {
        nEvaluated++;
        if (nEvaluated % getVerboseInterval() == 0) {
          performVerboseAction();
        }
        if (nEvaluated % getPeriodicMaintainInterval() == 0) {
          periodicMaintenance();
        }
      }
      if (isChanged(code)) {
        if(!isChange){
          updateWorkList(s);
        }else if(isChange){
          if(!changes.contains(s.getLHS()))
            changes.add(s.getLHS());
          updateWorkListAkka(s);
        }
      }

    } else if (toWorkList) {
      addToWorkList(s);
    }

  }

  private void incorporateNewStatementIR(boolean toWorkList, boolean eager, AbstractStatement s) {
    if (eager) {
      byte code = s.evaluate();
      if (verbose) {
        nEvaluated++;
        if (nEvaluated % getVerboseInterval() == 0) {
          performVerboseAction();
        }
        if (nEvaluated % getPeriodicMaintainInterval() == 0) {
          periodicMaintenance();
        }
      }
      if (isChanged(code)) {
//        if(isChange&&!changes.contains(s.getLHS()))
//          changes.add(s.getLHS());
        updateWorkListIR(s);
      }
    } else if (toWorkList) {
      addToWorkListIR(s);
    }

  }


//  private void incorporateAddStatement(boolean toWorkList, boolean eager, AbstractStatement delS) {
//    if (eager) {
//      //*** evaluateDel: evaluate this statement, find the final assign statement, clear lhs
//      byte code = delS.evaluate();
//      if(isChanged(code)&&!changes.contains(delS.getLHS()))
//        changes.add(delS.getLHS());
//    }
//  }


//sz: handle the deleted edge
 private void incorporateDelStatement(boolean toWorkList, boolean eager, AbstractStatement delS) {
   if (eager) {
     //*** assume this would always change pointsToMap
     //*** evaluateDel: evaluate this statement, find the final assign statement, clear lhs
     byte code = delS.evaluateDel();
     if(isChanged(code)){
       if(!changes.contains(delS.getLHS()))
         changes.add(delS.getLHS());
       updateWorkListAkka(delS);
     }
   }
 }


  /**
   * Add a step with one operand on the right-hand side.
   *
   * @param lhs the lattice variable set by this equation
   * @param operator the step's operator
   * @param rhs first operand on the rhs
   * @return true iff the system changes
   * @throws IllegalArgumentException if operator is null
   */
  public boolean newStatement(T lhs, UnaryOperator<T> operator, T rhs, boolean toWorkList, boolean eager) {
    if (operator == null) {
      throw new IllegalArgumentException("operator is null");
    }
    // add to the list of graph
    UnaryStatement<T> s = operator.makeEquation(lhs, rhs);
    try{//JEFF: $PutFieldOperator.hashCode NPE
      if (getFixedPointSystem().containsStatement(s)) {
        return false;
      }
    }catch(Exception e){}//FIXME
    if (lhs != null) {
      if(isChange){
        if(lhs.getOrderNumber() == -1)
          lhs.setOrderNumber(nextOrderNumber++);
      }else
        lhs.setOrderNumber(nextOrderNumber++);
    }
    nCreated++;
    getFixedPointSystem().addStatement(s);

    try{//JEFF
      incorporateNewStatement(toWorkList, eager, s);
    }catch(Exception e){}//FIXME
    topologicalCounter++;
    return true;//~~~ conversatively, always assume it's true
  }

  public boolean addStatement(T lhs, UnaryOperator<T> operator, T rhs, boolean toWorkList, boolean eager) {
    if (operator == null) {
      throw new IllegalArgumentException("operator is null");
    }
    // add to the list of graph
    UnaryStatement<T> s = operator.makeEquation(lhs, rhs);
    try{//JEFF: $PutFieldOperator.hashCode NPE
      if (getFixedPointSystem().containsStatement(s)) {
        return false;
      }
    }catch(Exception e){}//FIXME
    if (lhs != null) {
      if(isChange){
        if(lhs.getOrderNumber() == -1)
          lhs.setOrderNumber(nextOrderNumber++);
      }else
        lhs.setOrderNumber(nextOrderNumber++);
    }
    nCreated++;
    getFixedPointSystem().addStatement(s);

    topologicalCounter++;
    return true;//~~~ conversatively, always assume it's true
  }

  public boolean newStatementIR(T lhs, UnaryOperator<T> operator, T rhs, boolean toWorkList, boolean eager) {
    if (operator == null) {
      throw new IllegalArgumentException("operator is null");
    }
    // add to the list of graph
    UnaryStatement<T> s = operator.makeEquation(lhs, rhs);
    try{//JEFF: $PutFieldOperator.hashCode NPE
      if (getFixedPointSystem().containsStatement(s)) {
        return false;
      }
    }catch(Exception e){}//FIXME
    if (lhs != null) {
      lhs.setOrderNumber(nextOrderNumber++);
    }
    nCreated++;
    getFixedPointSystem().addStatement(s);

    try{//JEFF
    incorporateNewStatementIR(toWorkList, eager, s);
    }catch(Exception e){}//FIXME
    topologicalCounter++;
    return true;//~~~ conversatively, always assume it's true
  }


  /**
   * Delete a step with one operand on the right-hand side.
   *
   * @param lhs the lattice variable set by this equation
   * @param operator the step's operator
   * @param rhs first operand on the rhs
   * @return true iff the system changes
   * @throws IllegalArgumentException if operator is null
   */
  //sz: handle the deleted statement
  public boolean delStatement(T lhs, UnaryOperator<T> operator, T rhs, boolean toWorkList, boolean eager) {
    if (operator == null) {
      throw new IllegalArgumentException("operator is null");
    }
    // add to the list of graph
    UnaryStatement<T> s = operator.makeEquation(lhs, rhs);
    if (!getFixedPointSystem().containsStatement(s)) {
      return false;
    }

    if(isFirstDelete){
      getFixedPointSystem().delStatement(s);
    }

    incorporateDelStatement(toWorkList, eager, s);
    //topologicalCounter++;
    return true; //~~~ conversatively, always assume it's true
  }



  protected class Statement extends GeneralStatement<T> {

    public Statement(T lhs, AbstractOperator<T> operator, T op1, T op2, T op3) {
      super(lhs, operator, op1, op2, op3);
    }

    public Statement(T lhs, AbstractOperator<T> operator, T op1, T op2) {
      super(lhs, operator, op1, op2);
    }

    public Statement(T lhs, AbstractOperator<T> operator, T[] rhs) {
      super(lhs, operator, rhs);
    }

    public Statement(T lhs, AbstractOperator<T> operator) {
      super(lhs, operator);
    }

    @Override
    protected T[] makeRHS(int size) {
      return makeStmtRHS(size);
    }

  }

  /**
   * Add an equation with two operands on the right-hand side.
   *
   * @param lhs the lattice variable set by this equation
   * @param operator the equation operator
   * @param op1 first operand on the rhs
   * @param op2 second operand on the rhs
   */
  public boolean newStatement(T lhs, AbstractOperator<T> operator, T op1, T op2, boolean toWorkList, boolean eager) {
    // add to the list of graph

    GeneralStatement<T> s = new Statement(lhs, operator, op1, op2);
    if (getFixedPointSystem().containsStatement(s)) {
      return false;
    }
    if (lhs != null) {
      if(isChange){
        if(lhs.getOrderNumber() == -1)
          lhs.setOrderNumber(nextOrderNumber++);
      }else
        lhs.setOrderNumber(nextOrderNumber++);
    }
    nCreated++;
    getFixedPointSystem().addStatement(s);
    incorporateNewStatement(toWorkList, eager, s);
    topologicalCounter++;
    return true;
  }
  /**
   * Delete an equation with two operands on the right-hand side.
   *
   * @param lhs the lattice variable set by this equation
   * @param operator the equation operator
   * @param op1 first operand on the rhs
   * @param op2 second operand on the rhs
   */
  // sz
  public boolean delStatement(T lhs, AbstractOperator<T> operator, T op1, T op2, boolean toWorkList, boolean eager) {
    // add to the list of graph
    GeneralStatement<T> s = new Statement(lhs, operator, op1, op2);
    if (!getFixedPointSystem().containsStatement(s)) {
      return false;
    }
//    if (lhs != null) {
//      lhs.setOrderNumber(nextOrderNumber++);
//    }
    //nCreated++;
    if(isFirstDelete)
      getFixedPointSystem().delStatement(s);
    incorporateDelStatement(toWorkList, eager, s);
    //topologicalCounter++;
    return true;
  }
  /**
   * Add a step with three operands on the right-hand side.
   *
   * @param lhs the lattice variable set by this equation
   * @param operator the equation operator
   * @param op1 first operand on the rhs
   * @param op2 second operand on the rhs
   * @param op3 third operand on the rhs
   * @throws IllegalArgumentException if lhs is null
   */
  public boolean newStatement(T lhs, AbstractOperator<T> operator, T op1, T op2, T op3, boolean toWorkList, boolean eager) {
    if (lhs == null) {
      throw new IllegalArgumentException("lhs is null");
    }
    // add to the list of graph
    lhs.setOrderNumber(nextOrderNumber++);
    GeneralStatement<T> s = new Statement(lhs, operator, op1, op2, op3);
    if (getFixedPointSystem().containsStatement(s)) {
      nextOrderNumber--;
      return false;
    }
    nCreated++;
    getFixedPointSystem().addStatement(s);

    incorporateNewStatement(toWorkList, eager, s);
    topologicalCounter++;
    return true;
  }
  /**
   * Delete a step with three operands on the right-hand side.
   *
   * @param lhs the lattice variable set by this equation
   * @param operator the equation operator
   * @param op1 first operand on the rhs
   * @param op2 second operand on the rhs
   * @param op3 third operand on the rhs
   * @throws IllegalArgumentException if lhs is null
   */
  // sz
  public boolean delStatement(T lhs, AbstractOperator<T> operator, T op1, T op2, T op3, boolean toWorkList, boolean eager) {
//    if (lhs == null) {
//      throw new IllegalArgumentException("lhs is null");
//    }
    // add to the list of graph
    //lhs.setOrderNumber(nextOrderNumber++);
    GeneralStatement<T> s = new Statement(lhs, operator, op1, op2, op3);
    if (!getFixedPointSystem().containsStatement(s)) {
      //nextOrderNumber--;
      return false;
    }
    //nCreated++;
   if(isFirstDelete)
     getFixedPointSystem().delStatement(s);
    incorporateDelStatement(toWorkList, eager, s);
    //topologicalCounter++;
    return true;
  }
  /**
   * Add a step to the system with an arbitrary number of operands on the right-hand side.
   *
   * @param lhs lattice variable set by this equation
   * @param operator the operator
   * @param rhs the operands on the rhs
   */
  public boolean newStatement(T lhs, AbstractOperator<T> operator, T[] rhs, boolean toWorkList, boolean eager) {
    // add to the list of graph
    if (lhs != null)
      lhs.setOrderNumber(nextOrderNumber++);
    GeneralStatement<T> s = new Statement(lhs, operator, rhs);
    if(!updatechange){//converthandler
      if (getFixedPointSystem().containsStatement(s)) {
        nextOrderNumber--;
        return false;
      }
    }
    nCreated++;
    getFixedPointSystem().addStatement(s);
    incorporateNewStatement(toWorkList, eager, s);
    topologicalCounter++;
    return true;
  }

  public boolean newStatementIR(T lhs, AbstractOperator<T> operator, T[] rhs, boolean toWorkList, boolean eager) {
    // add to the list of graph
    if (lhs != null)
      lhs.setOrderNumber(nextOrderNumber++);
    GeneralStatement<T> s = new Statement(lhs, operator, rhs);
    if (getFixedPointSystem().containsStatement(s)) {
      nextOrderNumber--;
      return false;
    }
    nCreated++;
    getFixedPointSystem().addStatement(s);
    incorporateNewStatementIR(toWorkList, eager, s);
    topologicalCounter++;
    return true;
  }
  /**
   * Delete a step to the system with an arbitrary number of operands on the right-hand side.
   *
   * @param lhs lattice variable set by this equation
   * @param operator the operator
   * @param rhs the operands on the rhs
   */
  // sz
  public boolean delStatement(T lhs, AbstractOperator<T> operator, T[] rhs, boolean toWorkList, boolean eager) {
    //--- do nothing
//    if (lhs != null)
//      lhs.setOrderNumber(nextOrderNumber++);
    GeneralStatement<T> s = new Statement(lhs, operator, rhs);
    if (!getFixedPointSystem().containsStatement(s)) {
      //nextOrderNumber--;
      return false;
    }

    //nCreated++;
    if(isFirstDelete)
      getFixedPointSystem().delStatement(s);
    incorporateDelStatement(toWorkList, eager, s);
    //topologicalCounter++;
    return true;
  }
  /**
   * Initialize all lattice vars in the system.
   */
  abstract protected void initializeVariables();

  /**
   * Initialize the work list for iteration.j
   */
  abstract protected void initializeWorkList();

  /**
   * Update the worklist, assuming that a particular equation has been re-evaluated
   *
   * @param s the equation that has been re-evaluated.
   */
  private void updateWorkList(AbstractStatement<T, ?> s) {
    // find each equation which uses this lattice cell, and
    // add it to the work list
    T v = s.getLHS();
    if (v == null) {
      return;
    }
    changedVariable(v);
  }

  private void updateWorkListIR(AbstractStatement<T, ?> s) {
    // find each equation which uses this lattice cell, and
    // add it to the work list
    T v = s.getLHS();
    if (v == null) {
      return;
    }
    changedVariableIR(v);
  }

//  private void updateWorkListSe(AbstractStatement<T, ?> s) {
//    // find each equation which uses this lattice cell, and
//    // add it to the work list
//    T v = s.getLHS();
//    if (v == null) {
//      return;
//    }
//    changedVariableSe(v);
//  }

  private void updateWorkListAkka(AbstractStatement<T, ?> s) {
    // find each equation which uses this lattice cell, and
    // add it to the work list
    T v = s.getLHS();
    if (v == null) {
      return;
    }
    for (Iterator it = getFixedPointSystem().getStatementsThatUse(v); it.hasNext();) {
      AbstractStatement ss = (AbstractStatement) it.next();
      workListAkka.insertStatement(ss);
    }
  }

  /**
   * Number the graph in topological order.
   */
  private void orderStatementsInternal() {
    if (verbose) {
      if (nEvaluated > 0) {
        System.err.println("Reorder " + nEvaluated + " " + nCreated);
      }
    }
    reorder();
    if (verbose) {
      if (nEvaluated > 0) {
        System.err.println("Reorder finished " + nEvaluated + " " + nCreated);
      }
    }
    topologicalCounter = 0;
    evaluationsAtLastOrdering = nEvaluated;
  }

  /**
   *
   */
  public void orderStatements() {

    if (nextOrderNumber > minSizeForTopSort) {
      if (((double) topologicalCounter / (double) nextOrderNumber) > topologicalGrowthFactor) {
        orderStatementsInternal();
        return;
      }
    }

    if ((nEvaluated - evaluationsAtLastOrdering) > maxEvalBetweenTopo) {
      orderStatementsInternal();
      return;
    }
  }

  /**
   * Re-order the step definitions.
   */
  private void reorder() {
    // drain the worklist
    LinkedList<AbstractStatement> temp = new LinkedList<AbstractStatement>();
    while (!workList.isEmpty()) {
      AbstractStatement eq = workList.takeStatement();
      temp.add(eq);
    }
    workList = new Worklist();

    // compute new ordering
    getFixedPointSystem().reorder();

    // re-populate worklist
    for (Iterator<AbstractStatement> it = temp.iterator(); it.hasNext();) {
      AbstractStatement s = it.next();
      workList.insertStatement(s);
    }
  }

  public static boolean isChanged(byte code) {
    return (code & CHANGED_MASK) != 0;
  }

  public static boolean isSideEffect(byte code) {
    return (code & SIDE_EFFECT_MASK) != 0;
  }

  public static boolean isFixed(byte code) {
    return (code & FIXED_MASK) != 0;
  }

  public int getMinSizeForTopSort() {
    return minSizeForTopSort;
  }

  /**
   * @param i
   */
  public void setMinEquationsForTopSort(int i) {
    minSizeForTopSort = i;
  }

  public int getMaxEvalBetweenTopo() {
    return maxEvalBetweenTopo;
  }

  public double getTopologicalGrowthFactor() {
    return topologicalGrowthFactor;
  }

  /**
   * @param i
   */
  public void setMaxEvalBetweenTopo(int i) {
    maxEvalBetweenTopo = i;
  }

  /**
   * @param d
   */
  public void setTopologicalGrowthFactor(double d) {
    topologicalGrowthFactor = d;
  }

  public int getNumberOfEvaluations() {
    return nEvaluated;
  }

  public void incNumberOfEvaluations() {
    nEvaluated++;
  }

  /**
   * a method that will be called every N evaluations. subclasses should override as desired.
   */
  protected void periodicMaintenance() {
  }

  /**
   * subclasses should override as desired.
   */
  protected int getVerboseInterval() {
    return DEFAULT_VERBOSE_INTERVAL;
  }

  /**
   * subclasses should override as desired.
   */
  protected int getPeriodicMaintainInterval() {
    return DEFAULT_PERIODIC_MAINTENANCE_INTERVAL;
  }

  // sz: seems not used anymore
  public boolean isImplicitQ(AbstractStatement s) {
    // TODO Auto-generated method stub
    return false;
  }

  // sz
  public boolean isTransitiveRoot(AbstractStatement s) {
    // TODO Auto-generated method stub
    return false;
  }

  // sz
  private boolean isFirstDelete = true;

  public void setFirstDel(boolean isFirstDelete){
    //--- do  delete statements
    this.isFirstDelete = isFirstDelete;

  }
  public boolean getFirstDel(){
    return this.isFirstDelete;
  }


  // sz
  public void closeFirstSolve(){
    // --- do not initialize worklist based on current data flow infor
    this.firstSolve = false;
  }

  public boolean solveAkkaAdd(IProgressMonitor monitor) throws CancelException {

    boolean globalChange = false;

    while (!workListAkka.isEmpty()) {
      MonitorUtil.throwExceptionIfCanceled(monitor);
      orderStatements();

      // duplicate insertion detection
      AbstractStatement s = workListAkka.takeStatement();
      if (DEBUG) {
        System.err.println(("Before evaluation " + s));
      }
      byte code = s.evaluate();
      if (verbose) {
        nEvaluated++;
        if (nEvaluated % getVerboseInterval() == 0) {
          performVerboseAction();
        }
        if (nEvaluated % getPeriodicMaintainInterval() == 0) {
          periodicMaintenance();
        }

      }
      if (DEBUG) {
        System.err.println(("After evaluation  " + s + " " + isChanged(code)));
      }
      if (isChanged(code)) {
        if(isChange){
          if(!changes.contains(s.getLHS()))
            changes.add(s.getLHS());
          updateWorkListAkka(s);
        }
        globalChange = true;
      }
    }
    return globalChange;
  }



    public boolean solveAkkaDel(IProgressMonitor monitor) throws CancelException {
      boolean globalChange = false;

      while (!workListAkka.isEmpty()) {
        MonitorUtil.throwExceptionIfCanceled(monitor);
        orderStatements();

        // duplicate insertion detection
        AbstractStatement s = workListAkka.takeStatement();

        if(s.getLHS() != null && theRoot != null){
          IVariable lhs = s.getLHS();
          if(theRoot.contains(lhs))
            continue;
        }

        byte code = s.evaluateDel();
        if (verbose) {
          nEvaluated++;
          if (nEvaluated % getVerboseInterval() == 0) {
            performVerboseAction();
          }
          if (nEvaluated % getPeriodicMaintainInterval() == 0) {
            periodicMaintenance();
          }

        }
        if (isChanged(code)) {
          if(isChange){
            if(!changes.contains(s.getLHS()))
              changes.add(s.getLHS());
            updateWorkListAkka(s);
          }
          globalChange = true;
        }
      }
      return globalChange;
    }

  public boolean emptyWorkListAkka() {
    return workListAkka.isEmpty();
  }

  public void addToWorklistAkka(AbstractStatement statement) {
    synchronized (workListAkka) {
      workListAkka.insertStatement(statement);
    }
  }

  public void makeWorkListAkkaEmpty() {
    workListAkka.clear();
  }
}
