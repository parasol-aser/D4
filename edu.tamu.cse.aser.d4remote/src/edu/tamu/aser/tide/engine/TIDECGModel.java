/**
 * This file is licensed under the University of Illinois/NCSA Open Source License. See LICENSE.TXT for details.
 */
package edu.tamu.aser.tide.engine;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;

import com.ibm.wala.cast.ipa.callgraph.AstCallGraph;
import com.ibm.wala.cast.ipa.callgraph.AstCallGraph.AstCGNode;
import com.ibm.wala.cfg.ControlFlowGraph;
import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.IMethod.SourcePosition;
import com.ibm.wala.eclipse.cg.model.WalaProjectCGModel;
import com.ibm.wala.ide.util.JdtPosition;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.callgraph.propagation.PropagationGraph;
import com.ibm.wala.ipa.callgraph.propagation.PropagationSystem;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAArrayReferenceInstruction;
import com.ibm.wala.ssa.SSAFieldAccessInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.graph.InferGraphRoots;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import edu.tamu.aser.tide.akkasys.BugHub;
import edu.tamu.aser.tide.shb.SHBEdge;
import edu.tamu.aser.tide.shb.SHBGraph;
import edu.tamu.aser.tide.marker.BugMarker;
import edu.tamu.aser.tide.plugin.handlers.ConvertHandler;
import edu.tamu.aser.tide.tests.ReproduceBenchmark_remote;
import edu.tamu.aser.tide.nodes.DLockNode;
import edu.tamu.aser.tide.nodes.INode;
import edu.tamu.aser.tide.nodes.MemNode;
import edu.tamu.aser.tide.nodes.MethodNode;
import edu.tamu.aser.tide.nodes.ReadNode;
import edu.tamu.aser.tide.nodes.StartNode;
import edu.tamu.aser.tide.nodes.SyncNode;
import edu.tamu.aser.tide.nodes.WriteNode;
import scala.collection.generic.BitOperations.Int;

public class TIDECGModel extends WalaProjectCGModel {

	public AnalysisCache getCache()
	{
		return engine.getCache();
	}
	public AnalysisOptions getOptions()
	{
		return engine.getOptions();
	}
	private String entrySignature;

	public TIDECGModel(IJavaProject project, String exclusionsFile, String mainMethodSignature) throws IOException, CoreException {
		super(project, exclusionsFile);
		this.entrySignature = mainMethodSignature;
	}


	public TIDEEngine getEngine(){
		return bugEngine;
	}

	//start bug akka system
	int nrOfWorkers = 8;//8;
	public ActorSystem akkasys;
	public ActorRef bughub;
	public static TIDEEngine bugEngine;
	private static ClassLoader ourClassLoader = ActorSystem.class.getClassLoader();
	private final static boolean DEBUG = false;// true;

	public HashSet<ITIDEBug> detectBug() {
	    Thread.currentThread().setContextClassLoader(ourClassLoader);
		akkasys = ActorSystem.create("bug");
		CallGraphBuilder builder = engine.builder_echo;
		PropagationGraph flowgraph = null;
		if(builder instanceof SSAPropagationCallGraphBuilder){
			flowgraph = ((SSAPropagationCallGraphBuilder) builder).getPropagationSystem().getPropagationGraph();
		}
		//initial bug engine
		bughub = akkasys.actorOf(Props.create(BugHub.class, nrOfWorkers), "bughub");
		bugEngine = new TIDEEngine(entrySignature, callGraph, flowgraph, engine.getPointerAnalysis(), bughub);
		bugEngine.setChange(false);
		//detect bug
		return bugEngine.detectBothBugs(null);
	}

	public HashSet<ITIDEBug> detectBugAgain(HashSet<CGNode> changedNodes, HashSet<CGNode> changedModifiers, boolean ptachanges) {
		//update bug engine
		bugEngine.setChange(true);
		return bugEngine.updateEngine(changedNodes, changedModifiers, ptachanges, null);
	}


	HashMap<String, HashSet<IMarker>> bug_marker_map = new HashMap<>();

	public void addBugMarkersForConsider(HashSet<ITIDEBug> considerbugs, IFile file) throws CoreException {
		IPath fullPath = file.getProject().getFullPath();//full path of the project
		for (ITIDEBug add : bugEngine.addedbugs) {
			if(add instanceof TIDERace)
				showRace(fullPath, (TIDERace) add);
		}
//		updateEchoView();
	}

	public void removeBugMarkersForIgnore(HashSet<ITIDEBug> removedbugs){
		String key;
		for (ITIDEBug bug : removedbugs) {
			if(bug instanceof TIDERace){
				TIDERace race = (TIDERace) bug;
				key = race.raceMsg;
			}else{
				TIDEDeadlock dl = (TIDEDeadlock) bug;
			    key = dl.deadlockMsg;
			}
			HashSet<IMarker> markers = bug_marker_map.get(key);
			for (IMarker marker : markers) {
				try {
					IMarker[] dels = new IMarker[1];
					dels[0] = marker;
					IWorkspace workspace = marker.getResource().getWorkspace();
					workspace.deleteMarkers(dels);
					if(marker.exists()){//
						System.out.println("MARKER did not deleted!!!");
					}
				} catch (CoreException e) {
					e.printStackTrace();
				}
			}
			bug_marker_map.remove(key);
		}
	}


	private void showDeadlock(IPath fullPath, TIDEDeadlock bug) throws CoreException {
		// TODO Auto-generated method stub

		DLockNode l11 = bug.lp1.lock1;
		DLockNode l12 = bug.lp1.lock2;
		DLockNode l21 = bug.lp2.lock1;
		DLockNode l22 = bug.lp2.lock2;

		String sig11 = l11.getInstSig();
		String sig12 = l12.getInstSig();
		String sig21 = l21.getInstSig();
		String sig22 = l22.getInstSig();

		int line11 = l11.getLine();
		int line12 = l12.getLine();
		int line21 = l21.getLine();
		int line22 = l22.getLine();

		String deadlockMsg = "Deadlock: ("+sig11  +","+sig12+ ";  "+sig21+ ","+sig22+ ")";
		System.err.println(deadlockMsg);
		bug.setBugInfo(deadlockMsg, null, null);
	}

	private void showRace(IPath fullPath, TIDERace race) throws CoreException {
		String sig = race.sig;
		MemNode rnode = race.node1;
		MemNode wnode = race.node2;
		int findex = sig.indexOf('.');
		int lindex = sig.lastIndexOf('.');
		if(findex!=lindex)
			sig =sig.substring(0, lindex);//remove instance hashcode

		String raceMsg = "Race: "+sig+" ("+rnode.getSig()+", "+wnode.getSig()+")";
		System.err.println(raceMsg + rnode.getObjSig().toString());
		race.setBugInfo(raceMsg, null, null);
	}


	private String obtainFixOfRace(TIDERace race) {
		MemNode node1 = race.node1;
		MemNode node2 = race.node2;
		String prefix1 = node1.getPrefix();
		String prefix2 = node2.getPrefix();
		FieldReference field1 = null;
		FieldReference field2 = null;
		if(node1.inst instanceof SSAFieldAccessInstruction){
			field1 = ((SSAFieldAccessInstruction)node1.inst).getDeclaredField();
		}
		if(node2.inst instanceof SSAFieldAccessInstruction){
			field2 = ((SSAFieldAccessInstruction)node2.inst).getDeclaredField();
		}

		StringBuffer sb = new StringBuffer("Fix Suggestion Of Race: ");
		if(prefix1.equals(prefix2)){
			if(field1 == null || field2 == null){
				//array
				String class1 = node1.getSig().substring(0, node1.getSig().indexOf(':'));
				String class2 = node2.getSig().substring(0, node2.getSig().indexOf(':'));
				if(class1.equals(class2)){
					//same var
					sb.append("add synchronizations to protect the array: " +
							" in " + class1.toString());
				}else{
					sb.append("add common synchronizations to protect the array: " +
							" in " + class1.toString() + " and " + class2.toString());
				}
			}else{
				//field
				String class1 = field1.getDeclaringClass().toString();
				String class2 = field2.getDeclaringClass().toString();
				if(class1.equals(class2)){
					sb.append("add synchronizations to protect: " + field1.getName().toString() +
							" in " + class1.toString().substring(class1.indexOf("L")+1, class1.length()-1));
				}else{
					sb.append("add common synchronizations to protect: " + field1.getName().toString() +
							" in " + class1.substring(class1.indexOf("L")+1, class1.length()-1)
							+ " and " + field2.getName().toString()
							+ class2.substring(class2.indexOf("L")+1, class2.length()-1));
				}
			}
		}else{
			//need to find the common pointer
			System.out.println(" == no suggestion now.");
			sb.append("no suggestion now.");
		}
		return new String(sb);
	}


	private Iterable<Entrypoint> entryPoints;
	@Override
	protected Iterable<Entrypoint> getEntrypoints(AnalysisScope analysisScope, IClassHierarchy classHierarchy) {
		if(entryPoints==null){
			entryPoints = findEntryPoints(classHierarchy,entrySignature);
		}
		return entryPoints;
	}

	public Iterable<Entrypoint> findEntryPoints(IClassHierarchy classHierarchy, String entrySignature) {
		final Set<Entrypoint> result = HashSetFactory.make();
		Iterator<IClass> classIterator = classHierarchy.iterator();
		while (classIterator.hasNext()) {
			IClass klass = classIterator.next();
			if (!AnalysisUtils.isJDKClass(klass)) {
				// Logger.log("Visiting class " + klass);
				//String classname = klass.getName().getClassName().toString();
				//String classpackage = klass.getName().getPackage().toString();

				for (IMethod method : klass.getDeclaredMethods()) {
					try {
//						if (!(method instanceof ShrikeCTMethod)) {
//							throw new RuntimeException("@EntryPoint only works for byte code.");
//						}
						//String methodname = method.getName().toString();
						if(method.isStatic()&&method.isPublic()
								&&method.getName().toString().equals("main")
								&&method.getDescriptor().toString().equals(ConvertHandler.DESC_MAIN))

							result.add(new DefaultEntrypoint(method, classHierarchy));
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

	@Override
	protected Collection<CGNode> inferRoots(CallGraph cg) throws WalaException {
		return InferGraphRoots.inferRoots(cg);
	}

	public PointerAnalysis getPointerAnalysis() {
		return engine.getPointerAnalysis();
	}

	public IClassHierarchy getClassHierarchy() {
		return engine.getClassHierarchy();
	}

	public CGNode getOldCGNode(com.ibm.wala.classLoader.IMethod m_old){
		CGNode node = null;
			AstCallGraph cg = (AstCallGraph)callGraph;
			try {
				node = cg.findOrCreateNode(m_old, Everywhere.EVERYWHERE);
			} catch (CancelException e) {
			}
		return node;
	}

	public CGNode updateCallGraph(com.ibm.wala.classLoader.IMethod m_old,
			com.ibm.wala.classLoader.IMethod m, IR ir) {
		CGNode node = null;
		try{
			AstCallGraph cg = (AstCallGraph)callGraph;
			CGNode oldNode = cg.findOrCreateNode(m_old, Everywhere.EVERYWHERE);
			if(oldNode instanceof AstCGNode){
				AstCGNode astnode = (AstCGNode) oldNode;
				astnode.updateMethod(m, ir);
				//update call graph key
				cg.updateNode(m_old, m, Everywhere.EVERYWHERE, astnode);
				//update call site?
				astnode.clearAllTargets();//clear old targets
				if(engine.builder_echo!=null &&
						engine.builder_echo instanceof SSAPropagationCallGraphBuilder){
					SSAPropagationCallGraphBuilder builder = (SSAPropagationCallGraphBuilder) engine.builder_echo;
					builder.system.setUpdateChange(true);
					builder.addConstraintsFromChangedNode(astnode, null);
					PropagationSystem system = builder.system;
					do{
						system.solve(null);
					}while(!system.emptyWorkList());
					builder.system.setUpdateChange(false);
				}
			}
			node = oldNode;
			//System.out.println("DEBUG oldNode: "+System.identityHashCode(oldNode));

			//indicate builder has changed

			//engine.builder_echo.markChanged(oldNode);

//			CGNode newNode = cg.findOrCreateNode(m, Everywhere.EVERYWHERE);
//
//			cg.addNode(newNode);
//
//			Iterator<CGNode> iter = cg.getSuccNodes(oldNode);
//			cg.removeNode(oldNode);
//			while(iter.hasNext())
//			{
//				cg.addEdge(newNode, iter.next());
//			}

		}catch(Exception e)
		{
			e.printStackTrace();
		}
		return node;
	}



	public void updatePointerAnalysis(CGNode node, IR ir_old, IR ir) {

    	//compute diff
    	SSAInstruction[] insts_old = ir_old.getInstructions();
    	SSAInstruction[] insts = ir.getInstructions();

    	HashMap<String,SSAInstruction> mapOld = new HashMap<String,SSAInstruction>();
    	HashMap<String,SSAInstruction> mapNew = new HashMap<String,SSAInstruction>();


        ControlFlowGraph<SSAInstruction, ISSABasicBlock> cfg_old = ir_old.getControlFlowGraph();
        ControlFlowGraph<SSAInstruction, ISSABasicBlock> cfg_new = ir.getControlFlowGraph();

//        for (Iterator<ISSABasicBlock> x = cfg.iterator(); x.hasNext();) {
//          BasicBlock b = (BasicBlock) x.next();
//       // visit each instruction in the basic block.
//          for (Iterator<SSAInstruction> it = b.iterator(); it.hasNext();) {
//        	  SSAInstruction inst = it.next();
//              if (inst != null) {
//
//            	  String str = inst.toString();
//        			if(str.indexOf('@')>0)
//        				str = str.substring(0,str.indexOf('@')-1);
//        			mapOld.put(str, inst);
//              }
//          }
//        }

    	for(int i=0;i<insts_old.length;i++)
    	{
    		SSAInstruction inst = insts_old[i];
    		if(inst!=null)
    		{
    			String str = inst.toString();
    			//TODO: JEFF  -- program counter may change, call graph
//    			if(str.indexOf('@')>0)
//    				str = str.substring(0,str.indexOf('@')-1);
    			mapOld.put(str, inst);
    		}
    	}
    	for(int i=0;i<insts.length;i++)
    	{
    		SSAInstruction inst = insts[i];
    		if(inst!=null)
    		{
    			String str = inst.toString();
    			//TODO: JEFF
//    			if(str.indexOf('@')>0)
//    				str = str.substring(0,str.indexOf('@')-1);

    			mapNew.put(str, inst);
    		}
    	}
    	//NOT WORKING
    	//int use = insts[6].getUse(1);
    	//SSAConversion.undoCopyPropagation( (AstIR)ir, 6, use);

    	HashMap<SSAInstruction,ISSABasicBlock> deleted = new HashMap<SSAInstruction,ISSABasicBlock>();
    	HashMap<SSAInstruction,ISSABasicBlock> added = new HashMap<SSAInstruction,ISSABasicBlock>();

    	for(String s:mapOld.keySet())
    	{
    		if(!mapNew.keySet().contains(s))//removed from new
    		{
    			SSAInstruction inst = mapOld.get(s);

    			if(inst instanceof SSAFieldAccessInstruction
    					|| inst instanceof SSAAbstractInvokeInstruction
    					|| inst instanceof SSAArrayReferenceInstruction
    					)
    			{
        			ISSABasicBlock bb = cfg_old.getBlockForInstruction(inst.iindex);
        			deleted.put(inst,bb);
    			}

    		}
    	}
    	for(String s:mapNew.keySet())
    	{
    		if(!mapOld.keySet().contains(s))//removed from new
    		{
    			SSAInstruction inst = mapNew.get(s);
    			ISSABasicBlock bb = cfg_new.getBlockForInstruction(inst.iindex);
    			added.put(inst,bb);
    		}
    	}
    	//added.removeAll(mapOld.keySet());
    	//deleted.removeAll(mapNew.keySet());


//    	if(false)
//        	{
//        	if(!deleted.isEmpty())
//        	{
//        		System.err.println("Deleted Instructions");
//
//	        	for(Object o: deleted.keySet())
//	        		System.out.println(o);
//        	}
//        	if(!added.isEmpty())
//        	{
//        		System.err.println("Added Instructions");
//	        	for(Object o: added.keySet())
//	        		System.out.println(o);
//        	}
//    	}


//    	int size_old = insts_old.length;
//    	int size = insts.length;
//    	for(int i=0, j=0;i<size_old&&j<size;)
//    	{
//    		SSAInstruction i_old = insts_old[i];
//    		SSAInstruction i_new = insts[i];
//    		if(!i_old.equals(i_new))
//    		{
//
//    		}
//    	}

    	//UPDATE IR, CALL GRAPH, CHA, PTA
//    	model.getCache().getSSACache().updateMethodIR(m_old, Everywhere.EVERYWHERE, model.getOptions().getSSAOptions(),ir);

//    	HashSet set = new HashSet();
//    	set.add(class_old);
//    	loader_old.removeAll(set);

		engine.updatePointerAnalaysis(node, added,deleted,ir_old, ir);
	}

	public void clearChanges() {
		((SSAPropagationCallGraphBuilder) engine.builder_echo).system.clearChanges();
	}


}
