/*******************************************************************************
 * Copyright (c) 2007 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.wala.ipa.callgraph.threadpool;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.ibm.wala.akkaTaskScheduler.ResultFromRR;
import com.ibm.wala.akkaTaskScheduler.ResultFromS;
import com.ibm.wala.akkaTaskScheduler.WorkContentForCheckChange;
import com.ibm.wala.akkaTaskScheduler.WorkContentForSpecial;
import com.ibm.wala.fixedpoint.impl.AbstractFixedPointSolver;
import com.ibm.wala.fixpoint.AbstractOperator;
import com.ibm.wala.fixpoint.AbstractStatement;
import com.ibm.wala.fixpoint.IVariable;
import com.ibm.wala.fixpoint.UnaryStatement;
import com.ibm.wala.ipa.callgraph.propagation.AssignOperator;
import com.ibm.wala.ipa.callgraph.propagation.PointsToSetVariable;
import com.ibm.wala.ipa.callgraph.propagation.PropagationSystem;
import com.ibm.wala.ipa.callgraph.propagation.PropagationCallGraphBuilder.FilterOperator;
import com.ibm.wala.util.intset.IntSetAction;
import com.ibm.wala.util.intset.IntSetUtil;
import com.ibm.wala.util.intset.MutableIntSet;
import com.ibm.wala.util.intset.MutableSharedBitVectorIntSet;
import com.ibm.wala.util.intset.MutableSharedBitVectorIntSetFactory;

public class ThreadHub {

  public ExecutorService threadrouter;
  private static int nrOfResults = 0;
  private static int nrOfWorks;
  private static boolean finished = false;

  public ThreadHub(int nrOfWorkers) {
//    threadrouter = Executors.newFixedThreadPool(nrOfWorkers);
    threadrouter = Executors.newWorkStealingPool(nrOfWorkers);
  }

  public ExecutorService getThreadRouter(){
    return threadrouter;
  }

  public void initialRRTasks(MutableIntSet targets, ArrayList<PointsToSetVariable> firstusers,
      PropagationSystem system) throws InterruptedException, ExecutionException{
    System.err.println("RR is called. ");
    ArrayList<Callable<ResultFromRR>> tasks = distributeRRTasks(targets, firstusers, system);
    ArrayList<Future<ResultFromRR>> results = (ArrayList<Future<ResultFromRR>>) threadrouter.invokeAll(tasks);
    continueRRTasks(results,targets, system);
  }

  private void continueRRTasks(ArrayList<Future<ResultFromRR>> results,MutableIntSet targets, PropagationSystem system) throws InterruptedException, ExecutionException {
    ArrayList<PointsToSetVariable> firstusers = new ArrayList<>();
    for (Future<ResultFromRR> future : results) {
      nrOfResults ++;
      ResultFromRR result = future.get();
      MutableIntSet newtarget = result.getNewTargets();
      ArrayList<PointsToSetVariable> nexts = result.getCheckNext();
      if(nexts != null){
        if(!nexts.isEmpty() && newtarget.size() > 0){
          Iterator<PointsToSetVariable> iterator = nexts.iterator();
          while(iterator.hasNext()){
            PointsToSetVariable next = iterator.next();
            if(next.getValue() != null){
              firstusers.add(next);
            }
          }
        }
      }
      doWeTerminate();
    }
    if(firstusers.size() > 0)
      initialRRTasks(targets, firstusers, system);
    doWeTerminate();
  }

  private static ArrayList<Callable<ResultFromRR>> distributeRRTasks(final MutableIntSet targets, ArrayList<PointsToSetVariable> firstusers,
      final PropagationSystem system) {
    ArrayList<Callable<ResultFromRR>> tasks = new ArrayList<>();
    Iterator<PointsToSetVariable> users = firstusers.iterator();
    while(users.hasNext()){
      final PointsToSetVariable user = users.next();
      nrOfWorks++;
      tasks.add(new Callable<ResultFromRR>() {
        @Override
        public ResultFromRR call() throws Exception {
          WorkContentForCheckChange taskForRR = new WorkContentForCheckChange(user, targets, system);
          return processRRTask(taskForRR);
        }
      });
    }
    return tasks;
  }


  public void initialSpecialTasks(ArrayList<PointsToSetVariable> lhss, MutableIntSet targets,  boolean isAddition,
      PropagationSystem system) throws InterruptedException, ExecutionException{
    System.err.println("Speical is called. ");
    ArrayList<Callable<ResultFromS>> tasks = distributeSpecialTasks(targets, lhss, isAddition, system);
    ArrayList<Future<ResultFromS>> results = (ArrayList<Future<ResultFromS>>) threadrouter.invokeAll(tasks);
    continueSpecialTasks(results,targets, isAddition, system);
  }

  private void continueSpecialTasks(ArrayList<Future<ResultFromS>> results, MutableIntSet targets, boolean isAddition,
      PropagationSystem system) throws InterruptedException, ExecutionException {
    ArrayList<PointsToSetVariable> firstusers = new ArrayList<>();
    for (Future<ResultFromS> future : results) {
      nrOfResults++;
      ResultFromS result = future.get();
      MutableIntSet newtarget = result.getNewTargets();
      ArrayList<PointsToSetVariable> nexts = result.getCheckNext();
      if(nexts != null){
        if(!nexts.isEmpty() && newtarget.size() > 0){
          Iterator<PointsToSetVariable> iterator = nexts.iterator();
          while(iterator.hasNext()){
            PointsToSetVariable next = iterator.next();
            if(next.getValue() != null){
              firstusers.add(next);
            }
          }
        }
      }
      doWeTerminate();
    }
    if(firstusers.size() > 0)
      initialSpecialTasks(firstusers, targets, isAddition, system);
    doWeTerminate();
  }

  private static ArrayList<Callable<ResultFromS>> distributeSpecialTasks(final MutableIntSet targets, ArrayList<PointsToSetVariable> lhss,
      final boolean isAddition, final PropagationSystem system) {
    ArrayList<Callable<ResultFromS>> tasks = new ArrayList<>();
    Iterator<PointsToSetVariable> users = lhss.iterator();
    while(users.hasNext()){
      final PointsToSetVariable user = users.next();
      nrOfWorks++;
      tasks.add(new Callable<ResultFromS>() {
        @Override
        public ResultFromS call() throws Exception {
          WorkContentForSpecial job = new WorkContentForSpecial(user, targets, isAddition, system);
          if(isAddition)
            return processSpecialWorkAddition(job);
          else
            return processSpecialWorkDeletion(job);
        }
      });
    }
    return tasks;
  }

  private static void doWeTerminate() {
    // if all jobs complete
    if(nrOfResults == nrOfWorks){
      //clear this round
      nrOfWorks = 0;
      nrOfResults = 0;
      finished  = true;
      return;
    }
  }

  public static boolean askstatus(){
    if(finished){
      finished = false;
      return false;
    }else{
      return true;
    }
  }

  private static ResultFromRR processRRTask(WorkContentForCheckChange work) {
    final PointsToSetVariable user = work.getUser();
    final MutableIntSet targets = work.getTargets();
    final PropagationSystem system = work.getPropagationSystem();
    ArrayList<PointsToSetVariable> next = new ArrayList<>();
    if(AbstractFixedPointSolver.theRoot.contains(user)
        || system.flowGraph.getNumberOfStatementsThatDef(user) == 0 //root
        || user.getValue() == null)
      return new ResultFromRR(user, next, (MutableSharedBitVectorIntSet) targets);
    //check
    final MutableSharedBitVectorIntSet remaining = new MutableSharedBitVectorIntSetFactory().makeCopy(targets);
    for (Iterator it = system.flowGraph.getStatementsThatDef(user); it.hasNext();) {
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
          MutableIntSet set = pv.getValue();
          if(set != null){
            MutableIntSet set1;
            synchronized (pv) {
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
        synchronized (user) {
          copy = IntSetUtil.makeMutableCopy(user.getValue());
        }
        //future
        for (Iterator it = system.flowGraph.getStatementsThatUse(user); it.hasNext();) {
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
            synchronized (pv) {
              byte mark = filter.evaluateDel(pv, (MutableSharedBitVectorIntSet)copy);
              if(mark == 1){
                AbstractFixedPointSolver.addToChanges(pv);
                classifyPointsToConstraints(pv, copy, next, system);
              }
            }
          }else{
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

  private static void classifyPointsToConstraints(PointsToSetVariable L, final MutableIntSet targets,
      ArrayList<PointsToSetVariable> next, PropagationSystem system){
    for (Iterator it = system.flowGraph.getStatementsThatUse(L); it.hasNext();) {
      AbstractStatement s = (AbstractStatement) it.next();
      AbstractOperator op = s.getOperator();
      if(op instanceof AssignOperator){
        PointsToSetVariable pv = (PointsToSetVariable) s.getLHS();
        if(pv.getValue() != null){
          next.add(pv);
        }
      }else if(op instanceof FilterOperator){
        FilterOperator filter = (FilterOperator) op;
        PointsToSetVariable pv = (PointsToSetVariable) s.getLHS();
        if(AbstractFixedPointSolver.theRoot.contains(pv))
          continue;
        byte mark = filter.evaluateDel(pv, (MutableSharedBitVectorIntSet)targets);
        if(mark == 1){
          AbstractFixedPointSolver.addToChanges(pv);
          classifyPointsToConstraints(pv, targets, next, system);
        }
      }else{
        system.addToWorklistAkka(s);
      }
    }
  }

  private static ResultFromS processSpecialWorkAddition(WorkContentForSpecial work) {
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
//      further check
      for (Iterator it = system.flowGraph.getStatementsThatUse(user); it.hasNext();) {
        AbstractStatement s = (AbstractStatement) it.next();
        AbstractOperator op = s.getOperator();
        if(op instanceof AssignOperator){
          PointsToSetVariable pv = (PointsToSetVariable) s.getLHS();
          if(pv.getValue() != null)
            next.add(pv);
        }else if(op instanceof FilterOperator){
          FilterOperator filter = (FilterOperator) op;
          PointsToSetVariable pv = (PointsToSetVariable) s.getLHS();
          byte mark = filter.evaluate(pv, (PointsToSetVariable)((UnaryStatement)s).getRightHandSide());
          if(mark == 1){
            AbstractFixedPointSolver.addToChanges(pv);
            next.add(pv);
          }
        }else{
          system.addToWorklistAkka(s);
        }
      }
    }else{
      next = null;
    }
    return new ResultFromS(user, next, remaining, work.getIsAdd());
  }

  private static ResultFromS processSpecialWorkDeletion(WorkContentForSpecial work) {
    final PointsToSetVariable user = work.getUser();
    final MutableIntSet targets = work.getTargets();
    final PropagationSystem system = work.getPropagationSystem();
    ArrayList<PointsToSetVariable> next = new ArrayList<>();
    if(AbstractFixedPointSolver.theRoot.contains(user)
        || system.flowGraph.getNumberOfStatementsThatDef(user) == 0 //root
        || user.getValue() == null)
      return new ResultFromS(user, next, (MutableSharedBitVectorIntSet) targets, work.getIsAdd());

    final MutableSharedBitVectorIntSet remaining = new MutableSharedBitVectorIntSetFactory().makeCopy(targets);
    for (Iterator it = system.flowGraph.getStatementsThatDef(user); it.hasNext();) {
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
          MutableIntSet set = pv.getValue();
          if(set != null){
            MutableIntSet set1;
            synchronized (pv) {
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
        synchronized (user) {
          copy = IntSetUtil.makeMutableCopy(user.getValue());
        }
        //future
        for (Iterator it = system.flowGraph.getStatementsThatUse(user); it.hasNext();) {
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
            synchronized (pv) {
              byte mark = filter.evaluateDel(pv, (MutableSharedBitVectorIntSet)copy);
              if(mark == 1){
                AbstractFixedPointSolver.addToChanges(pv);
                classifyPointsToConstraints(pv, copy, next, system);
              }
            }
          }else{
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



}
