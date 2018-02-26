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


import java.util.Map;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.MonitorUtil.IProgressMonitor;

/**
 * standard fixed-point iterative solver for pointer analysis
 */
public class StandardSolver extends AbstractPointsToSolver {

  private static final boolean DEBUG_PHASES = DEBUG || false;

  public StandardSolver(PropagationSystem system, PropagationCallGraphBuilder builder) {
    super(system, builder);
  }

  @Override
  public void solve(CGNode node, Map added, Map deleted)
  {
    final PropagationSystem system = getSystem();
    final PropagationCallGraphBuilder builder = getBuilder();
    try {

      for(Object key: deleted.keySet()){
        SSAInstruction diff = (SSAInstruction)key;
        ISSABasicBlock bb = (ISSABasicBlock)deleted.get(key);

//        long start_time = System.currentTimeMillis();

        system.setFirstDel(true);
        builder.setDelete(true);
        builder.processDiff(node,bb,diff);//only for those affecting data flow facts
        system.setFirstDel(false);

        system.solveDel(null);

//        if(system.reConstruct(null)){
//          system.solve(null);
//        }
        System.out.print((System.currentTimeMillis()-start_time) + "\t");

      }
      //added instructions
      builder.setDelete(false);//add
      builder.addConstraintsFromChangedNode(node, null);
    do{
        system.solve(null);
      builder.addConstraintsFromNewNodes(null);
    } while (!system.emptyWorkList());

      /*
      for(Object key: added.keySet()){
        SSAInstruction diff = (SSAInstruction)key;
        ISSABasicBlock bb = (ISSABasicBlock)added.get(key);


    //System.out.println("del instruction "+ diff.toString() + " : " + (System.currentTimeMillis()-start_time));
      //--- add then delete
      long start_time = System.currentTimeMillis();
      builder.processDiff(node,bb,diff,false);
      system.solve(null);
      System.out.println((System.currentTimeMillis()-start_time));
      }

      */
    } catch (CancelException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  long start_time;
  long end_time;
  long total_time = 0;
  /*
   * @see com.ibm.wala.ipa.callgraph.propagation.IPointsToSolver#solve()
   */
  @Override
  public void solve(IProgressMonitor monitor) throws IllegalArgumentException, CancelException {
    int i = 0;
    final PropagationSystem system = getSystem();
    final PropagationCallGraphBuilder builder = getBuilder();


    do {
      i++;

      if (DEBUG_PHASES) {
        System.err.println("Iteration " + i);
      }
      start_time = System.currentTimeMillis();
      system.solve(monitor);
      end_time = System.currentTimeMillis();
      total_time = total_time + end_time - start_time;

//      if(total_time >= 10800000){
//        //3h = 10800000ms
//        break;
//      }

      if (DEBUG_PHASES) {
        System.err.println("Solved " + i);
      }

      if (builder.getOptions().getMaxNumberOfNodes() > -1) {
        if (builder.getCallGraph().getNumberOfNodes() >= builder.getOptions().getMaxNumberOfNodes()) {
          if (DEBUG) {
            System.err.println("Bail out from call graph limit" + i);
          }
          throw CancelException.make("reached call graph size limit");
        }
      }

      // Add constraints until there are no new discovered nodes
      if (DEBUG_PHASES) {
        System.err.println("adding constraints");
      }
      builder.addConstraintsFromNewNodes(monitor);

      // getBuilder().callGraph.summarizeByPackage();

      if (DEBUG_PHASES) {
        System.err.println("handling reflection");
      }
      if (i <= builder.getOptions().getReflectionOptions().getNumFlowToCastIterations()) {
        getReflectionHandler().updateForReflection(monitor);
      }
      // Handling reflection may have discovered new nodes!
      if (DEBUG_PHASES) {
        System.err.println("adding constraints again");
      }
      builder.addConstraintsFromNewNodes(monitor);

      if (monitor != null) { monitor.worked(i); }
      // Note that we may have added stuff to the
      // worklist; so,
    } while (!system.emptyWorkList());

//    System.out.println("total time: " + total_time);
//    System.out.println(" wl: " + AbstractFixedPointSolver.countforTotalWL);

    /*
 // sz: manually go through all pre-recorded ssa instructions, delete it first
    // then add it back. Some validation is based on comparison between original call graph
    // with delete then add call graph.
    System.out.println("num of instructions: "+((SSAPropagationCallGraphBuilder)builder).numInstructions);
    system.closeFirstSolve();
    // sz: considering properties of ssa instructions and this framework, both CGNode and Block info is needed
    // and stored in ArrayList<Object>>
    HashMap<SSAInstruction, ArrayList<Object>> del = ((SSAPropagationCallGraphBuilder)builder).del;
    if(del == null)
      return;
    for(SSAInstruction diff: del.keySet()){
      long start_time = System.currentTimeMillis();

      system.setFirstDel();
      builder.delDiff(diff);
      // sz: only directly effected edges are deleted
      system.closeFirstDel();

      system.solveDel(null);

      if(system.reConstruct(null)){
        system.solve(null);
      }
      System.out.print((System.currentTimeMillis()-start_time) + "\t");

      //System.out.println("del instruction "+ diff.toString() + " : " + (System.currentTimeMillis()-start_time));
      //--- add then delete
      start_time = System.currentTimeMillis();
      builder.addDiff(diff);
      system.solve(null);
      System.out.println((System.currentTimeMillis()-start_time));
  }
      */

  }
 // sz: return points to map, make it accessable for users
    public PointsToMap getPointsToMap(){
      return getSystem().pointsToMap;
    }

    //seems not used anymore, codes above is used instead.
    // sz: manually go through all SSA instructions, delete all one by one first
    // then add them back one by one again.
//    public void addDel() throws IllegalArgumentException, CancelException {
//        final PropagationSystem system = getSystem();
//        final PropagationCallGraphBuilder builder = getBuilder();
//        system.closeFirstSolve();
//
//        // sz: when building the graph, save all ssa instruction and corresponding cgnode
//        HashMap<SSAInstruction, CGNode> del = builder.del;
//        for(SSAInstruction diff: del.keySet()){
//          long start_time = System.currentTimeMillis();
//          builder.processDiff(diff,true);
//          system.solveDel(null);
//          if(system.reConstruct(null)){
//            system.solve(null);
//          }
//          System.out.println("del instruction "+ diff.toString() + " : " + (System.currentTimeMillis()-start_time));
//        }
//        HashMap<SSAInstruction, CGNode> add = builder.add;
//        for(SSAInstruction diff : add.keySet()){
//          long start_time = System.currentTimeMillis();
//          builder.processDiff(diff,false);
//          system.solve(null);
//          System.out.println("add instruction "+ diff.toString() + " : " + (System.currentTimeMillis()-start_time));
//        }
//
//    }

}
