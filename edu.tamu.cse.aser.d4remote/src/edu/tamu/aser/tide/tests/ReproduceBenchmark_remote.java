package edu.tamu.aser.tide.tests;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

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

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import edu.tamu.aser.tide.akkabug.BugHub;
import edu.tamu.aser.tide.engine.AnalysisUtils;
import edu.tamu.aser.tide.engine.ITIDEBug;
import edu.tamu.aser.tide.engine.TIDEEngine;
import edu.tamu.aser.tide.plugin.handlers.ConvertHandler;

public class ReproduceBenchmark_remote {
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
	static long scheduletime = 5400000;

	static SSAPropagationCallGraphBuilder builder;
	static CallGraph cg;

	static CGNode testCGNode;
	static HashSet<CGNode> changedNodes = new HashSet<>();
	static SSAInstruction[] insts;
	static ConstraintVisitor v;
	static ControlFlowGraph<SSAInstruction, ISSABasicBlock> cfg;

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


	public static boolean prepare(String tar) throws IOException, ClassHierarchyException, IllegalArgumentException, CallGraphBuilderCancelException {
		benchmark = tar;
		print(benchmark, true);
		print("D4-48", true);
		switch (tar) {
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

	    //short version
		case "avrora_short":
			mainClassName = "avrora/Main";
			mainMethodSig = "avrora.Main" + mainSignature;
			testFile = "data/avroratestfile.txt";
			excludeFile = "data/ShortDefaultExclusions.txt";
			scheduletime = 900000;
			break;
		case "batik_short":
			mainClassName = "rasterizer/Main";
			mainMethodSig = "rasterizer.Main" + mainSignature;
			testFile = "data/batiktestfile.txt";
			excludeFile = "data/ShortDefaultExclusions.txt";
			scheduletime = 900000;
			break;
		case "eclipse_short":
			mainClassName = "EclipseStarter";
			mainMethodSig = mainClassName + mainSignature;
			testFile = "data/eclipsetestfile.txt";
			excludeFile = "data/ShortDefaultExclusions.txt";
			scheduletime = 900000;
			break;
		case "fop_short": //no detection
			mainClassName = "TTFFile";
			mainMethodSig = mainClassName + mainSignature;
			testFile = "data/foptestfile.txt";
			excludeFile = "data/ShortDefaultExclusions.txt";
			scheduletime = 900000;
			break;
		case "h2_short":
			mainClassName = "Shell";
			mainMethodSig = mainClassName + mainSignature;
			testFile = "data/h2testfile.txt";
			excludeFile = "data/ShortDefaultExclusions.txt";
			scheduletime = 900000;
			break;
		case "jython_short":
			mainClassName = "jython";
			mainMethodSig = mainClassName + mainSignature;
			testFile = "data/jythontestfile.txt";
			excludeFile = "data/ShortDefaultExclusions.txt";
			scheduletime = 900000;
			break;
		case "luindex_short":
			mainClassName = "IndexFiles";
			mainMethodSig = mainClassName + mainSignature;
			excludeFile = "data/luindexexcludefileshort.txt";
			testFile = "data/dacapotestfile.txt";
			scheduletime = 900000;
			break;
		case "lusearch_short":
			mainClassName = "IndexHTML";
			mainMethodSig = mainClassName + mainSignature;
			excludeFile = "data/lusearchexcludefileshort.txt";
			testFile = "data/dacapotestfile.txt";
			scheduletime = 900000;
			break;
		case "pmd_short":
			mainClassName = "GUI";
			mainMethodSig = mainClassName + mainSignature;
			testFile = "data/pmdtestfile.txt";
			excludeFile = "data/ShortDefaultExclusions.txt";
			scheduletime = 900000;
			break;
		case "sunflow_short":
			mainClassName = "Benchmark";
			mainMethodSig = mainClassName + mainSignature;
			testFile = "data/sunflowtestfile.txt";
			excludeFile = "data/ShortDefaultExclusions.txt";
			scheduletime = 900000;
			break;
		case "tomcat_short":
			mainClassName = "ExpressionDemo";
			mainMethodSig = mainClassName + mainSignature;
			testFile = "data/dacapotestfile.txt";
			excludeFile = "data/tomcatexcludefileshort.txt";
			scheduletime = 900000;
			break;
		case "tradebeans_short":
			mainClassName = "REUtil";
			mainMethodSig = mainClassName + mainSignature;
			testFile = "data/dacapotestfile.txt";
			excludeFile = "data/tradebeansexcludefileshort.txt";
			scheduletime = 900000;
			break;
		case "tradesoap_short":
			mainClassName = "REUtil";
			mainMethodSig = mainClassName + mainSignature;
			testFile = "data/dacapotestfile.txt";
			excludeFile = "data/tradesoapexcludefileshort.txt";
			scheduletime = 900000;
			break;
		case "xalan_short":
			mainClassName = "XSLProcessorVersion";
			mainMethodSig = mainClassName + mainSignature;
			testFile = "data/xalantestfile.txt";
			excludeFile = "data/ShortDefaultExclusions.txt";
			scheduletime = 900000;
			break;


		default:
			throw new IllegalArgumentException("Invalid argument: " + tar);
		}

		AnalysisScope scope = CallGraphTestUtil.makeJ2SEAnalysisScope(testFile, excludeFile);
		ClassHierarchy cha = ClassHierarchy.make(scope);
		Iterable<Entrypoint> entrypoints = findEntryPoints(cha,mainClassName,includeAllMainEntryPoints);
		AnalysisOptions options = new AnalysisOptions(scope, entrypoints);

		builder = Util.makeZeroOneContainerCFABuilder(options, new AnalysisCache(), cha, scope);

		cg  = builder.makeCallGraph(options, null);
		PointerAnalysis<InstanceKey> pta = builder.getPointerAnalysis();
		System.out.println();
		ps.println();

		//detector
		ActorSystem akkasys = ActorSystem.create();
		ActorRef bughub = akkasys.actorOf(Props.create(BugHub.class, 48), "bughub");
		PropagationGraph flowgraph = builder.getPropagationSystem().getPropagationGraph();
		engine = new TIDEEngine((includeAllMainEntryPoints? mainSignature:mainMethodSig), cg, flowgraph, pta, bughub);
		engine.detectBothBugs(ps);

		builder.getPropagationSystem().initializeAkkaSys(48);
		return true;
	}


	public static int locateCGNode(String cgnode) {
		Iterator<CGNode> iter = cg.iterator();
		boolean locate = false;
		while(iter.hasNext()){
			CGNode next = iter.next();
			if(next.toString().equals(cgnode)){
				changedNodes.clear();
				ps.println();

				testCGNode = next;
				builder.system.setChange(true);
				IR ir = testCGNode.getIR();

				DefUse du = new DefUse(ir);
				v = builder.makeVisitor(testCGNode);
				v.setIR(ir);
				v.setDefUse(du);

				cfg = ir.getControlFlowGraph();
				insts = ir.getInstructions();
				changedNodes.add(next);
				locate = true;
			}
		}
		if(totaltime >= scheduletime){//900000  5400000
			totaltime = 0;
			return 0;
		}
		if(locate)
			return 1;
		else
			return 2;
	}

	public static boolean delete(String stmt_idx) {
		int idx = Integer.parseInt(stmt_idx);
		if(insts.length <= idx)
			return true;
		SSAInstruction inst = insts[idx];
		if(inst==null)
			return true;//skip null
		ISSABasicBlock bb = cfg.getBlockForInstruction(inst.iindex);
		//delete
		try{
			boolean ptachanges = false;
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
			long deldetect_start_time = System.currentTimeMillis();
			long ptadelete_time = (deldetect_start_time - delete_start_time);
			long deldetect_time = 0;
			if(!benchmark.contains("fop")){
				engine.setDelete(true);
				Set<ITIDEBug> bugs = engine.updateEngine2(changedNodes, ptachanges, inst, ps);
				engine.setDelete(false);
				deldetect_time = (System.currentTimeMillis() - deldetect_start_time);
			}else{
				ps.print(0+" "+0+" ");
			}

			builder.system.clearChanges();
			ps.print(ptadelete_time+" ");

			totaltime = totaltime + ptadelete_time  + deldetect_time;
		}catch(Exception e)
		{
			e.printStackTrace();
		}
		return true;
	}


	public static boolean add(String stmt_idx) {
		int idx = Integer.parseInt(stmt_idx);
		if(insts.length <= idx)
			return true;
		SSAInstruction inst = insts[idx];
		if(inst==null)
			return true;//skip null
		ISSABasicBlock bb = cfg.getBlockForInstruction(inst.iindex);
		//delete
		try{
			boolean ptachanges = false;
			builder.setDelete(true);
			builder.system.setFirstDel(true);
			v.setBasicBlock(bb);

			long delete_start_time = System.currentTimeMillis();
			inst.visit(v);
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
			long ptaadd_time = (adddetect_start_time-add_start_time);
			long adddetect_time = 0;
			if(!benchmark.contains("fop")){
				HashSet<ITIDEBug> bugs = engine.updateEngine(changedNodes, new HashSet<>(), ptachanges, ps);
				adddetect_time = (System.currentTimeMillis() - adddetect_start_time);
			}else{
				ps.print(0+" "+0+" ");
			}

			builder.system.clearChanges();
			ps.print(ptaadd_time+" ");

			totaltime = totaltime + ptaadd_time  + adddetect_time;
		}catch(Exception e)
		{
			e.printStackTrace();
		}

		return true;
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


	public static void performance() {
		DataAnalyze analyze = new DataAnalyze();
		try {
			analyze.analyze(benchmark);
		} catch (NumberFormatException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}



}
