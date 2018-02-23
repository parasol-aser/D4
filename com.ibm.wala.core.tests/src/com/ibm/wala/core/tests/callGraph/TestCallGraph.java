package com.ibm.wala.core.tests.callGraph;

import java.io.IOException;
import java.util.Scanner;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.core.tests.demandpa.AbstractPtrTest;
import com.ibm.wala.core.tests.util.TestConstants;
import com.ibm.wala.core.tests.util.WalaTestCase;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.CallGraphStats;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.AllApplicationEntrypoints;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ipa.callgraph.propagation.PointsToSetVariable;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ipa.cfg.InterproceduralCFG;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.types.Descriptor;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.collections.Iterator2Iterable;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.GraphIntegrity;
import com.ibm.wala.util.graph.GraphIntegrity.UnsoundGraphException;
import com.ibm.wala.util.intset.OrdinalSet;
import com.ibm.wala.util.io.FileProvider;
import com.ibm.wala.util.perf.StopwatchGC;
import com.ibm.wala.util.strings.Atom;
import com.ibm.wala.util.warnings.Warnings;
import com.ibm.wala.ipa.callgraph.propagation.*;

//--- write call graph to a file
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;


public class TestCallGraph {

  private static final ClassLoader MY_CLASSLOADER = TestCallGraph.class.getClassLoader();

  private final static int LOOPTIMES = 20;

  /**
   * should we check the heap footprint before and after CG construction?
   */
  private static final boolean CHECK_FOOTPRINT = false;



  public static AnalysisOptions makeAnalysisOptions(AnalysisScope scope, Iterable<Entrypoint> entrypoints) {
    AnalysisOptions options = new AnalysisOptions(scope, entrypoints);
    return options;
  }

  public static AnalysisScope makeJ2SEAnalysisScope(String scopeFile, String exclusionsFile) throws IOException {
    AnalysisScope scope = AnalysisScopeReader.readJavaScope(scopeFile, (new FileProvider()).getFile(exclusionsFile), MY_CLASSLOADER);
    return scope;
  }


  public static void main(String[] args)
      throws  ClassHierarchyException, IllegalArgumentException, CancelException, IOException  {

//    try{
//    IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
//    IProject project = root.getProject("test");
//
//  //set the Java nature
//    IProjectDescription description = project.getDescription();
//    description.setNatureIds(new String[] { JavaCore.NATURE_ID });
//
//
//    project.setDescription(description, null);
//    IJavaProject javaProject = JavaCore.create(project);
//
//
//    edu.tamu.cse.aser.tarp.engine.KeshmeshCGModel model = new edu.tamu.cse.aser.tarp.engine.KeshmeshCGModel(javaProject, "EclipseDefaultExclusions.txt",0,"TestHello.main([Ljava/lang/String;)");
//
//    model.buildGraph();
//
//
//    }catch(Exception e)
//    {
//      e.printStackTrace();
//    }
    /*
     * Make Scope, Build CHA, Find ALL Entrypoints
     */
//    String Scope = "./jigsaw/jigsaw/classes/jigsaw.jar";
//    String ExclusionFile = "./ExclusionSample.txt";
    String Scope = "/Users/shengzhan/Documents/wala/ide/test/TestHello.txt";
    Scope = "/Users/shengzhan/Documents/wala/ide/com.ibm.wala.core.tests/dat/TestHello.txt";
    String ExclusionFile = "/Users/shengzhan/Documents/wala/ide/test/ExclusionSample.txt";
    //ExclusionFile = null;
    Scanner in = new Scanner(System.in);
//    System.out.println("Please Input Class Path:");
//    Scope = in.nextLine();
//    System.out.println("Please Input Exclusion File Path:");
//    ExclusionFile = in.nextLine();

    long start = System.currentTimeMillis();
    AnalysisScope scope;
    if(ExclusionFile == null || ExclusionFile.equals(""))
      scope =  AnalysisScopeReader.readJavaScope(Scope, null, MY_CLASSLOADER);
    else
      scope = AnalysisScopeReader.readJavaScope(Scope, (new FileProvider()).getFile(ExclusionFile), MY_CLASSLOADER);


    ClassHierarchy cha = ClassHierarchy.make(scope);
    Iterable<Entrypoint> entrypoints = Util.makeMainEntrypoints(scope, cha);
    AnalysisOptions options = makeAnalysisOptions(scope, entrypoints);


    CallGraphBuilder builder = Util.makeZeroCFABuilder(options, new AnalysisCache(), cha, scope);
    System.out.println("Start");
    CallGraph cg  = builder.makeCallGraph(options, null);
    //builder.addDel();
    /*
     * count time used for call graph construction
     */
    if(System.currentTimeMillis()-start > ((0x1l<<63)-1)){
      System.out.println("too long, sorry :<");
      return;
    }
    long elapsedTimeMillis = System.currentTimeMillis()-start;
 // Get elapsed time in seconds
    float elapsedTimeSec = (elapsedTimeMillis/1000F)<1 ? 0 : elapsedTimeMillis/1000F;

    // Get elapsed time in minutes
    float elapsedTimeMin = (elapsedTimeMillis/(60*1000F))<1 ? 0 : elapsedTimeMillis/(60*1000F);

    // Get elapsed time in hours
    float elapsedTimeHour = (elapsedTimeMillis/(60*60*1000F))<1? 0: elapsedTimeMillis/(60*60*1000F);

    // Get elapsed time in days
    float elapsedTimeDay = (elapsedTimeMillis/(24*60*60*1000F))<1? 0: elapsedTimeMillis/(24*60*60*1000F);
    System.out.println("total time-- days: "+elapsedTimeDay+" hours: "+elapsedTimeHour+" mins: "+elapsedTimeMin+" secs: "+elapsedTimeSec);


    PointsToMap pointsToMap = builder.getPointsToMap();
    System.out.println("Please Enter File Name:");
    Scope = in.nextLine();
    //Scope = "jigsaw";
    /*
     * Write Call Graph & Points To Map to file
     */
    //String f1 = "/Users/szhan/Desktop/"+Scope+"_original.txt", f2 = "/users/szhan/test/delete1.txt";
    //String f3 = "/users/szhan/test/" + Scope+"_deleteThenAdd.txt";
    //String f5 = "/users/szhan/test/skip.txt";
    String f3 = "/Users/shengzhan/Desktop/"+Scope+"_deleteThenAdd.txt";

    //--- print out the call graph
    int numMethod = 0, numEdge = 0;
    try{
      File file = new File(f3);

      if (!file.exists()) {
        file.createNewFile();
      }
      FileWriter fw = new FileWriter(file.getAbsoluteFile());
      BufferedWriter bw = new BufferedWriter(fw);
      Iterator itNode =  cg.iterator();
      while(itNode != null && itNode.hasNext()){
        CGNode curNode = (CGNode)itNode.next();
//        if(curNode.toString().contains("main"))
//          System.out.println("gotcha!");
        String curStr = "---  "+curNode.toString()+"\n";
        bw.write(curStr);
        if(curNode == null)
          continue;
        numMethod ++;
        Iterator itSucc = cg.getSuccNodes(curNode);
        while(itSucc != null && itSucc.hasNext()){
          CGNode succNode = (CGNode)itSucc.next();
          if(succNode == null) continue;
          String succStr = "-|-  "+succNode.toString()+"\n";
          numEdge ++;
          bw.write(succStr);
        }
      }
      bw.write(String.valueOf(elapsedTimeMillis));
      bw.close();
      System.out.println("num of methods: "+numMethod);
      System.out.println("num of edges: "+numEdge);

      //System.out.println("Done!");
    }catch(IOException e){
      e.printStackTrace();
    }



    //String ff1 = "/Users/szhan/Desktop/"+Scope+"_original_PTM.txt", ff2 = "/users/szhan/test/delete1_PTM.txt";
    // String ff3 = "/users/szhan/test/" + Scope + "_deleteThenAdd_PTM.txt";
    //String ff5 = "/users/szhan/test/skip_PTM.txt";
    String ff3 = "/Users/shengzhan/Desktop/"+Scope+"_deleteThenAdd_PTM.txt";



    try{
      File file2 = new File(ff3);
      if (!file2.exists()) {
        file2.createNewFile();
      }
      int numPK = 0;
      FileWriter fw2 = new FileWriter(file2.getAbsoluteFile());
      BufferedWriter bw2 = new BufferedWriter(fw2);
      Iterator<PointerKey> itPK = pointsToMap.iterateKeys();
      while(itPK != null && itPK.hasNext()){
        PointerKey curPK = itPK.next();
        if(curPK == null){
          continue;
        }
        String curStr = "--- Cur PK : "+curPK.toString() + "\n";
        numPK ++;
        bw2.write(curStr);
        PointsToSetVariable PTSV = pointsToMap.getPointsToSet(curPK);
        String curPTSV = "-|- null \n";
        if(PTSV != null){
          curPTSV = "-|-  "+PTSV.toString()+"\n";
        }

        bw2.write(curPTSV);
      }
      bw2.close();
      System.out.println("num of pk: "+numPK);
      //System.out.println("Dang Dang Dang, Done!");
    }catch(IOException e){
      e.printStackTrace();
    }

    System.out.println("^.^!");
    }


}

