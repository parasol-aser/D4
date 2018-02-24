package edu.tamu.aser.tide.tests;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;

import com.ibm.wala.cfg.ControlFlowGraph;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.core.tests.callGraph.CallGraphTestUtil;
import com.ibm.wala.fixpoint.IVariable;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysisImpl;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ipa.callgraph.propagation.PropagationCallGraphBuilder;
import com.ibm.wala.ipa.callgraph.propagation.PropagationGraph;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder.ConstraintVisitor;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.io.FileProvider;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import edu.tamu.aser.tide.akkasys.BugHub;
import edu.tamu.aser.tide.dist.remote.master.DistributeMaster;
import edu.tamu.aser.tide.engine.AnalysisUtils;
import edu.tamu.aser.tide.engine.ITIDEBug;
import edu.tamu.aser.tide.engine.TIDEDeadlock;
import edu.tamu.aser.tide.engine.TIDEEngine;
import edu.tamu.aser.tide.engine.TIDERace;
import edu.tamu.aser.tide.plugin.handlers.ConvertHandler;
import edu.tamu.aser.tide.trace.DLockNode;
import edu.tamu.aser.tide.trace.MemNode;
import scala.reflect.internal.Trees.This;

public class ReproduceBenchmarks {

	static PrintStream ps;
	private static long totaltime;
	public static TIDEEngine engine;

	static boolean includeAllMainEntryPoints = false;

	static String benchmark = null;
	static String mainSignature = ".main([Ljava/lang/String;)V";
	static String mainClassName = null;
	static String mainMethodSig = null;
	static String testFile = null;
	static String excludeFile = "data/DefaultExclusions.txt";

    static String[] benchmark_names_short= new String[] { "avrora_short", "batik_short", "eclipse_short", "fop_short",
			"h2_short", "jython_short", "luindex_short", "lusearch_short", "pmd_short",
			"sunflow_short", "tomcat_short", "tradebeans_short", "tradesoap_short",
			"xalan_short"};

    static String[] benchmark_names = new String[] { "avrora", "batik", "eclipse", "fop", "h2", "jython",
			"luindex", "lusearch", "pmd", "sunflow", "tomcat", "tradebeans", "tradesoap",
			"xalan"};

	public static void main(String[] args) throws IOException, ClassHierarchyException, IllegalArgumentException, CallGraphBuilderCancelException {
		if(args.length == 0 || args.length > 2){
			throw new IllegalArgumentException("Wrong number of arguments. Please enter the benchmark name.");
		}

		String arg = args[0];
		benchmark = arg;
		switch (arg) {
		case "avrora":
			mainClassName = "avrora/Main";
			mainMethodSig = "avrora.Main" + mainSignature;
			testFile = "data/avroratestfile.txt";
			break;
		case "batik":
			mainClassName = "rasterizer/Main";
			mainMethodSig = "rasterizer.Main" + mainSignature;
			testFile = "data/batiktestfile.txt";
			break;
		case "eclipse":
			mainClassName = "EclipseStarter";
			mainMethodSig = mainClassName + mainSignature;
			testFile = "data/eclipsetestfile.txt";
			break;
		case "fop": //no detection
			mainClassName = "TTFFile";
			mainMethodSig = mainClassName + mainSignature;
			testFile = "data/foptestfile.txt";
			break;
		case "h2":
			mainClassName = "Shell";
			mainMethodSig = mainClassName + mainSignature;
			testFile = "data/h2testfile.txt";
			break;
		case "jython":
			mainClassName = "jython";
			mainMethodSig = mainClassName + mainSignature;
			testFile = "data/jythontestfile.txt";
			break;
		case "luindex":
			mainClassName = "IndexFiles";
			mainMethodSig = mainClassName + mainSignature;
			excludeFile = "data/luindexexcludefile.txt";
			testFile = "data/dacapotestfile.txt";
			break;
		case "lusearch":
			mainClassName = "IndexHTML";
			mainMethodSig = mainClassName + mainSignature;
			excludeFile = "data/lusearchexcludefile.txt";
			testFile = "data/dacapotestfile.txt";
			break;
		case "pmd":
			mainClassName = "GUI";
			mainMethodSig = mainClassName + mainSignature;
			testFile = "data/pmdtestfile.txt";
			break;
		case "sunflow":
			mainClassName = "Benchmark";
			mainMethodSig = mainClassName + mainSignature;
			testFile = "data/sunflowtestfile.txt";
			break;
		case "tomcat":
			mainClassName = "ExpressionDemo";
			mainMethodSig = mainClassName + mainSignature;
			testFile = "data/dacapotestfile.txt";
			excludeFile = "data/tomcatexcludefile.txt";
			break;
		case "tradebeans":
			mainClassName = "REUtil";
			mainMethodSig = mainClassName + mainSignature;
			testFile = "data/dacapotestfile.txt";
			excludeFile = "data/tradebeansexcludefile.txt";
			break;
		case "tradesoap":
			mainClassName = "REUtil";
			mainMethodSig = mainClassName + mainSignature;
			testFile = "data/dacapotestfile.txt";
			excludeFile = "data/tradesoapexcludefile.txt";
			break;
		case "xalan":
			mainClassName = "XSLProcessorVersion";
			mainMethodSig = mainClassName + mainSignature;
			testFile = "data/xalantestfile.txt";
			break;
		case "all":
			if(args.length == 2){
				String arg2 = args[1];
				iterateAllBenchmarksMultithread(arg2);
			}else{
				iterateAllBenchmarks();
			}
			break;

	    //short version
		case "avrora_short":
			mainClassName = "avrora/Main";
			mainMethodSig = "avrora.Main" + mainSignature;
			testFile = "data/avroratestfile.txt";
			excludeFile = "data/ShortDefaultExclusions.txt";
			break;
		case "batik_short":
			mainClassName = "rasterizer/Main";
			mainMethodSig = "rasterizer.Main" + mainSignature;
			testFile = "data/batiktestfile.txt";
			excludeFile = "data/ShortDefaultExclusions.txt";
			break;
		case "eclipse_short":
			mainClassName = "EclipseStarter";
			mainMethodSig = mainClassName + mainSignature;
			testFile = "data/eclipsetestfile.txt";
			excludeFile = "data/ShortDefaultExclusions.txt";
			break;
		case "fop_short": //no detection
			mainClassName = "TTFFile";
			mainMethodSig = mainClassName + mainSignature;
			testFile = "data/foptestfile.txt";
			excludeFile = "data/ShortDefaultExclusions.txt";
			break;
		case "h2_short":
			mainClassName = "Shell";
			mainMethodSig = mainClassName + mainSignature;
			testFile = "data/h2testfile.txt";
			excludeFile = "data/ShortDefaultExclusions.txt";
			break;
		case "jython_short":
			mainClassName = "jython";
			mainMethodSig = mainClassName + mainSignature;
			testFile = "data/jythontestfile.txt";
			excludeFile = "data/ShortDefaultExclusions.txt";
			break;
		case "luindex_short":
			mainClassName = "IndexFiles";
			mainMethodSig = mainClassName + mainSignature;
			excludeFile = "data/luindexexcludefileshort.txt";
			testFile = "data/dacapotestfile.txt";
			break;
		case "lusearch_short":
			mainClassName = "IndexHTML";
			mainMethodSig = mainClassName + mainSignature;
			excludeFile = "data/lusearchexcludefileshort.txt";
			testFile = "data/dacapotestfile.txt";
			break;
		case "pmd_short":
			mainClassName = "GUI";
			mainMethodSig = mainClassName + mainSignature;
			testFile = "data/pmdtestfile.txt";
			excludeFile = "data/ShortDefaultExclusions.txt";
			break;
		case "sunflow_short":
			mainClassName = "Benchmark";
			mainMethodSig = mainClassName + mainSignature;
			testFile = "data/sunflowtestfile.txt";
			excludeFile = "data/ShortDefaultExclusions.txt";
			break;
		case "tomcat_short":
			mainClassName = "ExpressionDemo";
			mainMethodSig = mainClassName + mainSignature;
			testFile = "data/dacapotestfile.txt";
			excludeFile = "data/tomcatexcludefileshort.txt";
			break;
		case "tradebeans_short":
			mainClassName = "REUtil";
			mainMethodSig = mainClassName + mainSignature;
			testFile = "data/dacapotestfile.txt";
			excludeFile = "data/tradebeansexcludefileshort.txt";
			break;
		case "tradesoap_short":
			mainClassName = "REUtil";
			mainMethodSig = mainClassName + mainSignature;
			testFile = "data/dacapotestfile.txt";
			excludeFile = "data/tradesoapexcludefileshort.txt";
			break;
		case "xalan_short":
			mainClassName = "XSLProcessorVersion";
			mainMethodSig = mainClassName + mainSignature;
			testFile = "data/xalantestfile.txt";
			excludeFile = "data/ShortDefaultExclusions.txt";
			break;
		case "all_short":
			if(args.length == 2){
				String arg2 = args[1];
				iterateAllBenchmarksShortMultithread(arg2);
			}else{
				iterateAllBenchmarksShort();
			}
			break;

		default:
			throw new IllegalArgumentException("Invalid argument: " + arg);
		}

		if(args.length == 2){
			String arg2 = args[1];
			print("Benchmark: " + arg, true);
			int numOfWorkers = Integer.parseInt(arg2);
			runD4_multithreads(numOfWorkers);
			System.out.println();
		}else{
			//start
			print("Benchmark: " + arg, true);
			runD4_1();
			System.out.println();
			runD4_48();
			System.out.println();
		}
	}

	private static void iterateAllBenchmarksShortMultithread(String arg2)
			throws ClassHierarchyException, IllegalArgumentException, CallGraphBuilderCancelException, IOException{
		System.out.println("RUNNING SHORT TESTS FOR ALL BENCHMARKS WITH " + arg2 + " ON A SINGLE MACHINE.\n");

		for (int i = 0; i < benchmark_names_short.length; i++) {
			String[] arg = new String[]{benchmark_names_short[i], arg2};
			main(arg);
		}

		System.out.println("\n COMPLETE SHORT TESTING ALL BENCHMARKS ON A SINGLE MACHINE.");
	}

	private static void iterateAllBenchmarksShort()
			throws ClassHierarchyException, IllegalArgumentException, CallGraphBuilderCancelException, IOException{
		System.out.println("RUNNING SHORT TESTS FOR ALL BENCHMARKS.\n");

		for (int i = 0; i < benchmark_names_short.length; i++) {
			String[] arg = new String[]{benchmark_names_short[i]};
			main(arg);
		}

		System.out.println("\n COMPLETE SHORT TESTING ALL BENCHMARKS.");
	}

	private static void iterateAllBenchmarksMultithread(String arg2)
			throws ClassHierarchyException, IllegalArgumentException, CallGraphBuilderCancelException, IOException {
		System.out.println("RUNNING FULL TESTS FOR ALL BENCHMARKS WITH " + arg2 + " ON A SINGLE MACHINE.\n");

		for (int i = 0; i < benchmark_names.length; i++) {
			String[] arg = new String[]{benchmark_names[i], arg2};
			main(arg);
		}

		System.out.println("\n COMPLETE FULL TESTING ALL BENCHMARKS ON A SINGLE MACHINE.");
	}

	private static void iterateAllBenchmarks() throws ClassHierarchyException, IllegalArgumentException, CallGraphBuilderCancelException, IOException {
		System.out.println("RUNNING FULL TESTS FOR ALL BENCHMARKS.\n");

		for (int i = 0; i < benchmark_names.length; i++) {
			String[] arg = new String[]{benchmark_names[i]};
			main(arg);
		}

		System.out.println("\n COMPLETE FULL TESTING ALL BENCHMARKS.");
	}



	private static void runD4_multithreads(int numOfWorkers)
			throws IOException, ClassHierarchyException, IllegalArgumentException, CallGraphBuilderCancelException{
		print("D4 with " + numOfWorkers + " threads on a single machine", true);
		System.out.println("Running Exhaustive Points-to Analysis ... ");
		AnalysisScope scope = CallGraphTestUtil.makeJ2SEAnalysisScope(testFile, excludeFile);
		ClassHierarchy cha = ClassHierarchy.make(scope);
		Iterable<Entrypoint> entrypoints = findEntryPoints(cha,mainClassName,includeAllMainEntryPoints);
		AnalysisOptions options = new AnalysisOptions(scope, entrypoints);

		SSAPropagationCallGraphBuilder builder = Util.makeZeroOneContainerCFABuilder(options, new AnalysisCache(), cha, scope);

		long start_time = System.currentTimeMillis();
		CallGraph cg  = builder.makeCallGraph(options, null);
		PointerAnalysis<InstanceKey> pta = builder.getPointerAnalysis();
		System.out.println("Exhaustive Points-to Analysis Time: "+(System.currentTimeMillis()-start_time) + "ms");
		int numofCGNodes = cg.getNumberOfNodes();
		int totalInstanceKey = pta.getInstanceKeys().size();
		int totalPointerKey =((PointerAnalysisImpl)pta).getNumOfPointerKeys();
		int totalPointerEdge = 0;
		int totalClass=cha.getNumberOfClasses();
		Iterator<PointerKey> iter = pta.getPointerKeys().iterator();
		while(iter.hasNext()){
			PointerKey key = iter.next();
			int size = pta.getPointsToSet(key).size();
			totalPointerEdge+=size;
		}
		System.out.println("#Class: "+totalClass);
		System.out.println("#Method: "+numofCGNodes);
		System.out.println("#Pointer: "+totalPointerKey);
		System.out.println("#Object: "+totalInstanceKey);
		System.out.println("#Edges: "+totalPointerEdge);
		System.out.println();
		ps.println();

		System.out.println("Running Exhaustive Detection ... ");
		//detector
		ActorSystem akkasys = ActorSystem.create();
		ActorRef bughub = akkasys.actorOf(Props.create(BugHub.class, numOfWorkers), "bughub");
		start_time = System.currentTimeMillis();
		PropagationGraph flowgraph = builder.getPropagationSystem().getPropagationGraph();
		engine = new TIDEEngine((includeAllMainEntryPoints? mainSignature:mainMethodSig), cg, flowgraph, pta, bughub);
		Set<ITIDEBug> bugs = engine.detectBothBugs(ps);

//		System.out.println("EXHAUSTIVE DETECTION >>>");
		int race = 0;
		int dl = 0;
		for(ITIDEBug bug : bugs){
			if(bug instanceof TIDERace){
				race++;
			}else if (bug instanceof TIDEDeadlock){
				dl++;
			}
		}
		System.out.println("Exhaustive Detection Time: " + (System.currentTimeMillis() - start_time) + "ms");
//		System.err.println("Exhaustive Race Detection Time: " + engine.timeForDetectingRaces);
//		System.err.println("Exhaustive Deadlock Detection Time: " + engine.timeForDetectingDL);
//		System.out.println("#Race: " + race + "  #Deadlock: " + dl);

		System.out.println("Running Incremental Points-to Analysis and Detection ... ");
		builder.getPropagationSystem().initializeAkkaSys(numOfWorkers);
		incrementalTest(builder, cg);
	}

	public static void runD4_1()
			throws IOException, ClassHierarchyException, IllegalArgumentException, CallGraphBuilderCancelException{
		print("D4-1", true);
		System.out.println("Running Exhaustive Points-to Analysis ... ");
		AnalysisScope scope = CallGraphTestUtil.makeJ2SEAnalysisScope(testFile, excludeFile);
		ClassHierarchy cha = ClassHierarchy.make(scope);
		Iterable<Entrypoint> entrypoints = findEntryPoints(cha,mainClassName,includeAllMainEntryPoints);
		AnalysisOptions options = new AnalysisOptions(scope, entrypoints);

		SSAPropagationCallGraphBuilder builder = Util.makeZeroOneContainerCFABuilder(options, new AnalysisCache(), cha, scope);

		long start_time = System.currentTimeMillis();
		CallGraph cg  = builder.makeCallGraph(options, null);
		PointerAnalysis<InstanceKey> pta = builder.getPointerAnalysis();
		System.out.println("Exhaustive Points-to Analysis Time: "+(System.currentTimeMillis()-start_time) + "ms");
		int numofCGNodes = cg.getNumberOfNodes();
		int totalInstanceKey = pta.getInstanceKeys().size();
		int totalPointerKey =((PointerAnalysisImpl)pta).getNumOfPointerKeys();
		int totalPointerEdge = 0;
		int totalClass=cha.getNumberOfClasses();
		Iterator<PointerKey> iter = pta.getPointerKeys().iterator();
		while(iter.hasNext()){
			PointerKey key = iter.next();
			int size = pta.getPointsToSet(key).size();
			totalPointerEdge+=size;
		}
		System.out.println("#Class: "+totalClass);
		System.out.println("#Method: "+numofCGNodes);
		System.out.println("#Pointer: "+totalPointerKey);
		System.out.println("#Object: "+totalInstanceKey);
		System.out.println("#Edges: "+totalPointerEdge);
		System.out.println();
		ps.println();

		System.out.println("Running Exhaustive Detection ... ");
		//detector
		ActorSystem akkasys = ActorSystem.create();
		ActorRef bughub = akkasys.actorOf(Props.create(BugHub.class, 1), "bughub");
		start_time = System.currentTimeMillis();
		PropagationGraph flowgraph = builder.getPropagationSystem().getPropagationGraph();
		engine = new TIDEEngine((includeAllMainEntryPoints? mainSignature:mainMethodSig), cg, flowgraph, pta, bughub);
		Set<ITIDEBug> bugs = engine.detectBothBugs(ps);

//		System.out.println("EXHAUSTIVE DETECTION >>>");
		int race = 0;
		int dl = 0;
		for(ITIDEBug bug : bugs){
			if(bug instanceof TIDERace){
				race++;
			}else if (bug instanceof TIDEDeadlock){
				dl++;
			}
		}
		System.out.println("Exhaustive Detection Time: " + (System.currentTimeMillis() - start_time) + "ms");
//		System.err.println("Exhaustive Race Detection Time: " + engine.timeForDetectingRaces);
//		System.err.println("Exhaustive Deadlock Detection Time: " + engine.timeForDetectingDL);
//		System.out.println("#Race: " + race + "  #Deadlock: " + dl);

		System.out.println("Running Incremental Points-to Analysis and Detection ... ");
		builder.getPropagationSystem().initializeAkkaSys(1);
		incrementalTest(builder, cg);
	}

	public static void runD4_48()
			throws IOException, ClassHierarchyException, IllegalArgumentException, CallGraphBuilderCancelException{
		print("D4-48", true);
		//dist
		DistributeMaster master = new DistributeMaster();
		master.startClusterSystem(benchmark);

		System.out.println("Running Exhaustive Points-to Analysis ... ");
		AnalysisScope scope = CallGraphTestUtil.makeJ2SEAnalysisScope(testFile, excludeFile);
		ClassHierarchy cha = ClassHierarchy.make(scope);
		Iterable<Entrypoint> entrypoints = findEntryPoints(cha,mainClassName,includeAllMainEntryPoints);
		AnalysisOptions options = new AnalysisOptions(scope, entrypoints);

		SSAPropagationCallGraphBuilder builder = Util.makeZeroOneContainerCFABuilder(options, new AnalysisCache(), cha, scope);

		long start_time = System.currentTimeMillis();
		CallGraph cg  = builder.makeCallGraph(options, null);
		PointerAnalysis<InstanceKey> pta = builder.getPointerAnalysis();
		System.out.println("Exhaustive Points-to Analysis Time: "+(System.currentTimeMillis()-start_time) + "ms");
		int numofCGNodes = cg.getNumberOfNodes();
		int totalInstanceKey = pta.getInstanceKeys().size();
		int totalPointerKey =((PointerAnalysisImpl)pta).getNumOfPointerKeys();
		int totalPointerEdge = 0;
		int totalClass=cha.getNumberOfClasses();
		Iterator<PointerKey> iter = pta.getPointerKeys().iterator();
		while(iter.hasNext()){
			PointerKey key = iter.next();
			int size = pta.getPointsToSet(key).size();
			totalPointerEdge+=size;
		}
		System.out.println("#Class: "+totalClass);
		System.out.println("#Method: "+numofCGNodes);
		System.out.println("#Pointer: "+totalPointerKey);
		System.out.println("#Object: "+totalInstanceKey);
		System.out.println("#Edges: "+totalPointerEdge);
		System.out.println();
		ps.println();

		System.out.println("Running Exhaustive Detection ... ");
		//detector
		ActorSystem akkasys = ActorSystem.create();
		ActorRef bughub = akkasys.actorOf(Props.create(BugHub.class, 1), "bughub");
		start_time = System.currentTimeMillis();
		PropagationGraph flowgraph = builder.getPropagationSystem().getPropagationGraph();
		engine = new TIDEEngine((includeAllMainEntryPoints? mainSignature:mainMethodSig), cg, flowgraph, pta, bughub);
		Set<ITIDEBug> bugs = engine.detectBothBugs(ps);

//		System.out.println("INITIAL DETECTION >>>");
		int race = 0;
		int dl = 0;
		for(ITIDEBug bug : bugs){
			if(bug instanceof TIDERace){
				race++;
			}else if (bug instanceof TIDEDeadlock){
				dl++;
			}
		}
//		System.err.println("Exhaustive Race Detection Time: " + engine.timeForDetectingRaces);
//		System.err.println("Exhaustive Deadlock Detection Time: " + engine.timeForDetectingDL);
//		System.out.println("#Race: " + race + "  #Deadlock: " + dl);
		master.awaitRemoteComplete();
		System.out.println("Exhaustive Detection Time: " + (System.currentTimeMillis() - start_time) + "ms");

		System.out.println("Running Incremental Points-to Analysis and Detection ... ");
		incrementalDistTest(master, builder, cg);
	}

	private static void incrementalDistTest(DistributeMaster master, SSAPropagationCallGraphBuilder builder, CallGraph cg) {
		Iterator<CGNode> iter2 = cg.iterator();
		HashSet<CGNode> storeCG = new HashSet<>();
		while(iter2.hasNext()){
			CGNode next = iter2.next();
			if(!next.getMethod().isSynthetic()){
				storeCG.add(next);
			}
		}

		for (CGNode n : storeCG) {
			if(!n.getMethod().getSignature().contains("com.ibm.wala")
					&& !n.getMethod().getSignature().contains(mainSignature)){
				if(!notreach){
					break;
				}
				IR ir = n.getIR();
				if(ir == null)
					continue;
				master.frontend.tell("METHOD:"+n.toString(), master.frontend);
				master.awaitRemoteComplete();
				if(nextNode){
					nextNode = false;
					continue;
				}
				SSAInstruction[] insts = ir.getInstructions();
				int size = insts.length;
				for(int i=size;i>0;i--){
					SSAInstruction inst = insts[i-1];
					if(inst==null)
						continue;//skip null
					master.frontend.tell("-STMT:"+inst.iindex, master.frontend);
					master.awaitRemoteComplete();
					master.frontend.tell("+STMT:"+inst.iindex, master.frontend);
					master.awaitRemoteComplete();
				}
			}
		}
		if(notreach){
			master.frontend.tell("PERFORMANCE", master.frontend);
			master.awaitRemoteComplete();
		}

		System.out.println("Complete D4-48 Evaluation for " + benchmark + ". Please see the log on remote server.");
	}

	static boolean nextNode = false;
	public static void nextCGNode() {
		nextNode = true;
	}

	static boolean notreach = true;
	public static void terminateEva() {
		notreach = false;
	}


	public static void incrementalTest(SSAPropagationCallGraphBuilder builder, CallGraph cg){
		Iterator<CGNode> iter2 = cg.iterator();
		HashSet<CGNode> storeCG = new HashSet<>();
		while(iter2.hasNext()){
			CGNode next = iter2.next();
			if(!next.getMethod().isSynthetic()){
				storeCG.add(next);
			}
		}

		boolean ptachanges = false;
		HashSet<CGNode> changedNodes = new HashSet<>();
		Set<ITIDEBug> bugs = new HashSet<>();
		for (CGNode n : storeCG) {
			if(!n.getMethod().getSignature().contains("com.ibm.wala")
					&& !n.getMethod().getSignature().contains(mainSignature)){
				builder.system.setChange(true);
				IR ir = n.getIR();
				if(ir == null)
					continue;

				DefUse du = new DefUse(ir);
				ConstraintVisitor v = builder.makeVisitor(n);
				v.setIR(ir);
				v.setDefUse(du);

				ControlFlowGraph<SSAInstruction, ISSABasicBlock> cfg = ir.getControlFlowGraph();
				SSAInstruction[] insts = ir.getInstructions();
				int size = insts.length;
				changedNodes.add(n);
//				System.out.println("DETECTION AGAIN >>> " + n.getMethod().toString());
				for(int i=size;i>0;i--){
					SSAInstruction inst = insts[i-1];

					if(inst==null)
						continue;//skip null

//					System.out.println("INST:      "+ inst.toString());
					ISSABasicBlock bb = cfg.getBlockForInstruction(inst.iindex);
					//delete
					try{

						builder.setDelete(true);
						builder.system.setFirstDel(true);
						v.setBasicBlock(bb);

						long delete_start_time = System.currentTimeMillis();
						inst.visit(v);
						//del
						builder.system.setFirstDel(false);
						do{
							builder.system.solveAkkaDel(null);
						}while(!builder.system.emptyWorkListAkka());
						builder.setDelete(false);
						HashSet<IVariable> resultsadd = builder.system.changes;
						if(resultsadd.size() > 0){
							ptachanges = true;
						}else{
							ptachanges = false;
						}
						long delete_end_time = System.currentTimeMillis();
						long deldetect_start_time = System.currentTimeMillis();
						long ptadelete_time = (deldetect_start_time - delete_start_time);
						long deldetect_time = 0;
						if(!benchmark.contains("fop")){
							engine.setDelete(true);
							bugs = engine.updateEngine2(changedNodes, ptachanges, inst, ps);
							engine.setDelete(false);
							deldetect_time = (delete_end_time - deldetect_start_time);
						}

						builder.system.clearChanges();
						//add
						long add_start_time = System.currentTimeMillis();
						inst.visit(v);
						do{
							builder.system.solveAkkaAdd(null);
							builder.addConstraintsFromNewNodes(null);
						} while (!builder.system.emptyWorkListAkka());

						HashSet<IVariable> resultsdel = builder.system.changes;
						if(resultsdel.size() > 0){
							ptachanges = true;
						}else{
							ptachanges = false;
						}
						long adddetect_start_time = System.currentTimeMillis();
						long add_end_time = System.currentTimeMillis();
						long ptaadd_time = (adddetect_start_time-add_start_time);
						long adddetect_time = 0;
						if(!benchmark.contains("fop")){
							bugs = engine.updateEngine(changedNodes, new HashSet<>(), ptachanges, ps);
							adddetect_time = (add_end_time - adddetect_start_time);
						}
						builder.system.clearChanges();
						ps.print(ptadelete_time+" "+ptaadd_time+" ");
						//incre_race_time+" "+incre_dl_time+" "+ptadelete_time+" "+ptaadd_time+" "
						totaltime = totaltime + ptadelete_time + ptaadd_time + deldetect_time + adddetect_time;
					}catch(Exception e)
					{
						e.printStackTrace();
					}
				}
				changedNodes.clear();
				ps.println();
			}
			if(totaltime >= 5400000)//7200000  5400000
				break;
		}
		totaltime = 0;

		DataAnalyze analyze = new DataAnalyze();
		try {
			analyze.analyze(benchmark);
		} catch (NumberFormatException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}


	public static Iterable<Entrypoint> findEntryPoints(IClassHierarchy classHierarchy, String mainClassName, boolean includeAll) {
		final Set<Entrypoint> result = HashSetFactory.make();
		Iterator<IClass> classIterator = classHierarchy.iterator();
		while (classIterator.hasNext()) {
			IClass klass = classIterator.next();
			if (!AnalysisUtils.isJDKClass(klass)) {
				for (IMethod method : klass.getDeclaredMethods()) {
					try {
						if(method.isStatic()&&method.isPublic()
								&&method.getName().toString().equals("main")
								&&method.getDescriptor().toString().equals(ConvertHandler.DESC_MAIN))
						{
							if(includeAll
									||klass.getName().toString().contains(mainClassName))
								result.add(new DefaultEntrypoint(method, classHierarchy));
						}
						else if(method.isPublic()&&!method.isStatic()
								&&method.getName().toString().equals("run")
								&&method.getDescriptor().toString().equals("()V"))
						{
							if (AnalysisUtils.implementsRunnableInterface(klass) || AnalysisUtils.extendsThreadClass(klass))
								result.add(new DefaultEntrypoint(method, classHierarchy));

						}
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				}
			}
		}

		return new Iterable<Entrypoint>() {
			public Iterator<Entrypoint> iterator() {
				return result.iterator();
			}
		};
	}


	public static void print(String msg, boolean printErr){
		try{
			if(ps==null)
				ps = new PrintStream(new FileOutputStream("log_" + benchmark));

			ps.println(msg);

			if(printErr)
				System.err.println(msg);

		}catch(Exception e){
			e.printStackTrace();
		}
	}



}
