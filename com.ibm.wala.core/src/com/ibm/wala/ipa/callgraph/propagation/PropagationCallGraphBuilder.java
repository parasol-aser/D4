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

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ibm.wala.analysis.reflection.IllegalArgumentExceptionContext;
import com.ibm.wala.cfg.ControlFlowGraph;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.Language;
import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.classLoader.SyntheticClass;
import com.ibm.wala.fixpoint.IVariable;
import com.ibm.wala.fixpoint.UnaryOperator;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.callgraph.Context;
import com.ibm.wala.ipa.callgraph.ContextSelector;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.AbstractRootMethod;
import com.ibm.wala.ipa.callgraph.impl.ExplicitCallGraph;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder.ConstraintVisitor;
import com.ibm.wala.ipa.callgraph.propagation.rta.RTAContextInterpreter;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.CancelRuntimeException;
import com.ibm.wala.util.MonitorUtil.IProgressMonitor;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.IntSetAction;
import com.ibm.wala.util.intset.IntSetUtil;
import com.ibm.wala.util.intset.MutableIntSet;
import com.ibm.wala.util.intset.MutableSharedBitVectorIntSet;
import com.ibm.wala.util.warnings.Warning;
import com.ibm.wala.util.warnings.Warnings;

/**
 * This abstract base class provides the general algorithm for a call graph builder that relies on propagation through an iterative
 * dataflow solver
 *
 * TODO: This implementation currently keeps all points to sets live ... even those for local variables that do not span
 * interprocedural boundaries. This may be too space-inefficient .. we can consider recomputing local sets on demand.
 */
public abstract class PropagationCallGraphBuilder implements CallGraphBuilder {
  private final static boolean DEBUG_ALL = false;

  final static boolean DEBUG_ASSIGN = DEBUG_ALL | false;

  private final static boolean DEBUG_ARRAY_LOAD = DEBUG_ALL | false;

  private final static boolean DEBUG_ARRAY_STORE = DEBUG_ALL | false;

  private final static boolean DEBUG_FILTER = DEBUG_ALL | false;

  final protected static boolean DEBUG_GENERAL = DEBUG_ALL | false;

  private final static boolean DEBUG_GET = DEBUG_ALL | false;

  private final static boolean DEBUG_PUT = DEBUG_ALL | false;

  private final static boolean DEBUG_ENTRYPOINTS = DEBUG_ALL | false;


  /**
   * Meta-data regarding how pointers are modeled
   */
  protected PointerKeyFactory pointerKeyFactory;

  /**
   * The object that represents the java.lang.Object class
   */
  final private IClass JAVA_LANG_OBJECT;

  /**
   * Governing class hierarchy
   */
  final protected IClassHierarchy cha;

  /**
   * Special rules for bypassing Java calls
   */
  final protected AnalysisOptions options;

  /**
   * Cache of IRs and things
   */
  private final AnalysisCache analysisCache;

  /**
   * Set of nodes that have already been traversed for constraints
   */
  final private Set<CGNode> alreadyVisited = HashSetFactory.make();

  /**
   * At any given time, the set of nodes that have been discovered but not yet processed for constraints
   */
  private Set<CGNode> discoveredNodes = HashSetFactory.make();

  /**
   * Set of calls (CallSiteReferences) that are created by entrypoints
   */
  final protected Set<CallSiteReference> entrypointCallSites = HashSetFactory.make();

  /**
   * The system of constraints used to build this graph
   */
  public PropagationSystem system;

  /**
   * Algorithm used to solve the system of constraints
   */
  private IPointsToSolver solver;

  /**
   * The call graph under construction
   */
  protected final ExplicitCallGraph callGraph;

  /**
   * Singleton operator for assignments
   */
  protected final static AssignOperator assignOperator = new AssignOperator();

  /**
   * singleton operator for filter
   */
  public final FilterOperator filterOperator = new FilterOperator();

  /**
   * singleton operator for inverse filter
   */
  protected final InverseFilterOperator inverseFilterOperator = new InverseFilterOperator();

  /**
   * An object which interprets methods in context
   */
  private SSAContextInterpreter contextInterpreter;

  /**
   * A context selector which may use information derived from the propagation-based dataflow.
   */
  protected ContextSelector contextSelector;

  /**
   * An object that abstracts how to model instances in the heap.
   */
  protected InstanceKeyFactory instanceKeyFactory;

  /**
   * Algorithmic choice: should the GetfieldOperator and PutfieldOperator cache its previous history to reduce work?
   */
  final private boolean rememberGetPutHistory = true;


  public HashMap add;
  public HashMap del;

  /**
   * @param cha governing class hierarchy
   * @param options governing call graph construction options
   * @param pointerKeyFactory factory which embodies pointer abstraction policy
   */
  protected PropagationCallGraphBuilder(IClassHierarchy cha, AnalysisOptions options, AnalysisCache cache,
      PointerKeyFactory pointerKeyFactory) {
    if (cha == null) {
      throw new IllegalArgumentException("cha is null");
    }
    if (options == null) {
      throw new IllegalArgumentException("options is null");
    }
    assert cache != null;
    this.cha = cha;
    this.options = options;
    this.analysisCache = cache;
    // we need pointer keys to handle reflection
    assert pointerKeyFactory != null;
    this.pointerKeyFactory = pointerKeyFactory;
    callGraph = createEmptyCallGraph(cha, options);
    try {
      callGraph.init();
    } catch (CancelException e) {
      if (DEBUG_GENERAL) {
        System.err.println("Could not initialize the call graph due to node number constraints: " + e.getMessage());
      }
    }
    callGraph.setInterpreter(contextInterpreter);
    JAVA_LANG_OBJECT = cha.lookupClass(TypeReference.JavaLangObject);
  }

  protected ExplicitCallGraph createEmptyCallGraph(IClassHierarchy cha, AnalysisOptions options) {
    return new ExplicitCallGraph(cha, options, getAnalysisCache());
  }

  /**
   * @return true iff the klass represents java.lang.Object
   */
  protected boolean isJavaLangObject(IClass klass) {
    return (klass.getReference().equals(TypeReference.JavaLangObject));
  }

  public CallGraph makeCallGraph(AnalysisOptions options) throws IllegalArgumentException, CancelException {
    return makeCallGraph(options, null);
  }


  //for experiments only
  public static long totaltime = 0;
  //
  //  public static HSSFWorkbook wb = new HSSFWorkbook();
  //  HSSFSheet sheet = wb.createSheet("parallel");
  //
  //  public void initialSheet(){
  //    Row row1st = sheet.createRow(0);
  //    Cell r1c1 = row1st.createCell(0);    r1c1.setCellValue("Deletion Time");
  //    Cell r1c2 = row1st.createCell(1);    r1c2.setCellValue("#of changes");
  //    Cell r1c3 = row1st.createCell(2);    r1c3.setCellValue("Addition Time");
  //    Cell r1c4 = row1st.createCell(3);    r1c4.setCellValue("#of changes");
  //    Cell r1c5 = row1st.createCell(4);    r1c5.setCellValue("Inst");
  //
  //  }
  //
  //  int rowNum = 1;
  //  Row row;
  //
  //  public void writeDTime(long dt, int delCh){
  //    row = sheet.createRow(rowNum);
  //    row.createCell(0).setCellValue(dt);
  //    row.createCell(1).setCellValue(delCh);
  //  }
  //
  //  public void writeATime(long at, int addCh){
  //    row.createCell(2).setCellValue(at);
  //    row.createCell(3).setCellValue(addCh);
  //  }
  //
  //  public void writeInst(SSAInstruction inst){
  //    row.createCell(4).setCellValue(inst.toString());
  //    rowNum++;
  //  }


  /**
   * for incremental pta check
   * @param n
   */
  public void testChange(CGNode node) {
    system.setChange(true);
    IR ir = node.getIR();
    if(ir==null)
      return;

    DefUse du = new DefUse(ir);
    ConstraintVisitor v = ((SSAPropagationCallGraphBuilder)this).makeVisitor(node);
    v.setIR(ir);
    v.setDefUse(du);

    ControlFlowGraph<SSAInstruction, ISSABasicBlock> cfg = ir.getControlFlowGraph();
    SSAInstruction[] insts = ir.getInstructions();
    int size = insts.length;

    for(int i=size;i>0;i--){
      SSAInstruction inst = insts[i-1];

      if(inst==null)
        continue;//skip null

      ISSABasicBlock bb = cfg.getBlockForInstruction(inst.iindex);
      //delete
      try{
        System.out.println("........ Deleting SSAInstruction:      "+ inst.toString());
        this.setDelete(true);
        system.setFirstDel(true);
        v.setBasicBlock(bb);
        long delete_start_time = System.currentTimeMillis();

        inst.visit(v);
        system.setFirstDel(false);
        do{
          system.solveAkkaDel(null);
        }while(!system.emptyWorkListAkka());
        system.clearTheRoot();
        system.makeWorkListAkkaEmpty();
        setDelete(false);

        long delete_end_time = System.currentTimeMillis();
        long delete_time = (delete_end_time-delete_start_time);

        HashSet<IVariable> resultsDelete = system.changes;
        int deletesize = resultsDelete.size();
//        Iterator<IVariable> itDelete = resultsDelete.iterator();
//        while(itDelete.hasNext()){
//          System.out.println(((PointsToSetVariable)itDelete.next()).getPointerKey().toString());
//        }
//        System.out.println("num of deletion changes seq: " + resultsDelete.size());
        system.clearChanges();

        //add
        System.out.println("........ Adding SSAInstruction:      "+ inst.toString());
        long add_start_time = System.currentTimeMillis();
        inst.visit(v);
        do{
          system.solveAkkaAdd(null);
          addConstraintsFromNewNodes(null);
        } while (!system.emptyWorkListAkka());
        system.makeWorkListAkkaEmpty();

        long add_end_time = System.currentTimeMillis();
        long add_time = (add_end_time-add_start_time);

        HashSet<IVariable> results = system.changes;
        int addsize = results.size();
        if(addsize == deletesize){
          System.out.println(".......... the same points-to sets changed in deleting and adding instruction. ");
        }

        boolean nochange = true;
        Iterator<IVariable> it = results.iterator();
        while(it.hasNext()){
//          System.out.println(((PointsToSetVariable)it.next()).getPointerKey().toString());
          PointsToSetVariable var = (PointsToSetVariable) it.next();
          MutableIntSet update = var.getValue();
          MutableIntSet origin = system.var_pts_map.get(var);
          if(var != null && update != null){
            if(!update.sameValue(origin)){
              nochange = false;
            }
          }
        }

        if(nochange){
          System.out.println(".......... points-to sets are the same before deleting inst and after adding back inst. ");
        }else{
          System.err.println("********** points-to sets are the changed before deleting inst and after adding back inst. ");
        }

        system.clearChanges();

        System.out.println();
        totaltime = totaltime+ delete_time + add_time;

      }catch(Exception e)
      {
        e.printStackTrace();
      }
    }
  }

  /**
   * for experiment
   * @param node
   * @param ps
   */
  public void testChange(CGNode node, PrintStream ps){
    system.setChange(true);
    IR ir = node.getIR();
    if(ir==null)
      return;

    DefUse du = new DefUse(ir);
    ConstraintVisitor v = ((SSAPropagationCallGraphBuilder)this).makeVisitor(node);
    v.setIR(ir);
    v.setDefUse(du);

    ControlFlowGraph<SSAInstruction, ISSABasicBlock> cfg = ir.getControlFlowGraph();
    SSAInstruction[] insts = ir.getInstructions();
    int size = insts.length;
    for(int i=size;i>0;i--){

      SSAInstruction inst = insts[i-1];

      if(inst==null)
        continue;//skip null

      //          if(!inst.toString().contains("putfield 29.< Primordial, Ljava/util/HashMap$TreeNode, left, <Primordial,Ljava/util/HashMap$TreeNode> > = 26"))
      //          if(!inst.toString().contains("15 = arrayload 8[14]"))
      //derby
      //          if(!inst.toString().contains("putfield 32.< Primordial, Ljava/util/HashMap$Node, next, <Primordial,Ljava/util/HashMap$Node> > = 35"))
      //            if(!inst.toString().contains("28 = arrayload 3[53]"))
      //          if(!inst.toString().contains("return 5"))
      //circular wait
      //            if(!inst.toString().contains("31 = getfield < Primordial, Ljava/util/HashMap$Node, next, <Primordial,Ljava/util/HashMap$Node> > 40"))
      //          if(!inst.toString().contains("putfield 44.< Primordial, Ljava/util/HashMap$Node, next, <Primordial,Ljava/util/HashMap$Node> > = 40"))
      //        if(!inst.toString().contains("putstatic < Primordial, Ljava/util/ArrayList, DEFAULTCAPACITY_EMPTY_ELEMENTDATA, <Primordial,[Ljava/lang/Object> > = 4"))
      //            if(!inst.toString().contains("putfield 1.< Application, Lorg/apache/derby/catalog/types/BaseTypeIdImpl, SQLTypeName, <Application,Ljava/lang/String> > = 39"))
      //        if(!inst.toString().contains("55 = invokestatic < Primordial, Ljava/util/HashMap$TreeNode, balanceInsertion(Ljava/util/HashMap$TreeNode;Ljava/util/HashMap$TreeNode;)Ljava/util/HashMap$TreeNode; > 13,52 @303 exception:54"))
      //          if(!inst.toString().contains("12 = getfield < Application, Lorg/apache/derby/impl/jdbc/authentication/AuthenticationServiceBase, authenticationScheme, <Application,Lorg/apache/derby/authentication/UserAuthenticator> > 1"))
      //          if(!inst.toString().contains("6 = getfield < Application, Lorg/apache/derby/impl/drda/DRDAConnThread, database, <Application,Lorg/apache/derby/impl/drda/Database> > 1"))
      //          if(!inst.toString().contains("3 = getfield < Primordial, Ljava/util/Hashtable$Enumerator, entry, <Primordial,Ljava/util/Hashtable$Entry> > 1"))
      //floodlight
      //          if(!inst.toString().contains("putfield 18.< Primordial, Ljava/util/HashMap$TreeNode, next, <Primordial,Ljava/util/HashMap$Node> > = 13"))
      //            if(!inst.toString().contains("12 = arrayload 5[11]"))
      //        if(!inst.toString().contains("putfield 42.< Primordial, Ljava/util/HashMap$Node, value, <Primordial,Ljava/lang/Object> > = 4"))
      //        if(!inst.toString().contains("return 7"))
      //        if(!inst.toString().contains("12 = arrayload 5[11]"))
      //          if(!inst.toString().contains("putstatic < Application, Lorg/slf4j/LoggerFactory, NOP_FALLBACK_FACTORY, <Application,Lorg/slf4j/helpers/NOPLoggerFactory> > = 5"))
      //        if(!inst.toString().contains("29 = getstatic < Application, Ljava/util/logging/Level, FINE, <Application,Ljava/util/logging/Level> >"))
      //        if(!inst.toString().contains("invokestatic < Application, Lorg/apache/derby/iapi/services/monitor/Monitor, logTextMessage(Ljava/lang/String;Ljava/lang/Object;)V > 12,14 @66 exception:15"))
      //          if(!inst.toString().contains("putfield 32.< Primordial, Ljava/util/HashMap$Node, next, <Primordial,Ljava/util/HashMap$Node> > = 35"))
      //            continue;

      //test
      //          if(!inst.toString().contains("arrayload") && !inst.toString().contains("getfield"))
      //            if(!inst.toString().contains("arraystore") && !inst.toString().contains("putfield"))
      //          if(!inst.toString().contains("invoke") && !inst.toString().contains("invoke"))
      //        if(!inst.toString().contains("putfield 18.< Primordial, Ljava/util/HashMap$TreeNode, next, <Primordial,Ljava/util/HashMap$Node> > = 13"))
      //          if(!inst.toString().contains("putfield 32.< Primordial, Ljava/util/HashMap$Node, next, <Primordial,Ljava/util/HashMap$Node> > = 35"))
      //
      //              continue;
//      if(!inst.toString().contains("invokestatic < Primordial, Ljava/util/Arrays, sort([Ljava/lang/Object;)V > 1 @5 exception:11"))
//        continue;
//    85 = invokespecial < Application, Lorg/h2/command/Parser, readIf(Ljava/lang/String;)Z > 1,83 @886 exception:84
      System.out.println("INST:      "+ inst.toString());
      ISSABasicBlock bb = cfg.getBlockForInstruction(inst.iindex);
      //delete
      try{

        this.setDelete(true);
        system.setFirstDel(true);
        v.setBasicBlock(bb);
        long delete_start_time = System.currentTimeMillis();

        inst.visit(v);

        system.setFirstDel(false);
        do{
          system.solveAkkaDel(null);
        }while(!system.emptyWorkListAkka());
        system.clearTheRoot();
        system.makeWorkListAkkaEmpty();
        setDelete(false);

        long delete_end_time = System.currentTimeMillis();
        long delete_time = (delete_end_time-delete_start_time);

        HashSet<IVariable> resultsDelete = system.changes;
        Iterator<IVariable> itDelete = resultsDelete.iterator();
        while(itDelete.hasNext()){
          System.out.println(((PointsToSetVariable)itDelete.next()).getPointerKey().toString());
        }
        int delsize = resultsDelete.size();
        System.out.println("num of deletion changes seq: " + resultsDelete.size());

        //          if(delete_time!=0){
        //            writeDTime(delete_time, resultsDelete.size());//resultsDelete.size()
        //          }

        system.clearChanges();

        long add_start_time = System.currentTimeMillis();
        inst.visit(v);
        do{
          system.solveAkkaAdd(null);
          addConstraintsFromNewNodes(null);
        } while (!system.emptyWorkListAkka());
        system.makeWorkListAkkaEmpty();

        long add_end_time = System.currentTimeMillis();
        long add_time = (add_end_time-add_start_time);

        HashSet<IVariable> results = system.changes;
        Iterator<IVariable> it = results.iterator();
        while(it.hasNext()){
          System.out.println(((PointsToSetVariable)it.next()).getPointerKey().toString());
        }
        int addsize = results.size();
        System.out.println("num of addition changes seq: " +results.size());

//        if(delsize != addsize)
//          System.err.println("NOT MATCH ");
        System.out.println("PARALLEL INCREMENTAL DELETE TIME: " +delete_time + ";  PARALLEL INCREMEMTAL ADDITION TIME: " + add_time);
        //          if(delete_time!=0){
        //            writeATime(add_time, results.size());//results.size()
        //            writeInst(inst);
        //          }

        system.clearChanges();
        //          ps.print(delete_time+" "+add_time+" ");

        if(delete_time > 3000){
          System.err.println("BAD NODE: "+ node.toString() + "\n --- INST: "+inst.toString());
        }

        System.out.println();
        totaltime = totaltime+ delete_time + add_time;

      }catch(Exception e)
      {
        e.printStackTrace();
      }
    }
    ps.println();
  }

  ////**** do not forget to change this function to handle IDE
  @Override
  public void updatePointerAnalaysis(CGNode node, Map added, Map deleted,
      ConstraintVisitor v_old, ConstraintVisitor v_new) {
    system.setChange(true);
    try {
      this.setDelete(true);

//      System.out.println("**** Update PTA, Delete Inst: ");
      for(Object key: deleted.keySet()){
        SSAInstruction diff = (SSAInstruction)key;
//        System.out.println("        " + diff.toString());
        ISSABasicBlock bb = (ISSABasicBlock)deleted.get(key);
        v_old.setBasicBlock(bb);

        system.setFirstDel(true);
        diff.visit(v_old);
        system.setFirstDel(false);
        do{
          system.solveAkkaDel(null);
        }while(!system.emptyWorkListAkka());
        system.clearTheRoot();
        system.makeWorkListAkkaEmpty();
      }

      this.setDelete(false);//add

      //for additions, solved when updating call graph??

//      for(Object key: added.keySet()){
//        SSAInstruction diff = (SSAInstruction)key;
//        ISSABasicBlock bb = (ISSABasicBlock)added.get(key);
//        v_new.setBasicBlock(bb);
//        diff.visit(v_new);
//        do{
//          system.solveAkkaAdd(null);
//          addConstraintsFromNewNodes(null);
//        } while (!system.emptyAkkaWorkList());
//      }

    } catch (Exception e) {
      e.printStackTrace();
    }
  }



  @Override
  public void updatePointerAnalaysis(CGNode node, Map added, Map deleted) {

    //	CGNode node = callGraph.findOrCreateNode(method, Everywhere.EVERYWHERE);
    //    this.markChanged(node);
    try {
      solver.solve(node, added, deleted);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
  /*
   * @see com.ibm.wala.ipa.callgraph.CallGraphBuilder#makeCallGraph(com.ibm.wala.ipa.callgraph.AnalysisOptions)
   */
  @Override
  public CallGraph makeCallGraph(AnalysisOptions options, IProgressMonitor monitor) throws IllegalArgumentException,
  CallGraphBuilderCancelException {
    if (options == null) {
      throw new IllegalArgumentException("options is null");
    }
    system = makeSystem(options);

    if (DEBUG_GENERAL) {
      System.err.println("Enter makeCallGraph!");
    }

    if (DEBUG_GENERAL) {
      System.err.println("Initialized call graph");
    }

    system.setMinEquationsForTopSort(options.getMinEquationsForTopSort());
    system.setTopologicalGrowthFactor(options.getTopologicalGrowthFactor());
    system.setMaxEvalBetweenTopo(options.getMaxEvalBetweenTopo());

    discoveredNodes = HashSetFactory.make();
    discoveredNodes.add(callGraph.getFakeRootNode());

    // Set up the initially reachable methods and classes
    for (Iterator it = options.getEntrypoints().iterator(); it.hasNext();) {
      Entrypoint E = (Entrypoint) it.next();
      if (DEBUG_ENTRYPOINTS) {
        System.err.println("Entrypoint: " + E);
      }
      SSAAbstractInvokeInstruction call = E.addCall((AbstractRootMethod) callGraph.getFakeRootNode().getMethod());

      if (call == null) {
        Warnings.add(EntrypointResolutionWarning.create(E));
      } else {
        entrypointCallSites.add(call.getCallSite());
      }
    }

    /** BEGIN Custom change: throw exception on empty entry points. This is a severe issue that should not go undetected! */
    if (entrypointCallSites.isEmpty()) {
      throw new IllegalStateException("Could not create a entrypoint callsites."
          + " This happens when some parameters of the method can not be generated automatically "
          + "(e.g. when they refer to an interface or an abstract class).");
    }

    /** END Custom change: throw exception on empty entry points. This is a severe issue that should not go undetected! */
    customInit();

    solver = makeSolver();
    try {
      solver.solve(monitor);
    } catch (CancelException e) {
      CallGraphBuilderCancelException c = CallGraphBuilderCancelException.createCallGraphBuilderCancelException(e, callGraph,
          system.extractPointerAnalysis(this));
      throw c;
    } catch (CancelRuntimeException e) {
      CallGraphBuilderCancelException c = CallGraphBuilderCancelException.createCallGraphBuilderCancelException(e, callGraph,
          system.extractPointerAnalysis(this));
      throw c;
    }

    return callGraph;
  }

  protected PropagationSystem makeSystem(AnalysisOptions options) {
    return new PropagationSystem(callGraph, pointerKeyFactory, instanceKeyFactory);
  }

  protected abstract IPointsToSolver makeSolver();

  /**
   * A warning for when we fail to resolve a call to an entrypoint
   */
  private static class EntrypointResolutionWarning extends Warning {

    final Entrypoint entrypoint;

    EntrypointResolutionWarning(Entrypoint entrypoint) {
      super(Warning.SEVERE);
      this.entrypoint = entrypoint;
    }

    @Override
    public String getMsg() {
      return getClass().toString() + " : " + entrypoint;
    }

    public static EntrypointResolutionWarning create(Entrypoint entrypoint) {
      return new EntrypointResolutionWarning(entrypoint);
    }
  }

  protected void customInit() {
  }

  /**
   * Add constraints for a node.
   * @param monitor
   *
   * @return true iff any new constraints are added.
   */
  protected abstract boolean addConstraintsFromNode(CGNode n, IProgressMonitor monitor) throws CancelException;

  /**
   * Add constraints from newly discovered nodes. Note: the act of adding constraints may discover new nodes, so this routine is
   * iterative.
   *
   * @return true iff any new constraints are added.
   * @throws CancelException
   */
  public boolean addConstraintsFromNewNodes(IProgressMonitor monitor) throws CancelException {
    boolean result = false;
    while (!discoveredNodes.isEmpty()) {
      Iterator<CGNode> it = discoveredNodes.iterator();
      discoveredNodes = HashSetFactory.make();
      while (it.hasNext()) {
        CGNode n = it.next();
        result |= addConstraintsFromNode(n, monitor);
      }
    }
    return result;
  }

  /**
   * @return the PointerKey that acts as a representative for the class of pointers that includes the local variable identified by
   *         the value number parameter.
   */
  public PointerKey getPointerKeyForLocal(CGNode node, int valueNumber) {
    return pointerKeyFactory.getPointerKeyForLocal(node, valueNumber);
  }

  /**
   * @return the PointerKey that acts as a representative for the class of pointers that includes the local variable identified by
   *         the value number parameter.
   */
  public FilteredPointerKey getFilteredPointerKeyForLocal(CGNode node, int valueNumber, FilteredPointerKey.TypeFilter filter) {
    assert filter != null;
    return pointerKeyFactory.getFilteredPointerKeyForLocal(node, valueNumber, filter);
  }

  public FilteredPointerKey getFilteredPointerKeyForLocal(CGNode node, int valueNumber, IClass filter) {
    return getFilteredPointerKeyForLocal(node, valueNumber, new FilteredPointerKey.SingleClassFilter(filter));
  }

  public FilteredPointerKey getFilteredPointerKeyForLocal(CGNode node, int valueNumber, InstanceKey filter) {
    return getFilteredPointerKeyForLocal(node, valueNumber, new FilteredPointerKey.SingleInstanceFilter(filter));
  }

  /**
   * @return the PointerKey that acts as a representative for the class of pointers that includes the return value for a node
   */
  public PointerKey getPointerKeyForReturnValue(CGNode node) {
    return pointerKeyFactory.getPointerKeyForReturnValue(node);
  }

  /**
   * @return the PointerKey that acts as a representative for the class of pointers that includes the exceptional return value
   */
  public PointerKey getPointerKeyForExceptionalReturnValue(CGNode node) {
    return pointerKeyFactory.getPointerKeyForExceptionalReturnValue(node);
  }

  /**
   * @return the PointerKey that acts as a representative for the class of pointers that includes the contents of the static field
   */
  public PointerKey getPointerKeyForStaticField(IField f) {
    assert f != null : "null FieldReference";
    return pointerKeyFactory.getPointerKeyForStaticField(f);
  }

  /**
   * @return the PointerKey that acts as a representation for the class of pointers that includes the given instance field. null if
   *         there's some problem.
   * @throws IllegalArgumentException if I is null
   * @throws IllegalArgumentException if field is null
   */
  public PointerKey getPointerKeyForInstanceField(InstanceKey I, IField field) {
    if (field == null) {
      throw new IllegalArgumentException("field is null");
    }
    if (I == null) {
      throw new IllegalArgumentException("I is null");
    }
    IClass t = field.getDeclaringClass();
    IClass C = I.getConcreteType();
    if (!(C instanceof SyntheticClass)) {
      if (!getClassHierarchy().isSubclassOf(C, t)) {
        return null;
      }
    }

    return pointerKeyFactory.getPointerKeyForInstanceField(I, field);
  }

  /**
   * TODO: expand this API to differentiate between different array indices
   *
   * @param I an InstanceKey representing an abstract array
   * @return the PointerKey that acts as a representation for the class of pointers that includes the given array contents, or null
   *         if none found.
   * @throws IllegalArgumentException if I is null
   */
  public PointerKey getPointerKeyForArrayContents(InstanceKey I) {
    if (I == null) {
      throw new IllegalArgumentException("I is null");
    }
    IClass C = I.getConcreteType();
    if (!C.isArrayClass()) {
      assert false : "illegal arguments: " + I;
    }
    return pointerKeyFactory.getPointerKeyForArrayContents(I);
  }

  /**
   * Handle assign of a particular exception instance into an exception variable
   *
   * @param exceptionVar points-to set for a variable representing a caught exception
   * @param catchClasses set of TypeReferences that the exceptionVar may catch
   * @param e a particular exception instance
   */
  protected void assignInstanceToCatch(PointerKey exceptionVar, Set<IClass> catchClasses, InstanceKey e) {
    if (catches(catchClasses, e.getConcreteType(), cha)) {
      system.newConstraint(exceptionVar, e);
    }
  }

  /**
   * Generate a set of constraints to represent assignment to an exception variable in a catch clause. Note that we use
   * FilterOperator to filter out types that the exception handler doesn't catch.
   *
   * @param exceptionVar points-to set for a variable representing a caught exception
   * @param catchClasses set of TypeReferences that the exceptionVar may catch
   * @param e points-to-set representing a thrown exception that might be caught.
   */
  protected void addAssignmentsForCatchPointerKey(PointerKey exceptionVar, Set<IClass> catchClasses, PointerKey e) {
    if (DEBUG_GENERAL) {
      System.err.println("addAssignmentsForCatch: " + catchClasses);
    }
    // this is tricky ... we want to filter based on a number of classes ... so we can't
    // just used a FilteredPointerKey for the exceptionVar. Instead, we create a new
    // "typed local" for each catch class, and coalesce the results using
    // assignment
    for (IClass c : catchClasses) {
      if (c.getReference().equals(c.getClassLoader().getLanguage().getThrowableType())) {
        system.newConstraint(exceptionVar, assignOperator, e);
      } else {
        FilteredPointerKey typedException = TypedPointerKey.make(exceptionVar, c);
        system.newConstraint(typedException, filterOperator, e);
        system.newConstraint(exceptionVar, assignOperator, typedException);
      }
    }
  }

  /**
   * A warning for when we fail to resolve a call to an entrypoint
   */
  @SuppressWarnings("unused")
  private static class ExceptionLookupFailure extends Warning {

    final TypeReference t;

    ExceptionLookupFailure(TypeReference t) {
      super(Warning.SEVERE);
      this.t = t;
    }

    @Override
    public String getMsg() {
      return getClass().toString() + " : " + t;
    }

    public static ExceptionLookupFailure create(TypeReference t) {
      return new ExceptionLookupFailure(t);
    }
  }

  /**
   * A pointer key that delegates to an untyped variant, but adds a type filter
   */
  public final static class TypedPointerKey implements FilteredPointerKey {

    private final IClass type;

    private final PointerKey base;

    static TypedPointerKey make(PointerKey base, IClass type) {
      assert type != null;
      return new TypedPointerKey(base, type);
    }

    private TypedPointerKey(PointerKey base, IClass type) {
      this.type = type;
      this.base = base;
      assert type != null;
      assert !(type instanceof FilteredPointerKey);
    }

    /*
     * @see com.ibm.wala.ipa.callgraph.propagation.FilteredPointerKey#getTypeFilter()
     */
    @Override
    public TypeFilter getTypeFilter() {
      return new SingleClassFilter(type);
    }

    @Override
    public boolean equals(Object obj) {
      // instanceof is OK because this class is final
      if (obj instanceof TypedPointerKey) {
        TypedPointerKey other = (TypedPointerKey) obj;
        return type.equals(other.type) && base.equals(other.base);
      } else {
        return false;
      }
    }

    @Override
    public int hashCode() {
      return 67931 * base.hashCode() + type.hashCode();
    }

    @Override
    public String toString() {
      return "{ " + base + " type: " + type + "}";
    }

    public PointerKey getBase() {
      return base;
    }
  }

  /**
   * @param catchClasses Set of TypeReference
   * @param klass an Exception Class
   * @return true iff klass is a subclass of some element of the Set
   * @throws IllegalArgumentException if catchClasses is null
   */
  public static boolean catches(Set<IClass> catchClasses, IClass klass, IClassHierarchy cha) {
    if (catchClasses == null) {
      throw new IllegalArgumentException("catchClasses is null");
    }
    // quick shortcut
    if (catchClasses.size() == 1) {
      IClass c = catchClasses.iterator().next();
      if (c != null && c.getReference().equals(TypeReference.JavaLangThread)) {
        return true;
      }
    }
    for (IClass c : catchClasses) {
      if (c != null && cha.isAssignableFrom(c, klass)) {
        return true;
      }
    }
    return false;
  }

  public static boolean representsNullType(InstanceKey key) throws IllegalArgumentException {
    if (key == null) {
      throw new IllegalArgumentException("key == null");
    }
    IClass cls = key.getConcreteType();
    Language L = cls.getClassLoader().getLanguage();
    return L.isNullType(cls.getReference());
  }

  /**
   * The FilterOperator is a filtered set-union. i.e. the LHS is `unioned' with the RHS, but filtered by the set associated with
   * this operator instance. The filter is the set of InstanceKeys corresponding to the target type of this cast. This is still
   * monotonic.
   *
   * LHS U= (RHS n k)
   *
   *
   * Unary op: <lhs>:= Cast_k( <rhs>)
   *
   * (Again, technically a binary op -- see note for Assign)
   *
   * TODO: these need to be canonicalized.
   *
   */
  public class FilterOperator extends UnaryOperator<PointsToSetVariable> implements IPointerOperator {

    protected FilterOperator() {
    }

    /*
     * @see com.ibm.wala.dataflow.UnaryOperator#evaluate(com.ibm.wala.dataflow.IVariable, com.ibm.wala.dataflow.IVariable)
     */
    @Override
    public byte evaluate(PointsToSetVariable lhs, PointsToSetVariable rhs) {

      FilteredPointerKey pk = (FilteredPointerKey) lhs.getPointerKey();

      if (DEBUG_FILTER) {
        String S = "EVAL Filter " + lhs.getPointerKey() + " " + rhs.getPointerKey();
        S += "\nEVAL      " + lhs + " " + rhs;
        System.err.println(S);
      }
      if (rhs.size() == 0) {
        return NOT_CHANGED;
      }

      boolean changed = false;
      FilteredPointerKey.TypeFilter filter = pk.getTypeFilter();

      int orl = lhs.getOrderNumber();
      int orr = rhs.getOrderNumber();
      PointsToSetVariable first;
      PointsToSetVariable sec;
      if(orl<orr){
        first = lhs;
        sec = rhs;
      }else{
        first =rhs;
        sec = lhs;
      }

      synchronized(first){
        synchronized (sec) {
          changed = filter.addFiltered(system, lhs, rhs);
        }
      }
      //      changed = filter.addFiltered(system, lhs, rhs);

      if (DEBUG_FILTER) {
        System.err.println("RESULT " + lhs + (changed ? " (changed)" : ""));
      }

      return changed ? CHANGED : NOT_CHANGED;
    }

    /*
     * @see com.ibm.wala.ipa.callgraph.propagation.IPointerOperator#isComplex()
     */
    @Override
    public boolean isComplex() {
      return false;
    }

    @Override
    public String toString() {
      return "Filter ";
    }

    @Override
    public boolean equals(Object obj) {
      // these objects are canonicalized for the duration of a solve
      return this == obj;
    }

    @Override
    public int hashCode() {
      return 88651;
    }

    @Override
    public byte evaluateDel(PointsToSetVariable lhs, PointsToSetVariable rhs) {
      FilteredPointerKey pk = (FilteredPointerKey) lhs.getPointerKey();

      if (DEBUG_FILTER) {
        String S = "DEL EVAL Filter " + lhs.getPointerKey() + " " + rhs.getPointerKey();
        S += "\nEVAL      " + lhs + " " + rhs;
        System.err.println(S);

      }
      if(rhs.getValue() == null){//added
        return NOT_CHANGED;
      }
      if (rhs.size() == 0) {
        return NOT_CHANGED;
      }

      boolean changed = false;
      //*** clear lhs ??? wrong
      FilteredPointerKey.TypeFilter filter = pk.getTypeFilter();
      changed = filter.delFiltered(system, lhs, rhs);
      //      FilteredPointerKey.TypeFilter filter = pk.getTypeFilter();
      //      changed = filter.addFiltered(system, lhs, rhs);
      //
      //      if (DEBUG_FILTER) {
      //        System.err.println("RESULT " + lhs + (changed ? " (changed)" : ""));
      //      }
      return changed ? CHANGED : NOT_CHANGED;
    }

    //for parallel
    public byte evaluateDel(PointsToSetVariable lhs, MutableSharedBitVectorIntSet set) {
      FilteredPointerKey pk = (FilteredPointerKey) lhs.getPointerKey();

      if(set == null){//added
        return NOT_CHANGED;
      }
      if (set.size() == 0) {
        return NOT_CHANGED;
      }

      boolean changed = false;
      //*** clear lhs ??? wrong
      FilteredPointerKey.TypeFilter filter = pk.getTypeFilter();
      changed = filter.delFiltered(system, lhs, set);
      //      FilteredPointerKey.TypeFilter filter = pk.getTypeFilter();
      //      changed = filter.addFiltered(system, lhs, rhs);
      //
      //      if (DEBUG_FILTER) {
      //        System.err.println("RESULT " + lhs + (changed ? " (changed)" : ""));
      //      }
      return changed ? CHANGED : NOT_CHANGED;
    }

  }


  public IClassHierarchy getClassHierarchy() {
    return cha;
  }

  public AnalysisOptions getOptions() {
    return options;
  }

  public IClass getJavaLangObject() {
    return JAVA_LANG_OBJECT;
  }

  public ExplicitCallGraph getCallGraph() {
    return callGraph;
  }

  /**
   * Subclasses must register the context interpreter before building a call graph.
   */
  public void setContextInterpreter(SSAContextInterpreter interpreter) {
    contextInterpreter = interpreter;
    callGraph.setInterpreter(interpreter);
  }

  /*
   * @see com.ibm.detox.ipa.callgraph.CallGraphBuilder#getPointerAnalysis()
   */
  @Override
  public PointerAnalysis<InstanceKey> getPointerAnalysis() {
    return system.extractPointerAnalysis(this);
  }

  public PropagationSystem getPropagationSystem() {
    return system;
  }

  public PointerKeyFactory getPointerKeyFactory() {
    return pointerKeyFactory;
  }

  /** BEGIN Custom change: setter for pointerkey factory */
  public void setPointerKeyFactory(PointerKeyFactory pkFact) {
    pointerKeyFactory = pkFact;
  }

  /** END Custom change: setter for pointerkey factory */
  public RTAContextInterpreter getContextInterpreter() {
    return contextInterpreter;
  }

  /**
   * @param caller the caller node
   * @param iKey an abstraction of the receiver of the call (or null if not applicable)
   * @return the CGNode to which this particular call should dispatch.
   */
  protected CGNode getTargetForCall(CGNode caller, CallSiteReference site, IClass recv, InstanceKey iKey[]) {

    IMethod targetMethod = options.getMethodTargetSelector().getCalleeTarget(caller, site, recv);

    // this most likely indicates an exclusion at work; the target selector
    // should have issued a warning
    if (targetMethod == null || targetMethod.isAbstract()) {
      return null;
    }
    Context targetContext = contextSelector.getCalleeTarget(caller, site, targetMethod, iKey);

    if (targetContext instanceof IllegalArgumentExceptionContext) {
      return null;
    }
    try {
      return getCallGraph().findOrCreateNode(targetMethod, targetContext);
    } catch (CancelException e) {
      return null;
    }
  }

  /**
   * @return the context selector for this call graph builder
   */
  public ContextSelector getContextSelector() {
    return contextSelector;
  }

  public void setContextSelector(ContextSelector selector) {
    contextSelector = selector;
  }

  public InstanceKeyFactory getInstanceKeys() {
    return instanceKeyFactory;
  }

  public void setInstanceKeys(InstanceKeyFactory keys) {
    this.instanceKeyFactory = keys;
  }

  /**
   * @return the InstanceKey that acts as a representative for the class of objects that includes objects allocated at the given new
   *         instruction in the given node
   */
  public InstanceKey getInstanceKeyForAllocation(CGNode node, NewSiteReference allocation) {
    return instanceKeyFactory.getInstanceKeyForAllocation(node, allocation);
  }

  /**
   * @param dim the dimension of the array whose instance we would like to model. dim == 0 represents the first dimension, e.g., the
   *          [Object; instances in [[Object; e.g., the [[Object; instances in [[[Object; dim == 1 represents the second dimension,
   *          e.g., the [Object instances in [[[Object;
   * @return the InstanceKey that acts as a representative for the class of array contents objects that includes objects allocated
   *         at the given new instruction in the given node
   */
  public InstanceKey getInstanceKeyForMultiNewArray(CGNode node, NewSiteReference allocation, int dim) {
    return instanceKeyFactory.getInstanceKeyForMultiNewArray(node, allocation, dim);
  }

  public <T> InstanceKey getInstanceKeyForConstant(TypeReference type, T S) {
    return instanceKeyFactory.getInstanceKeyForConstant(type, S);
  }

  public InstanceKey getInstanceKeyForMetadataObject(Object obj, TypeReference objType) {
    return instanceKeyFactory.getInstanceKeyForMetadataObject(obj, objType);
  }

  public boolean haveAlreadyVisited(CGNode node) {
    return alreadyVisited.contains(node);
  }

  protected void markAlreadyVisited(CGNode node) {
    alreadyVisited.add(node);
  }

  /**
   * record that we've discovered a node
   */
  public void markDiscovered(CGNode node) {
    discoveredNodes.add(node);
  }

  //JEFF - change to public
  // only used by echo
  public void markChanged(CGNode node) {
    alreadyVisited.remove(node);
    discoveredNodes.add(node);

    try {
      solver.solve(null);
    } catch (IllegalArgumentException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (CancelException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  protected boolean wasChanged(CGNode node) {
    return discoveredNodes.contains(node) && !alreadyVisited.contains(node);
  }

  /**
   * Binary op: <dummy>:= ArrayLoad( &lt;arrayref>) Side effect: Creates new equations.
   */
  public final class ArrayLoadOperator extends UnarySideEffect implements IPointerOperator {
    protected final MutableIntSet priorInstances = rememberGetPutHistory ? IntSetUtil.make() : null;

    @Override
    public String toString() {
      return "ArrayLoad";
    }

    public ArrayLoadOperator(PointsToSetVariable def) {
      super(def);
      system.registerFixedSet(def, this);
    }

    @Override
    public byte evaluate(PointsToSetVariable rhs) {
      if (DEBUG_ARRAY_LOAD) {
        PointsToSetVariable def = getFixedSet();
        String S = "EVAL ArrayLoad " + rhs.getPointerKey() + " " + def.getPointerKey();
        System.err.println(S);
        System.err.println("EVAL ArrayLoad " + def + " " + rhs);
        if (priorInstances != null) {
          System.err.println("prior instances: " + priorInstances + " " + priorInstances.getClass());
        }
      }

      if (rhs.size() == 0) {
        return NOT_CHANGED;
      }
      final PointerKey object = rhs.getPointerKey();

      PointsToSetVariable def = getFixedSet();
      final PointerKey dVal = def.getPointerKey();

      final MutableBoolean sideEffect = new MutableBoolean();
      IntSetAction action = new IntSetAction() {
        @Override
        public void act(int i) {
          InstanceKey I = system.getInstanceKey(i);
          if (!I.getConcreteType().isArrayClass()) {
            return;
          }
          TypeReference C = I.getConcreteType().getReference().getArrayElementType();
          if (C.isPrimitiveType()) {
            return;
          }
          PointerKey p = getPointerKeyForArrayContents(I);
          if (p == null) {
            return;
          }

          if (DEBUG_ARRAY_LOAD) {
            System.err.println("ArrayLoad add assign: " + dVal + " " + p);
          }
          //          sideEffect.b |= system.newFieldRead(dVal, assignOperator, p, object);
          sideEffect.b |= system.newConstraint(dVal, assignOperator, p);
        }
      };

      final ArrayList<PointsToSetVariable> rhss = new ArrayList<>();
      IntSetAction action2 = new IntSetAction() {
        @Override
        public void act(int i) {
          InstanceKey I = system.getInstanceKey(i);
          if (!I.getConcreteType().isArrayClass()) {
            return;
          }
          TypeReference C = I.getConcreteType().getReference().getArrayElementType();
          if (C.isPrimitiveType()) {
            return;
          }
          PointerKey p = getPointerKeyForArrayContents(I);
          if (p == null) {
            return;
          }

          if (DEBUG_ARRAY_LOAD) {
            System.err.println("ArrayLoad add assign: " + dVal + " " + p);
          }
          sideEffect.b |= system.newConstraint(dVal, assignOperator, p);
          PointsToSetVariable rhs = system.findOrCreatePointsToSet(p);
          if(rhs.getValue() != null)
            rhss.add(rhs);
        }
      };

      MutableIntSet value = rhs.getValue();
      if(system.isChange && rhss.size() > 1000){
        if (priorInstances != null) {
          value.foreachExcluding(priorInstances, action2);
          priorInstances.addAll(rhs.getValue());
        } else {
          value.foreach(action2);
        }
        if(rhss.size() != 0)
          system.addConstraintHasMultiR(dVal, assignOperator, rhss);//change
      }else{
        if (priorInstances != null) {
          value.foreachExcluding(priorInstances, action);
          priorInstances.addAll(rhs.getValue());
        } else {
          value.foreach(action);
        }
      }
      byte sideEffectMask = sideEffect.b ? (byte) SIDE_EFFECT_MASK : 0;
      return (byte) (NOT_CHANGED | sideEffectMask);
    }

    @Override
    public int hashCode() {
      return 9871 + super.hashCode();
    }

    @Override
    public boolean equals(Object o) {
      return super.equals(o);
    }

    @Override
    protected boolean isLoadOperator() {
      return true;
    }

    /*
     * @see com.ibm.wala.ipa.callgraph.propagation.IPointerOperator#isComplex()
     */
    @Override
    public boolean isComplex() {
      return true;
    }

    @Override
    public byte evaluateDel(PointsToSetVariable rhs) {//bz
      if (DEBUG_ARRAY_LOAD) {
        PointsToSetVariable def = getFixedSet();
        String S = "DEL EVAL ArrayLoad " + rhs.getPointerKey() + " " + def.getPointerKey();
        System.err.println(S);
        System.err.println("DEl EVAL ArrayLoad " + def + " " + rhs);
        if (priorInstances != null) {
          System.err.println("prior instances: " + priorInstances + " " + priorInstances.getClass());
        }
      }

      if (rhs.size() == 0) {
        return NOT_CHANGED;
      }
      final PointerKey object = rhs.getPointerKey();

      PointsToSetVariable def = getFixedSet();
      final PointerKey dVal = def.getPointerKey();

      final MutableBoolean sideEffect_del = new MutableBoolean();
      final MutableIntSet delset = IntSetUtil.getDefaultIntSetFactory().make();
      final ArrayList<PointsToSetVariable> rhss = new ArrayList<>();
      IntSetAction action = new IntSetAction() {
        @Override
        public void act(int i) {
          InstanceKey I = system.getInstanceKey(i);
          if (!I.getConcreteType().isArrayClass()) {
            return;
          }
          TypeReference C = I.getConcreteType().getReference().getArrayElementType();
          if (C.isPrimitiveType()) {
            return;
          }
          PointerKey p = getPointerKeyForArrayContents(I);
          if (p == null) {
            return;
          }

          if (DEBUG_ARRAY_LOAD) {
            System.err.println("ArrayLoad del assign: " + dVal + " " + p);
          }
          //          sideEffect_del.b |= system.delConstraint(dVal, assignOperator, p);
          //          System.out.println("rhs : "+ p.toString());
          PointsToSetVariable ptv = system.findOrCreatePointsToSet(p);
          if(ptv.getValue() != null){
            delset.addAll(ptv.getValue());
            rhss.add(ptv);
          }
        }
      };
      rhs.getValue().foreach(action);
      if(rhss.size() != 0)
        sideEffect_del.b |= system.delConstraintHasMultiR(dVal, assignOperator, rhss, delset, rhs);
      priorInstances.foreach(action);
      priorInstances.clear();
      delset.clear();

      byte sideEffectMask = sideEffect_del.b ? (byte) SIDE_EFFECT_MASK : 0;
      return (byte) (NOT_CHANGED | sideEffectMask);
    }

    //jeff's idea
    //    @Override
    //    public byte evaluateDel(PointsToSetVariable rhs) {//bz
    //      if (DEBUG_ARRAY_LOAD) {
    //        PointsToSetVariable def = getFixedSet();
    //        String S = "DEL EVAL ArrayLoad " + rhs.getPointerKey() + " " + def.getPointerKey();
    //        System.err.println(S);
    //        System.err.println("DEl EVAL ArrayLoad " + def + " " + rhs);
    //        if (priorInstances != null) {
    //          System.err.println("prior instances: " + priorInstances + " " + priorInstances.getClass());
    //        }
    //      }
    //
    //      if (rhs.size() == 0) {
    //        return NOT_CHANGED;
    //      }
    //      final PointerKey object = rhs.getPointerKey();
    //
    //      PointsToSetVariable def = getFixedSet();
    //      final PointerKey dVal = def.getPointerKey();
    //
    //      final MutableBoolean sideEffect_del = new MutableBoolean();
    //      final ArrayList<PointsToSetVariable> rhss = new ArrayList<>();
    //      IntSetAction action = new IntSetAction() {
    //        @Override
    //        public void act(int i) {
    //          InstanceKey I = system.getInstanceKey(i);
    //          if (!I.getConcreteType().isArrayClass()) {
    //            return;
    //          }
    //          TypeReference C = I.getConcreteType().getReference().getArrayElementType();
    //          if (C.isPrimitiveType()) {
    //            return;
    //          }
    //          PointerKey p = getPointerKeyForArrayContents(I);
    //          if (p == null) {
    //            return;
    //          }
    //
    //          if (DEBUG_ARRAY_LOAD) {
    //            System.err.println("ArrayLoad del assign: " + dVal + " " + p);
    //          }
    ////          sideEffect_del.b |= system.delConstraint(dVal, assignOperator, p);
    ////          System.out.println("rhs : "+ p.toString());
    //          PointsToSetVariable ptv = system.findOrCreatePointsToSet(p);
    //          if(ptv.getValue() != null){
    //            rhss.add(ptv);
    //          }
    //        }
    //      };
    //      rhs.getValue().foreach(action);
    //      priorInstances.foreach(action);
    //      //start to parallel
    //      if(!rhss.isEmpty()){
    //        system.anotherWay(def, assignOperator, rhss, false);
    //      }
    //      priorInstances.clear();
    ////      byte sideEffectMask = sideEffect_del.b ? (byte) SIDE_EFFECT_MASK : 0;
    ////      return (byte) (NOT_CHANGED | sideEffectMask);
    //      return (byte) SIDE_EFFECT_MASK;
    //    }
  }

  /**
   * Binary op: <dummy>:= ArrayStore( &lt;arrayref>) Side effect: Creates new equations.
   */
  public final class ArrayStoreOperator extends UnarySideEffect implements IPointerOperator {
    @Override
    public String toString() {
      return "ArrayStore";
    }

    public ArrayStoreOperator(PointsToSetVariable val) {
      super(val);
      system.registerFixedSet(val, this);
    }

    @Override
    public byte evaluate(PointsToSetVariable rhs) {
      if (DEBUG_ARRAY_STORE) {
        PointsToSetVariable val = getFixedSet();
        String S = "EVAL ArrayStore " + rhs.getPointerKey() + " " + val.getPointerKey();
        System.err.println(S);
        System.err.println("EVAL ArrayStore " + rhs + " " + getFixedSet());
      }

      if (rhs.size() == 0) {
        return NOT_CHANGED;
      }
      PointerKey object = rhs.getPointerKey();

      PointsToSetVariable val = getFixedSet();
      PointerKey pVal = val.getPointerKey();

      List<InstanceKey> instances = system.getInstances(rhs.getValue());
      boolean sideEffect = false;

//      if(system.isChange){
//        if(instances.size() < 1000){
//          for (Iterator<InstanceKey> it = instances.iterator(); it.hasNext();) {
//            InstanceKey I = it.next();
//            if (!I.getConcreteType().isArrayClass()) {
//              continue;
//            }
//            if (I instanceof ZeroLengthArrayInNode) {
//              continue;
//            }
//            TypeReference C = I.getConcreteType().getReference().getArrayElementType();
//            if (C.isPrimitiveType()) {
//              continue;
//            }
//            IClass contents = getClassHierarchy().lookupClass(C);
//            if (contents == null) {
//              assert false : "null type for " + C + " " + I.getConcreteType();
//            }
//            PointerKey p = getPointerKeyForArrayContents(I);
//            if (DEBUG_ARRAY_STORE) {
//              System.err.println("ArrayStore add filtered-assign: " + p + " " + pVal);
//            }
//
//            // note that the following is idempotent
//            if (isJavaLangObject(contents)) {
//              //              sideEffect |= system.newFieldWrite(p, assignOperator, pVal, object);
//              sideEffect |= system.newConstraint(p, assignOperator, pVal);
//            } else {
//              //              sideEffect |= system.newFieldWrite(p, filterOperator, pVal, object);
//              sideEffect|= system.newConstraint(p, filterOperator, pVal);
//            }
//          }
//        }else {
//          if(val.getValue() != null){
//            instCounter++;
//            ArrayList<PointsToSetVariable> lhssFilter = new ArrayList<>();
//            ArrayList<PointsToSetVariable> lhssAssign = new ArrayList<>();
//
//            for (Iterator<InstanceKey> it = instances.iterator(); it.hasNext();) {
//              InstanceKey I = it.next();
//              if (!I.getConcreteType().isArrayClass()) {
//                continue;
//              }
//              if (I instanceof ZeroLengthArrayInNode) {
//                continue;
//              }
//              TypeReference C = I.getConcreteType().getReference().getArrayElementType();
//              if (C.isPrimitiveType()) {
//                continue;
//              }
//              IClass contents = getClassHierarchy().lookupClass(C);
//              if (contents == null) {
//                assert false : "null type for " + C + " " + I.getConcreteType();
//              }
//              PointerKey p = getPointerKeyForArrayContents(I);
//              // note that the following is idempotent
//              PointsToSetVariable pptv = system.findOrCreatePointsToSet(p);
//              if (isJavaLangObject(contents)) {
//                lhssAssign.add(pptv);
//              } else {
//                lhssFilter.add(pptv);
//              }
//            }
//            MutableIntSet targets = IntSetUtil.makeMutableCopy(val.getValue());
//            system.addConstraintHasMultiLSeperate(lhssAssign, lhssFilter, assignOperator, filterOperator, val, targets); //sideEffectMask
//          }
//        }
//      }else{
//        for (Iterator<InstanceKey> it = instances.iterator(); it.hasNext();) {
//          InstanceKey I = it.next();
//          if (!I.getConcreteType().isArrayClass()) {
//            continue;
//          }
//          if (I instanceof ZeroLengthArrayInNode) {
//            continue;
//          }
//          TypeReference C = I.getConcreteType().getReference().getArrayElementType();
//          if (C.isPrimitiveType()) {
//            continue;
//          }
//          IClass contents = getClassHierarchy().lookupClass(C);
//          if (contents == null) {
//            assert false : "null type for " + C + " " + I.getConcreteType();
//          }
//          PointerKey p = getPointerKeyForArrayContents(I);
//          if (DEBUG_ARRAY_STORE) {
//            System.err.println("ArrayStore add filtered-assign: " + p + " " + pVal);
//          }
//
//          // note that the following is idempotent
//          if (isJavaLangObject(contents)) {
//            //              sideEffect |= system.newFieldWrite(p, assignOperator, pVal, object);
//            sideEffect |= system.newConstraint(p, assignOperator, pVal);
//          } else {
//            //              sideEffect |= system.newFieldWrite(p, filterOperator, pVal, object);
//            sideEffect|= system.newConstraint(p, filterOperator, pVal);
//          }
//        }
//      }
      for (Iterator<InstanceKey> it = instances.iterator(); it.hasNext();) {
        InstanceKey I = it.next();
        if (!I.getConcreteType().isArrayClass()) {
          continue;
        }
        if (I instanceof ZeroLengthArrayInNode) {
          continue;
        }
        TypeReference C = I.getConcreteType().getReference().getArrayElementType();
        if (C.isPrimitiveType()) {
          continue;
        }
        IClass contents = getClassHierarchy().lookupClass(C);
        if (contents == null) {
          assert false : "null type for " + C + " " + I.getConcreteType();
        }
        PointerKey p = getPointerKeyForArrayContents(I);
        if (DEBUG_ARRAY_STORE) {
          System.err.println("ArrayStore add filtered-assign: " + p + " " + pVal);
        }

        // note that the following is idempotent
        if (isJavaLangObject(contents)) {
          sideEffect |= system.newFieldWrite(p, assignOperator, pVal, object);
        } else {
          sideEffect |= system.newFieldWrite(p, filterOperator, pVal, object);
        }
      }
      byte sideEffectMask = sideEffect ? (byte) SIDE_EFFECT_MASK : 0;
      return (byte) (NOT_CHANGED | sideEffectMask);
    }

    @Override
    public int hashCode() {
      return 9859 + super.hashCode();
    }

    @Override
    public boolean isComplex() {
      return true;
    }

    @Override
    public boolean equals(Object o) {
      return super.equals(o);
    }

    @Override
    protected boolean isLoadOperator() {
      return false;
    }

    @Override
    public byte evaluateDel(PointsToSetVariable rhs) {
      if (DEBUG_ARRAY_STORE) {
        PointsToSetVariable val = getFixedSet();
        String S = "DEL EVAL ArrayStore " + rhs.getPointerKey() + " " + val.getPointerKey();
        System.err.println(S);
        System.err.println("DEL EVAL ArrayStore " + rhs + " " + getFixedSet());
      }

      if (rhs.size() == 0) {
        return NOT_CHANGED;
      }
      PointerKey object = rhs.getPointerKey();

      PointsToSetVariable val = getFixedSet();
      PointerKey pVal = val.getPointerKey();

      List<InstanceKey> instances = system.getInstances(rhs.getValue());
      int size = instances.size();
      boolean sideEffect_del = false;
//      if(size < 10){
        for (Iterator<InstanceKey> it = instances.iterator(); it.hasNext();) {
          InstanceKey I = it.next();
          if (!I.getConcreteType().isArrayClass()) {
            continue;
          }
          if (I instanceof ZeroLengthArrayInNode) {
            continue;
          }
          TypeReference C = I.getConcreteType().getReference().getArrayElementType();
          if (C.isPrimitiveType()) {
            continue;
          }
          IClass contents = getClassHierarchy().lookupClass(C);
          if (contents == null) {
            assert false : "null type for " + C + " " + I.getConcreteType();
          }
          PointerKey p = getPointerKeyForArrayContents(I);
          if (DEBUG_ARRAY_STORE) {
            System.err.println("ArrayStore del filtered-assign: " + p + " " + pVal);
          }

          // note that the following is idempotent
          if (isJavaLangObject(contents)) {
            sideEffect_del |= system.delConstraint(p, assignOperator, pVal);
          } else {
            sideEffect_del |= system.delConstraint(p, filterOperator, pVal);
          }
        }
//      }else{
//        if(val.getValue() != null){
//          ArrayList<PointsToSetVariable> lhss = new ArrayList<>();
//          for (Iterator<InstanceKey> it = instances.iterator(); it.hasNext();) {
//            InstanceKey I = it.next();
//            if (!I.getConcreteType().isArrayClass()) {
//              continue;
//            }
//            if (I instanceof ZeroLengthArrayInNode) {
//              continue;
//            }
//            TypeReference C = I.getConcreteType().getReference().getArrayElementType();
//            if (C.isPrimitiveType()) {
//              continue;
//            }
//            IClass contents = getClassHierarchy().lookupClass(C);
//            if (contents == null) {
//              assert false : "null type for " + C + " " + I.getConcreteType();
//            }
//            PointerKey p = getPointerKeyForArrayContents(I);
//            // note that the following is idempotent
//            PointsToSetVariable pptv = system.findOrCreatePointsToSet(p);
//            lhss.add(pptv);
//          }
//          MutableIntSet targets = IntSetUtil.makeMutableCopy(val.getValue());
//          system.delConstraintHasMultiL(lhss, assignOperator, val, targets); //sideEffectMask
//        }
//      }
      byte sideEffectMask = sideEffect_del ? (byte) SIDE_EFFECT_MASK : 0;
      return (byte) (NOT_CHANGED | sideEffectMask);
    }
  }

  /**
   * Binary op: <dummy>:= GetField( <ref>) Side effect: Creates new equations.
   */
  public class GetFieldOperator extends UnarySideEffect implements IPointerOperator {
    private final IField field;

    protected final MutableIntSet priorInstances = rememberGetPutHistory ? IntSetUtil.make() : null;

    public GetFieldOperator(IField field, PointsToSetVariable def) {
      super(def);
      this.field = field;
      system.registerFixedSet(def, this);
    }

    @Override
    public String toString() {
      return "GetField " + getField() + "," + getFixedSet().getPointerKey();
    }

    @Override
    public byte evaluate(PointsToSetVariable rhs) {
      if (DEBUG_GET) {
        String S = "EVAL GetField " + getField() + " " + getFixedSet().getPointerKey() + " " + rhs.getPointerKey() + getFixedSet()
        + " " + rhs;
        System.err.println(S);
      }

      PointsToSetVariable ref = rhs;
      if (ref.size() == 0) {
        return NOT_CHANGED;
      }
      final PointerKey object = ref.getPointerKey();
      PointsToSetVariable def = getFixedSet();
      final PointerKey dVal = def.getPointerKey();

      IntSet value = filterInstances(ref.getValue());
      if (DEBUG_GET) {
        System.err.println("filtered value: " + value + " " + value.getClass());
        if (priorInstances != null) {
          System.err.println("prior instances: " + priorInstances + " " + priorInstances.getClass());
        }
      }
      final MutableBoolean sideEffect = new MutableBoolean();
      IntSetAction action = new IntSetAction() {
        @Override
        public void act(int i) {
          InstanceKey I = system.getInstanceKey(i);
          if (!representsNullType(I)) {
            PointerKey p = getPointerKeyForInstanceField(I, getField());

            if (p != null) {
              if (DEBUG_GET) {
                String S = "Getfield add constraint " + dVal + " " + p;
                System.err.println(S);
              }
              //              sideEffect.b |= system.newFieldRead(dVal, assignOperator, p, object);
              sideEffect.b |= system.newConstraint(dVal, assignOperator, p);
            }
          }
        }
      };

      //      boolean changed = false;
      //      int orl = def.getOrderNumber();
      //      int orr = rhs.getOrderNumber();
      //      PointsToSetVariable first;
      //      PointsToSetVariable sec;
      //      if(orl<orr){
      //        first = def;
      //        sec = rhs;
      //      }else{
      //        first =rhs;
      //        sec = def;
      //      }

      final ArrayList<PointsToSetVariable> rhss = new ArrayList<>();
      IntSetAction action2 = new IntSetAction() {
        @Override
        public void act(int i) {
          InstanceKey I = system.getInstanceKey(i);
          if (!representsNullType(I)) {
            PointerKey p = getPointerKeyForInstanceField(I, getField());

            if (p != null) {
              if (DEBUG_GET) {
                String S = "Getfield add constraint " + dVal + " " + p;
                System.err.println(S);
              }
              rhss.add(system.findOrCreatePointsToSet(p));
            }
          }
        }
      };

      int size = rhs.getValue().size();
      //      synchronized(first){
      //        synchronized (sec) {
      if(system.isChange && rhss.size() > 600){
        if (priorInstances != null) {
          value.foreachExcluding(priorInstances, action2);
          priorInstances.addAll(value);
        } else {
          value.foreach(action2);
        }
      }else{
        if (priorInstances != null) {
          value.foreachExcluding(priorInstances, action);
          priorInstances.addAll(value);
        } else {
          value.foreach(action);
        }
      }
      //        }
      //      }
      byte sideEffectMask = sideEffect.b ? (byte) SIDE_EFFECT_MASK : 0;
      return (byte) (NOT_CHANGED | sideEffectMask);
    }

    /**
     * Subclasses can override as needed
     */
    protected IntSet filterInstances(IntSet value) {
      return value;
    }

    @Override
    public int hashCode() {
      return 9857 * getField().hashCode() + getFixedSet().hashCode();
    }

    @Override
    public boolean equals(Object o) {
      if (o instanceof GetFieldOperator) {
        GetFieldOperator other = (GetFieldOperator) o;
        return getField().equals(other.getField()) && getFixedSet().equals(other.getFixedSet());
      } else {
        return false;
      }
    }

    protected IField getField() {
      return field;
    }

    @Override
    protected boolean isLoadOperator() {
      return true;
    }

    /*
     * @see com.ibm.wala.ipa.callgraph.propagation.IPointerOperator#isComplex()
     */
    @Override
    public boolean isComplex() {
      return true;
    }

    @Override
    public byte evaluateDel(PointsToSetVariable rhs) {
      if (DEBUG_GET) {
        //--- fixedSet is def
        String S = "DEL EVAL GetField " + getField() + " " + getFixedSet().getPointerKey() + " " + rhs.getPointerKey() + getFixedSet()
        + " " + rhs;
        System.err.println(S);
      }

      PointsToSetVariable ref = rhs;
      if (ref.size() == 0) {
        return NOT_CHANGED;
      }
      final PointerKey object = ref.getPointerKey();
      PointsToSetVariable def = getFixedSet();
      final PointerKey dVal = def.getPointerKey();
      //~~~ did not implement filter part
      IntSet value = filterInstances(ref.getValue());
      if (DEBUG_GET) {
        System.err.println("filtered value: " + value + " " + value.getClass());
        if (priorInstances != null) {
          System.err.println("prior instances: " + priorInstances + " " + priorInstances.getClass());
        }
      }
      final MutableBoolean sideEffect_del = new MutableBoolean();
      final MutableIntSet delset = IntSetUtil.getDefaultIntSetFactory().make();
      final ArrayList<PointsToSetVariable> rhss = new ArrayList<>();
      IntSetAction action = new IntSetAction() {
        @Override
        public void act(int i) {
          InstanceKey I = system.getInstanceKey(i);
          if (!representsNullType(I)) {
            //--- this for getField is the GetFieldOperator
            PointerKey p = getPointerKeyForInstanceField(I, getField());

            if (p != null) {
              if (DEBUG_GET) {
                String S = "Getfield del constraint " + dVal + " " + p;
                System.err.println(S);
              }
              //              sideEffect_del.b |= system.delConstraint(dVal, assignOperator, p);
              PointsToSetVariable ptv = system.findOrCreatePointsToSet(p);
              if(ptv.getValue() != null){
                delset.addAll(ptv.getValue());
                rhss.add(ptv);
              }
            }
          }
        }
      };
      //*** always do it for all instance
      value.foreach(action);
      priorInstances.foreach(action);
      if(rhss.size() != 0)
        sideEffect_del.b |= system.delConstraintHasMultiR(dVal, assignOperator, rhss, delset, ref);
      //--- remove all priorInstance
      priorInstances.clear();
      delset.clear();

      byte sideEffectMask = sideEffect_del.b ? (byte) SIDE_EFFECT_MASK : 0;
      return (byte) (NOT_CHANGED | sideEffectMask);
    }

    //jeff's idea
    //    @Override
    //    public byte evaluateDel(PointsToSetVariable rhs) {
    //      if (DEBUG_GET) {
    //        //--- fixedSet is def
    //        String S = "DEL EVAL GetField " + getField() + " " + getFixedSet().getPointerKey() + " " + rhs.getPointerKey() + getFixedSet()
    //            + " " + rhs;
    //        System.err.println(S);
    //      }
    //
    //      PointsToSetVariable ref = rhs;
    //      if (ref.size() == 0) {
    //        return NOT_CHANGED;
    //      }
    //      final PointerKey object = ref.getPointerKey();
    //      PointsToSetVariable def = getFixedSet();
    //      final PointerKey dVal = def.getPointerKey();
    //      //~~~ did not implement filter part
    //      IntSet value = filterInstances(ref.getValue());
    //      if (DEBUG_GET) {
    //        System.err.println("filtered value: " + value + " " + value.getClass());
    //        if (priorInstances != null) {
    //          System.err.println("prior instances: " + priorInstances + " " + priorInstances.getClass());
    //        }
    //      }
    //      final MutableBoolean sideEffect_del = new MutableBoolean();
    //      final MutableIntSet delset = IntSetUtil.getDefaultIntSetFactory().make();
    //      final ArrayList<PointsToSetVariable> rhss = new ArrayList<>();
    //      IntSetAction action = new IntSetAction() {
    //        @Override
    //        public void act(int i) {
    //          InstanceKey I = system.getInstanceKey(i);
    //          if (!representsNullType(I)) {
    //            //--- this for getField is the GetFieldOperator
    //            PointerKey p = getPointerKeyForInstanceField(I, getField());
    //
    //            if (p != null) {
    //              if (DEBUG_GET) {
    //                String S = "Getfield del constraint " + dVal + " " + p;
    //                System.err.println(S);
    //              }
    ////              sideEffect_del.b |= system.delConstraint(dVal, assignOperator, p);
    //              PointsToSetVariable ptv = system.findOrCreatePointsToSet(p);
    //              if(ptv.getValue() != null){
    //                rhss.add(ptv);
    //              }
    //            }
    //          }
    //        }
    //      };
    //      //*** always do it for all instance
    //      value.foreach(action);
    //      priorInstances.foreach(action);
    //      if(!rhss.isEmpty()){
    //        system.anotherWay(def, assignOperator, rhss, false);
    //      }
    //      //--- remove all priorInstance
    //      priorInstances.clear();
    //      delset.clear();
    //
    ////      byte sideEffectMask = sideEffect_del.b ? (byte) SIDE_EFFECT_MASK : 0;
    ////      return (byte) (NOT_CHANGED | sideEffectMask);
    //      return (byte) SIDE_EFFECT_MASK;
    //    }
  }

  /**
   * Operator that represents a putfield
   */
  public class PutFieldOperator extends UnarySideEffect implements IPointerOperator {
    private final IField field;

    protected final MutableIntSet priorInstances = rememberGetPutHistory ? IntSetUtil.make() : null;

    @Override
    public String toString() {
      return "PutField" + getField();
    }

    public PutFieldOperator(IField field, PointsToSetVariable val) {
      super(val);
      this.field = field;
      system.registerFixedSet(val, this);
    }

    /*
     * @see com.ibm.wala.ipa.callgraph.propagation.IPointerOperator#isComplex()
     */
    @Override
    public boolean isComplex() {
      return true;
    }

    @Override
    public byte evaluate(PointsToSetVariable rhs) {
      if (DEBUG_PUT) {
        String S = "EVAL PutField " + getField() + " " + (getFixedSet()).getPointerKey() + " " + rhs.getPointerKey()
        + getFixedSet() + " " + rhs;
        System.err.println(S);
      }

      if (rhs.size() == 0) {
        return NOT_CHANGED;
      }
      final PointerKey object = rhs.getPointerKey();

      PointsToSetVariable val = getFixedSet();
      final PointerKey pVal = val.getPointerKey();
      IntSet value = rhs.getValue();
      value = filterInstances(value);
      final UnaryOperator<PointsToSetVariable> assign = getPutAssignmentOperator();
      if (assign == null) {
        Assertions.UNREACHABLE();
      }
      final MutableBoolean sideEffect = new MutableBoolean();
      IntSetAction action = new IntSetAction() {
        @Override
        public void act(int i) {
          InstanceKey I = system.getInstanceKey(i);
          if (!representsNullType(I)) {
            if (DEBUG_PUT) {
              String S = "Putfield consider instance " + I;
              System.err.println(S);
            }
            PointerKey p = getPointerKeyForInstanceField(I, getField());
            if (p != null) {
              if (DEBUG_PUT) {
                String S = "Putfield add constraint " + p + " " + pVal;
                System.err.println(S);
              }
              //              sideEffect.b |= system.newFieldWrite(p, assign, pVal, object);
              sideEffect.b |= system.newConstraint(p, assign, pVal);
            }
          }
        }
      };

      //      int orl = val.getOrderNumber();
      //      int orr = rhs.getOrderNumber();
      //      PointsToSetVariable first;
      //      PointsToSetVariable sec;
      //      if(orl<orr){
      //        first = val;
      //        sec = rhs;
      //      }else{
      //        first =rhs;
      //        sec = val;
      //      }

      final ArrayList<PointsToSetVariable> lhss = new ArrayList<>();
      IntSetAction action2 = new IntSetAction() {
        @Override
        public void act(int i) {
          InstanceKey I = system.getInstanceKey(i);
          if (!representsNullType(I)) {
            PointerKey p = getPointerKeyForInstanceField(I, getField());
            if(p != null){
              PointsToSetVariable pptv = system.findOrCreatePointsToSet(p);
              if (p != null) {
                lhss.add(pptv);
              }
            }
          }
        }
      };
      //      synchronized(first){
      //        synchronized (sec) {
      if(system.isChange){//incremental
        if (priorInstances != null) {
          int size = value.size() - priorInstances.size();
          if(size < 1000){
            value.foreachExcluding(priorInstances, action);
          }else{
            if(val.getValue() != null){
              value.foreachExcluding(priorInstances, action2);
              MutableIntSet targets = IntSetUtil.makeMutableCopy(val.getValue());
              system.addConstraintHasMultiL(lhss, assignOperator, val, targets);
            }
          }
          priorInstances.addAll(value);
        } else {
          int size = value.size();
          if(size < 60){
            value.foreach(action);
          }else{
            if(val.getValue() != null){
              value.foreach(action2);
              MutableIntSet targets = IntSetUtil.makeMutableCopy(val.getValue());
              system.addConstraintHasMultiL(lhss, assignOperator, val, targets);
            }
          }
        }
      }else{//whole compute
        if (priorInstances != null) {
          value.foreachExcluding(priorInstances, action);
          priorInstances.addAll(value);
        }else{
          value.foreach(action);
        }
      }
      //        }
      //      }
      byte sideEffectMask = sideEffect.b ? (byte) SIDE_EFFECT_MASK : 0;
      return (byte) (NOT_CHANGED | sideEffectMask);
    }

    /**
     * Subclasses can override as needed
     */
    protected IntSet filterInstances(IntSet value) {
      return value;
    }

    @Override
    public int hashCode() {
      return 9857 * getField().hashCode() + getFixedSet().hashCode();
    }

    @Override
    public boolean equals(Object o) {
      if (o != null && o.getClass().equals(getClass())) {
        PutFieldOperator other = (PutFieldOperator) o;
        return getField().equals(other.getField()) && getFixedSet().equals(other.getFixedSet());
      } else {
        return false;
      }
    }

    /**
     * subclasses (e.g. XTA) can override this to enforce a filtered assignment. returns null if there's a problem.
     */
    public UnaryOperator<PointsToSetVariable> getPutAssignmentOperator() {
      return assignOperator;
    }

    /**
     * @return Returns the field.
     */
    protected IField getField() {
      return field;
    }

    @Override
    protected boolean isLoadOperator() {
      return false;
    }

    @Override
    public byte evaluateDel(PointsToSetVariable rhs) {

      if (DEBUG_PUT) {
        String S = "DEL EVAL PutField " + getField() + " " + (getFixedSet()).getPointerKey() + " " + rhs.getPointerKey()
        + getFixedSet() + " " + rhs;
        System.err.println(S);
      }

      if (rhs.size() == 0) {
        return NOT_CHANGED;
      }
      final PointerKey object = rhs.getPointerKey();
      final PointsToSetVariable val = getFixedSet();
      final PointerKey pVal = val.getPointerKey();
      IntSet value = rhs.getValue();
      value = filterInstances(value);
      final UnaryOperator<PointsToSetVariable> assign = getPutAssignmentOperator();
      if (assign == null) {
        Assertions.UNREACHABLE();
      }
      final MutableBoolean sideEffect_del = new MutableBoolean();
      IntSetAction action = new IntSetAction() {
        @Override
        public void act(int i) {
          InstanceKey I = system.getInstanceKey(i);
          if (!representsNullType(I)) {
            if (DEBUG_PUT) {
              String S = "Putfield consider instance " + I;
              System.err.println(S);
            }
            PointerKey p = getPointerKeyForInstanceField(I, getField());
            if (p != null) {
              if (DEBUG_PUT) {
                String S = "Putfield del constraint " + p + " " + pVal;
                System.err.println(S);
              }
              sideEffect_del.b |= system.delConstraint(p, assign, pVal);//bz: optimize
            }
          }
        }
      };

      if(value.size() < 10)
        value.foreach(action);
      else{
        if(val.getValue() != null){
          final ArrayList<PointsToSetVariable> lhss = new ArrayList<>();
          IntSetAction action2 = new IntSetAction() {
            @Override
            public void act(int i) {
              InstanceKey I = system.getInstanceKey(i);
              if (!representsNullType(I)) {
                PointerKey p = getPointerKeyForInstanceField(I, getField());
                if(p != null){
                  PointsToSetVariable pptv = system.findOrCreatePointsToSet(p);
                  MutableIntSet set = pptv.getValue();
                  if(set != null)
                    lhss.add(pptv);
                }
              }
            }
          };
          value.foreach(action2);
          MutableIntSet targets = IntSetUtil.makeMutableCopy(val.getValue());
          system.delConstraintHasMultiL(lhss, assignOperator, val, targets);
        }
      }
      priorInstances.foreach(action);
      priorInstances.clear();

      byte sideEffectMask = sideEffect_del.b ? (byte) SIDE_EFFECT_MASK : 0;
      return (byte) (NOT_CHANGED | sideEffectMask);

    }
  }

  /**
   * Update the points-to-set for a field to include a particular instance key.
   */
  public final class InstancePutFieldOperator extends UnaryOperator<PointsToSetVariable> implements IPointerOperator {
    final private IField field;

    final private InstanceKey instance;

    protected final MutableIntSet priorInstances = rememberGetPutHistory ? IntSetUtil.make() : null;

    @Override
    public String toString() {
      return "InstancePutField" + field;
    }

    public InstancePutFieldOperator(IField field, InstanceKey instance) {
      this.field = field;
      this.instance = instance;
    }

    /**
     * Simply add the instance to each relevant points-to set.
     */
    @Override
    public byte evaluate(PointsToSetVariable dummyLHS, PointsToSetVariable var) {
      PointsToSetVariable ref = var;
      if (ref.size() == 0) {
        return NOT_CHANGED;
      }
      IntSet value = ref.getValue();
      final MutableBoolean sideEffect = new MutableBoolean();
      IntSetAction action = new IntSetAction() {
        @Override
        public void act(int i) {
          InstanceKey I = system.getInstanceKey(i);
          if (!representsNullType(I)) {
            PointerKey p = getPointerKeyForInstanceField(I, field);
            if (p != null) {
              sideEffect.b |= system.newConstraint(p, instance);
            }
          }
        }
      };
      if (priorInstances != null) {
        value.foreachExcluding(priorInstances, action);
        priorInstances.addAll(value);
      } else {
        value.foreach(action);
      }
      byte sideEffectMask = sideEffect.b ? (byte) SIDE_EFFECT_MASK : 0;
      return (byte) (NOT_CHANGED | sideEffectMask);
    }

    @Override
    public int hashCode() {
      return field.hashCode() + 9839 * instance.hashCode();
    }

    @Override
    public boolean equals(Object o) {
      if (o instanceof InstancePutFieldOperator) {
        InstancePutFieldOperator other = (InstancePutFieldOperator) o;
        return field.equals(other.field) && instance.equals(other.instance);
      } else {
        return false;
      }
    }

    /*
     * @see com.ibm.wala.ipa.callgraph.propagation.IPointerOperator#isComplex()
     */
    @Override
    public boolean isComplex() {
      return true;
    }

    @Override
    public byte evaluateDel(PointsToSetVariable dummyLHS, PointsToSetVariable var) {

      PointsToSetVariable ref = var;
      if (ref.size() == 0) {
        return NOT_CHANGED;
      }
      IntSet value = ref.getValue();
      final MutableBoolean sideEffect_del = new MutableBoolean();
      final ArrayList<PointsToSetVariable> lhss = new ArrayList<>();
      IntSetAction action = new IntSetAction() {
        @Override
        public void act(int i) {
          InstanceKey I = system.getInstanceKey(i);
          if (!representsNullType(I)) {
            //--- this I is ref IK, since field is settled, it's up to ref PK
            PointerKey p = getPointerKeyForInstanceField(I, field);
            if (p != null) {
              //?? why not read/write new field????
              //              sideEffect_del.b |= system.delConstraint(p, instance);
              PointsToSetVariable lhs = system.findOrCreatePointsToSet(p);
              if(lhs != null)
                lhss.add(lhs);
            }
          }
        }
      };

      value.foreach(action);
      MutableIntSet delset = IntSetUtil.make();
      delset.add(system.findOrCreateIndexForInstanceKey(instance));
      system.delConstraintMultiInstanceFromL(lhss, delset, ref);
      priorInstances.foreach(action);
      priorInstances.clear();
      byte sideEffectMask = sideEffect_del.b ? (byte) SIDE_EFFECT_MASK : 0;
      return (byte) (NOT_CHANGED | sideEffectMask);
    }
  }

  /**
   * Update the points-to-set for an array contents to include a particular instance key.
   */
  public final class InstanceArrayStoreOperator extends UnaryOperator<PointsToSetVariable> implements IPointerOperator {
    final private InstanceKey instance;

    protected final MutableIntSet priorInstances = rememberGetPutHistory ? IntSetUtil.make() : null;

    @Override
    public String toString() {
      return "InstanceArrayStore ";
    }

    public InstanceArrayStoreOperator(InstanceKey instance) {
      this.instance = instance;
    }

    /**
     * Simply add the instance to each relevant points-to set.
     */
    @Override
    public byte evaluate(PointsToSetVariable dummyLHS, PointsToSetVariable var) {
      PointsToSetVariable arrayref = var;
      if (arrayref.size() == 0) {
        return NOT_CHANGED;
      }
      IntSet value = arrayref.getValue();
      final MutableBoolean sideEffect = new MutableBoolean();
      IntSetAction action = new IntSetAction() {
        @Override
        public void act(int i) {
          InstanceKey I = system.getInstanceKey(i);
          if (!I.getConcreteType().isArrayClass()) {
            return;
          }
          if (I instanceof ZeroLengthArrayInNode) {
            return;
          }
          TypeReference C = I.getConcreteType().getReference().getArrayElementType();
          if (C.isPrimitiveType()) {
            return;
          }
          IClass contents = getClassHierarchy().lookupClass(C);
          if (contents == null) {
            assert false : "null type for " + C + " " + I.getConcreteType();
          }
          PointerKey p = getPointerKeyForArrayContents(I);
          if (contents.isInterface()) {
            if (getClassHierarchy().implementsInterface(instance.getConcreteType(), contents)) {
              sideEffect.b |= system.newConstraint(p, instance);
            }
          } else {
            if (getClassHierarchy().isSubclassOf(instance.getConcreteType(), contents)) {
              sideEffect.b |= system.newConstraint(p, instance);
            }
          }
        }
      };
      if (priorInstances != null) {
        value.foreachExcluding(priorInstances, action);
        priorInstances.addAll(value);
      } else {
        value.foreach(action);
      }
      byte sideEffectMask = sideEffect.b ? (byte) SIDE_EFFECT_MASK : 0;
      return (byte) (NOT_CHANGED | sideEffectMask);
    }

    @Override
    public int hashCode() {
      return 9839 * instance.hashCode();
    }

    @Override
    public boolean equals(Object o) {
      if (o instanceof InstanceArrayStoreOperator) {
        InstanceArrayStoreOperator other = (InstanceArrayStoreOperator) o;
        return instance.equals(other.instance);
      } else {
        return false;
      }
    }

    /*
     * @see com.ibm.wala.ipa.callgraph.propagation.IPointerOperator#isComplex()
     */
    @Override
    public boolean isComplex() {
      return true;
    }


    @Override
    public byte evaluateDel(PointsToSetVariable dummyLHS, PointsToSetVariable var) {
      PointsToSetVariable arrayref = var;
      if (arrayref.size() == 0) {
        return NOT_CHANGED;
      }
      IntSet value = arrayref.getValue();
      final MutableBoolean sideEffect_del = new MutableBoolean();
      final ArrayList<PointsToSetVariable> lhss = new ArrayList<>();
      IntSetAction action = new IntSetAction() {
        @Override
        public void act(int i) {
          InstanceKey I = system.getInstanceKey(i);
          if (!I.getConcreteType().isArrayClass()) {
            return;
          }
          if (I instanceof ZeroLengthArrayInNode) {
            return;
          }
          TypeReference C = I.getConcreteType().getReference().getArrayElementType();
          if (C.isPrimitiveType()) {
            return;
          }
          IClass contents = getClassHierarchy().lookupClass(C);
          if (contents == null) {
            assert false : "null type for " + C + " " + I.getConcreteType();
          }
          PointerKey p = getPointerKeyForArrayContents(I);
          if (contents.isInterface()) {
            if (getClassHierarchy().implementsInterface(instance.getConcreteType(), contents)) {
              //              sideEffect_del.b |= system.delConstraint(p, instance);
              lhss.add(system.findOrCreatePointsToSet(p));
            }
          } else {
            if (getClassHierarchy().isSubclassOf(instance.getConcreteType(), contents)) {
              //              sideEffect_del.b |= system.delConstraint(p, instance);
              lhss.add(system.findOrCreatePointsToSet(p));
            }
          }
        }
      };
      //*** in case of prior instances is not a subset of value
      priorInstances.foreach(action);
      value.foreach(action);
      MutableIntSet delset = IntSetUtil.make();
      delset.add(system.findOrCreateIndexForInstanceKey(instance));
      system.delConstraintMultiInstanceFromL(lhss, delset, arrayref);
      priorInstances.clear();
      byte sideEffectMask = sideEffect_del.b ? (byte) SIDE_EFFECT_MASK : 0;
      return (byte) (NOT_CHANGED | sideEffectMask);

    }
  }

  protected MutableIntSet getMutableInstanceKeysForClass(IClass klass) {
    return system.cloneInstanceKeysForClass(klass);
  }

  protected IntSet getInstanceKeysForClass(IClass klass) {
    return system.getInstanceKeysForClass(klass);
  }

  /**
   * @param klass a class
   * @return an int set which represents the subset of S that correspond to subtypes of klass
   */
  protected IntSet filterForClass(IntSet S, IClass klass) {
    MutableIntSet filter = null;
    if (klass.getReference().equals(TypeReference.JavaLangObject)) {
      return S;
    } else {
      filter = getMutableInstanceKeysForClass(klass);

      boolean debug = false;
      if (DEBUG_FILTER) {
        String s = "klass     " + klass;
        System.err.println(s);
        System.err.println("initial filter    " + filter);
      }
      filter.intersectWith(S);

      if (DEBUG_FILTER && debug) {
        System.err.println("final filter    " + filter);
      }
    }
    return filter;
  }

  protected class InverseFilterOperator extends FilterOperator {
    public InverseFilterOperator() {
      super();
    }

    @Override
    public String toString() {
      return "InverseFilter";
    }

    /*
     * @see com.ibm.wala.ipa.callgraph.propagation.IPointerOperator#isComplex()
     */
    @Override
    public boolean isComplex() {
      return false;
    }

    /*
     * simply check if rhs contains a malleable.
     *
     * @see com.ibm.wala.dataflow.UnaryOperator#evaluate(com.ibm.wala.dataflow.IVariable, com.ibm.wala.dataflow.IVariable)
     */
    @Override
    public byte evaluate(PointsToSetVariable lhs, PointsToSetVariable rhs) {

      FilteredPointerKey pk = (FilteredPointerKey) lhs.getPointerKey();
      FilteredPointerKey.TypeFilter filter = pk.getTypeFilter();

      boolean debug = false;
      if (DEBUG_FILTER) {
        String S = "EVAL InverseFilter/" + filter + " " + lhs.getPointerKey() + " " + rhs.getPointerKey();
        S += "\nEVAL      " + lhs + " " + rhs;
        System.err.println(S);
      }
      if (rhs.size() == 0) {
        return NOT_CHANGED;
      }

      boolean changed = filter.addInverseFiltered(system, lhs, rhs);

      if (DEBUG_FILTER) {
        if (debug) {
          System.err.println("RESULT " + lhs + (changed ? " (changed)" : ""));
        }
      }
      return changed ? CHANGED : NOT_CHANGED;
    }
  }

  protected IPointsToSolver getSolver() {
    return solver;
  }

  /**
   * Add constraints when the interpretation of a node changes (e.g. reflection)
   * @param monitor
   * @throws CancelException
   */
  public void addConstraintsFromChangedNode(CGNode node, IProgressMonitor monitor) throws CancelException {
    SSAInstruction[] instructions = node.getIR().getInstructions();
//    System.out.println("**** Update Call Graph, Add Inst: ");
//    for (SSAInstruction ssaInstruction : instructions) {
//      if(ssaInstruction != null)
//        System.out.println("          " + ssaInstruction.toString());
//    }
    unconditionallyAddConstraintsFromNode(node, monitor);
  }

  protected abstract boolean unconditionallyAddConstraintsFromNode(CGNode node, IProgressMonitor monitor) throws CancelException;

  protected static class MutableBoolean {
    // a horrendous hack since we don't have closures
    boolean b = false;
  }

  @Override
  public AnalysisCache getAnalysisCache() {
    return analysisCache;
  };

  public PointsToMap getPointsToMap(){
    return system.pointsToMap;
  }

  public abstract void processDiff(CGNode node, ISSABasicBlock bb, SSAInstruction diff);
  public abstract void setDelete(boolean delete);


}
