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
package com.ibm.wala.akkaTaskScheduler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

import com.ibm.wala.ipa.callgraph.propagation.PointsToSetVariable;
import com.ibm.wala.ipa.callgraph.propagation.PropagationSystem;
import com.ibm.wala.util.intset.MutableIntSet;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.routing.BalancingPool;

public class Hub extends UntypedActor{

  private final int nrOfWorkers;
  private MutableIntSet targets;
  private ArrayList<Result> results;
  private int nrOfResults;
  private int nrOfWorks;

  private final ActorRef workerRouter;

  private PropagationSystem system;

  private static boolean finished = false;

  public Hub(final int nrOfWorkers) {
    this.nrOfWorkers = nrOfWorkers;
    Props props = Props.create(Worker.class).withRouter(new BalancingPool(nrOfWorkers));
    workerRouter = this.getContext().actorOf(props, "workerRouter");
  }

  @Override
  public void onReceive(Object message) throws Throwable {
    if(message instanceof SchedulerForResetSetAndRecompute){
      SchedulerForResetSetAndRecompute work = (SchedulerForResetSetAndRecompute) message;
      processResetSetAndRecompute(work);
    }else if(message instanceof SchedulerForSpecial){
      SchedulerForSpecial work = (SchedulerForSpecial) message;
      processSpecial(work);
    }else if(message instanceof ResultFromRR){
      ResultFromRR result = (ResultFromRR) message;
      analyzeResultFromRR(result);
    }else if(message instanceof ResultFromS){
      ResultFromS result = (ResultFromS) message;
      analyzeResultFromS(result);
    }else{
      unhandled(message);
    }
  }

  private void processSpecial(SchedulerForSpecial work) {
    // initial job distribution
    system = work.getPropagationSystem();
    MutableIntSet targets = work.getTargets();
    Iterator<PointsToSetVariable> lhss = work.getLhss().iterator();
    boolean op = work.getIsAddition();
    while(lhss.hasNext()){
      PointsToSetVariable lhs = lhss.next();
      WorkContentForSpecial job = new WorkContentForSpecial(lhs, targets, op, system);
      nrOfWorks++;
      workerRouter.tell(job, getSelf());
    }
  }

  private void processResetSetAndRecompute(SchedulerForResetSetAndRecompute work) {
    // initial job distribution
    system = work.getPropagationSystem();
    MutableIntSet targets = work.getTargets();
    Iterator<PointsToSetVariable> users = work.getFirstUsers().iterator();
    while(users.hasNext()){
      PointsToSetVariable user = users.next();
      WorkContentForCheckChange job = new WorkContentForCheckChange(user, targets, system);
      nrOfWorks++;
      workerRouter.tell(job, getSelf());
    }
  }


  private void analyzeResultFromRR(ResultFromRR result) {
    nrOfResults++;
    PointsToSetVariable ptsv = result.getUser();
    MutableIntSet newtarget = result.getNewTargets();
    ArrayList<PointsToSetVariable> nexts = result.getCheckNext();
//    if(!processed.contains(ptsv)){
//      processed.add(ptsv);
      if(nexts != null){
        if(!nexts.isEmpty() && newtarget.size() > 0){
          Iterator<PointsToSetVariable> iterator = nexts.iterator();
          while(iterator.hasNext()){
            PointsToSetVariable next = iterator.next();
            if(next.getValue() != null){//!processed.contains(next) &&
              WorkContentForCheckChange job = new WorkContentForCheckChange(next, newtarget, system);
              nrOfWorks++;
              workerRouter.tell(job, getSelf());
//              processed.add(next);
            }
          }
        }
      }
//    }
    doWeTerminate();
  }

  private void doWeTerminate() {
    // if all jobs complete
    if(nrOfResults == nrOfWorks){
//      System.err.println("num of works: " + nrOfWorks);
      //clear this round
      nrOfWorks = 0;
      nrOfResults = 0;
      finished  = true;
      system = null;
    }
  }

  private void analyzeResultFromS(ResultFromS result) {
    // TODO Auto-generated method stub
    nrOfResults++;
    PointsToSetVariable ptsv = result.getUser();
    MutableIntSet newtarget = result.getNewTargets();
    ArrayList<PointsToSetVariable> nexts = result.getCheckNext();
    boolean isAdd = result.getIsAdd();
//    if(!processed.contains(ptsv)){
//      processed.add(ptsv);
      if(nexts != null){
        if(!nexts.isEmpty() && newtarget.size() > 0){
          Iterator<PointsToSetVariable> iterator = nexts.iterator();
          while(iterator.hasNext()){
            PointsToSetVariable next = iterator.next();
            if(next.getValue() != null){//!processed.contains(next) &&
              WorkContentForSpecial job = new WorkContentForSpecial(next, newtarget, isAdd, system);
              nrOfWorks++;
              workerRouter.tell(job, getSelf());
//              processed.add(ptsv);
            }
          }
        }
      }
//    }
    doWeTerminate();
  }

  public static boolean askstatus(){
    if(finished){
      finished = false;
      return false;
    }else{
      return true;
    }
  }

}
