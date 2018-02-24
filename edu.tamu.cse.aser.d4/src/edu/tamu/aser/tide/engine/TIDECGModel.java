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

import org.eclipse.core.internal.resources.Marker;
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
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.IMethod.SourcePosition;
import com.ibm.wala.classLoader.ShrikeCTMethod;
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
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.NormalAllocationInNode;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
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
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSAMonitorInstruction;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.graph.InferGraphRoots;
import com.ibm.wala.util.intset.OrdinalSet;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import edu.tamu.aser.tide.graph.LockSetEngine;
import edu.tamu.aser.tide.graph.ReachabilityEngine;
import edu.tamu.aser.tide.graph.SHBEdge;
import edu.tamu.aser.tide.graph.SHBGraph;
import edu.tamu.aser.tide.marker.BugMarker;
import edu.tamu.aser.tide.plugin.Activator;
import edu.tamu.aser.tide.plugin.ChangedItem;
import edu.tamu.aser.tide.plugin.handlers.ConvertHandler;
import edu.tamu.aser.tide.tests.ReproduceBenchmarks;
import edu.tamu.aser.tide.trace.MemNode;
import edu.tamu.aser.tide.trace.MethodNode;
import edu.tamu.aser.tide.trace.DLLockPair;
import edu.tamu.aser.tide.trace.DLockNode;
import edu.tamu.aser.tide.trace.HandlerChanges;
import edu.tamu.aser.tide.trace.INode;
import edu.tamu.aser.tide.trace.SyncNode;
import edu.tamu.aser.tide.trace.JoinNode;
import edu.tamu.aser.tide.trace.LockPair;
import edu.tamu.aser.tide.trace.ReadNode;
import edu.tamu.aser.tide.trace.StartNode;
import edu.tamu.aser.tide.trace.WriteNode;
import edu.tamu.aser.tide.views.BugDetail;
import edu.tamu.aser.tide.views.EchoDLView;
import edu.tamu.aser.tide.views.EchoRaceView;
import edu.tamu.aser.tide.views.EchoReadWriteView;
import edu.tamu.aser.tide.views.ExcludeView;
import edu.tamu.aser.tide.akkasys.BugHub;
import scala.collection.script.Start;

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
		echoRaceView = (EchoRaceView) PlatformUI.getWorkbench().getActiveWorkbenchWindow()
				.getActivePage().showView("edu.tamu.aser.tide.views.echoraceview");
		echoDLView = (EchoDLView) PlatformUI.getWorkbench().getActiveWorkbenchWindow()
				.getActivePage().showView("edu.tamu.aser.tide.views.echodlview");
		echoRWView = (EchoReadWriteView) PlatformUI.getWorkbench().getActiveWorkbenchWindow()
				.getActivePage().showView("edu.tamu.aser.tide.views.echotableview");
	}

	public EchoRaceView getEchoRaceView(){
		return echoRaceView;
	}

	public EchoReadWriteView getEchoRWView(){
		return echoRWView;
	}

	public EchoDLView getEchoDLView(){
		return echoDLView;
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
	//gui view
	public EchoRaceView echoRaceView;
	public EchoDLView echoDLView;
	public EchoReadWriteView echoRWView;

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

	public HashSet<ITIDEBug> ignoreCGNodes(HashSet<CGNode> ignoreNodes) {
		bugEngine.setChange(true);
		return bugEngine.ignoreCGNodes(ignoreNodes);
	}

	public HashSet<ITIDEBug> considerCGNodes(HashSet<CGNode> considerNodes) {
		bugEngine.setChange(true);
		return bugEngine.considerCGNodes(considerNodes);
	}


	HashMap<String, HashSet<IMarker>> bug_marker_map = new HashMap<>();

	public void updateGUI(IJavaProject project, IFile file, Set<ITIDEBug> bugs, boolean initial) {
		try{
			if(initial){
				//remove all markers in previous checks
				IMarker[] markers0 = project.getResource().findMarkers(BugMarker.TYPE_SCARIEST, true, 3);
				IMarker[] markers1 = project.getResource().findMarkers(BugMarker.TYPE_SCARY, true, 3);
				for (IMarker marker : markers0) {
					marker.delete();
				}
				for (IMarker marker : markers1) {
					marker.delete();
				}
				//create new markers
				IPath fullPath = file.getProject().getFullPath();//full path of the project
				if(bugs.isEmpty())
					System.err.println(" _________________NO BUGS ________________");

				for(ITIDEBug bug:bugs){
					if(bug instanceof TIDERace)
						showRace(fullPath, (TIDERace) bug);
					else
						showDeadlock(fullPath,(TIDEDeadlock) bug);
				}
				//
				initialEchoView(bugs);
			}else{
				if(bugEngine.removedbugs.isEmpty() && bugEngine.addedbugs.isEmpty())
					return;
				//remove deleted markers
				for (ITIDEBug removed : bugEngine.removedbugs) {//not work => update by compilationunit
					String key;
					if(removed instanceof TIDERace){
						TIDERace race = (TIDERace) removed;
						key = race.raceMsg;
					}else{
						TIDEDeadlock dl = (TIDEDeadlock) removed;
					    key = dl.deadlockMsg;
					}
					HashSet<IMarker> markers = bug_marker_map.get(key);
					if(markers != null){
						for (IMarker marker : markers) {
							try {
								IMarker[] dels = new IMarker[1];
								dels[0] = marker;
								IWorkspace workspace = marker.getResource().getWorkspace();
								workspace.deleteMarkers(dels);
							} catch (CoreException e) {
								e.printStackTrace();
							}
						}
						bug_marker_map.remove(key);
					}
				}
				//show up new markers
				IPath fullPath = file.getProject().getFullPath();//full path of the project
				for (ITIDEBug add : bugEngine.addedbugs) {
					if(add instanceof TIDERace)
						showRace(fullPath, (TIDERace) add);
					else
						showDeadlock(fullPath,(TIDEDeadlock) add);
				}
				updateEchoView();
			}

		}catch (Exception e) {
			e.printStackTrace();
		}
	}

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

	private void initialEchoView(Set<ITIDEBug> bugs) {
		new Thread(new Runnable() {
			public void run() {
				while (true) {
					try { Thread.sleep(100);} catch (Exception e) {System.err.println(e);}
					Display.getDefault().asyncExec(new Runnable() {
						public void run() {
							echoRaceView.initialGUI(bugs);
						}
					});
					break;
				}
			}
		}).start();
		new Thread(new Runnable() {
			public void run() {
				while (true) {
					try { Thread.sleep(100);} catch (Exception e) {System.err.println(e);}
					Display.getDefault().asyncExec(new Runnable() {
						public void run() {
							echoDLView.initialGUI(bugs);
						}
					});
					break;
				}
			}
		}).start();
		new Thread(new Runnable() {
			public void run() {
				while (true) {
					try { Thread.sleep(100);} catch (Exception e) {System.err.println(e);}
					Display.getDefault().asyncExec(new Runnable() {
						public void run() {
							echoRWView.initialGUI(bugs);
						}
					});
					break;
				}
			}
		}).start();
	}

	private void updateEchoView() {
		new Thread(new Runnable() {
			public void run() {
				while (true) {
					try { Thread.sleep(100);} catch (Exception e) {e.printStackTrace();}
					Display.getDefault().asyncExec(new Runnable() {
						public void run() {
							//do update
							echoRaceView.updateGUI(bugEngine.addedbugs, bugEngine.removedbugs);
						}
					});
					break;
				}
			}
		}).start();
		new Thread(new Runnable() {
			public void run() {
				while (true) {
					try { Thread.sleep(100);} catch (Exception e) {e.printStackTrace();}
					Display.getDefault().asyncExec(new Runnable() {
						public void run() {
							//do update
							echoDLView.updateGUI(bugEngine.addedbugs, bugEngine.removedbugs);
						}
					});
					break;
				}
			}
		}).start();
		new Thread(new Runnable() {
			public void run() {
				while (true) {
					try { Thread.sleep(100);} catch (Exception e) {e.printStackTrace();}
					Display.getDefault().asyncExec(new Runnable() {
						public void run() {
							//do update
							echoRWView.updateGUI(bugEngine.addedbugs, bugEngine.removedbugs);
						}
					});
					break;
				}
			}
		}).start();
	}

	public void updateEchoViewForIgnore() {
		new Thread(new Runnable() {
			public void run() {
				while (true) {
					try { Thread.sleep(100);} catch (Exception e) {e.printStackTrace();}
					Display.getDefault().asyncExec(new Runnable() {
						public void run() {
							//do update
							echoRaceView.ignoreBugs(bugEngine.removedbugs);
						}
					});
					break;
				}
			}
		}).start();
		new Thread(new Runnable() {
			public void run() {
				while (true) {
					try { Thread.sleep(100);} catch (Exception e) {e.printStackTrace();}
					Display.getDefault().asyncExec(new Runnable() {
						public void run() {
							//do update
							echoRWView.ignoreBugs(bugEngine.removedbugs);
						}
					});
					break;
				}
			}
		}).start();
		new Thread(new Runnable() {
			public void run() {
				while (true) {
					try { Thread.sleep(100);} catch (Exception e) {e.printStackTrace();}
					Display.getDefault().asyncExec(new Runnable() {
						public void run() {
							//do update
							echoDLView.ignoreBugs(bugEngine.removedbugs);
						}
					});
					break;
				}
			}
		}).start();
	}

	public void updateEchoViewForConsider() {
		new Thread(new Runnable() {
			public void run() {
				while (true) {
					try { Thread.sleep(200);} catch (Exception e) {e.printStackTrace();}
					Display.getDefault().asyncExec(new Runnable() {
						public void run() {
							//do update
							echoRaceView.considerBugs(bugEngine.addedbugs);
						}
					});
					break;
				}
			}
		}).start();
		new Thread(new Runnable() {
			public void run() {
				while (true) {
					try { Thread.sleep(200);} catch (Exception e) {e.printStackTrace();}
					Display.getDefault().asyncExec(new Runnable() {
						public void run() {
							//do update
							echoRWView.considerBugs(bugEngine.addedbugs);
						}
					});
					break;
				}
			}
		}).start();
		new Thread(new Runnable() {
			public void run() {
				while (true) {
					try { Thread.sleep(200);} catch (Exception e) {e.printStackTrace();}
					Display.getDefault().asyncExec(new Runnable() {
						public void run() {
							//do update
							echoDLView.considerBugs(bugEngine.addedbugs);
						}
					});
					break;
				}
			}
		}).start();
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
		ArrayList<LinkedList<String>> traceMsg = obtainTraceOfDeadlock(bug);
		String fixMsg = obtainFixOfDeadlock(bug);
		bug.setBugInfo(deadlockMsg, traceMsg, fixMsg);
//		System.out.println(traceMsg);

		IFile file11 = l11.getFile();
		if(file11 == null){
			file11 = getFileFromSig(fullPath,sig11);
		}
		IFile file12 = l12.getFile();
		if(file12 == null){
			file12 = getFileFromSig(fullPath,sig12);
		}
		IFile file21 = l21.getFile();
		if(file21 == null){
			file21 = getFileFromSig(fullPath,sig21);
		}
		IFile file22 = l22.getFile();
		if(file22 == null){
			file22 = getFileFromSig(fullPath,sig22);
		}

		IMarker marker1 = createMarkerDL(file11,line11,deadlockMsg);
		IMarker marker2 = createMarkerDL(file12,line12,deadlockMsg);
		IMarker marker3 = createMarkerDL(file21,line21,deadlockMsg);
		IMarker marker4 = createMarkerDL(file22,line22,deadlockMsg);

		//store
		HashSet<IMarker> newMarkers = new HashSet<>();
		newMarkers.add(marker1);
		newMarkers.add(marker2);
		newMarkers.add(marker3);
		newMarkers.add(marker4);
		bug_marker_map.put(deadlockMsg, newMarkers);
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
		ArrayList<LinkedList<String>> traceMsg = obtainTraceOfRace(race);
		String fixMsg = obtainFixOfRace(race);
		race.setBugInfo(raceMsg, traceMsg, fixMsg);
//		System.out.println(traceMsg);

		IFile file1 = rnode.getFile();
		if(file1 == null){
			file1 = getFileFromSig(fullPath,rnode.getSig());
		}
		IMarker marker1 = createMarkerRace(file1, rnode.getLine(), raceMsg);

		IFile file2 = wnode.getFile();
		if(file2 == null){
			file2 = getFileFromSig(fullPath,wnode.getSig());
		}
		IMarker marker2 = createMarkerRace(file2, wnode.getLine(), raceMsg);
		//store bug -> markers
		HashSet<IMarker> newMarkers = new HashSet<>();
		newMarkers.add(marker1);
		newMarkers.add(marker2);
		bug_marker_map.put(raceMsg, newMarkers);
	}

	private IMarker createMarkerRace(IFile file, int line, String msg) throws CoreException {
		Map<String, Object> attributes = new HashMap<String, Object>();
		attributes.put(IMarker.LINE_NUMBER, line);
		attributes.put(IMarker.MESSAGE, msg);
		IMarker newMarker = file.createMarker(BugMarker.TYPE_SCARIEST);
		newMarker.setAttributes(attributes);
		IMarker[] problems = file.findMarkers(BugMarker.TYPE_SCARIEST,true,IResource.DEPTH_INFINITE);
		return newMarker;
	}

	private IMarker createMarkerDL(IFile file, int line, String msg) throws CoreException{
		//for deadlock markers
		Map<String, Object> attributes = new HashMap<String, Object>();
		attributes.put(IMarker.LINE_NUMBER, line);
		attributes.put(IMarker.MESSAGE,msg);
		IMarker newMarker = file.createMarker(BugMarker.TYPE_SCARY);
		newMarker.setAttributes(attributes);
		return newMarker;
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


	private String obtainFixOfDeadlock(TIDEDeadlock bug) {
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

		StringBuffer sb = new StringBuffer("Fix Suggestion Of Deadlock: ");
//		System.err.println("Fix Suggestion Of Deadlock: ");
//		System.out.println(" == exchange the lock order on line " + line11 + " with line " + line12);
		sb.append("exchange the lock order between line " + line11 + " in "
		        + l11.getBelonging().getMethod().getDeclaringClass().getName().toString()
				+ " and line " + line12 + " in "
		        + l12.getBelonging().getMethod().getDeclaringClass().getName().toString());
		return new String(sb);
	}


	private ArrayList<LinkedList<String>> obtainTraceOfRace(TIDERace race) {
		MemNode rw1 = race.node1;
		MemNode rw2 = race.node2;
		int tid1 = race.tid1;
		int tid2 = race.tid2;
		ArrayList<LinkedList<String>> traces = new ArrayList<>();
		LinkedList<String> trace1 = obtainTraceOfINode(tid1, rw1, race, 1);
		LinkedList<String> trace2 = obtainTraceOfINode(tid2, rw2, race, 2);
		traces.add(trace1);
		traces.add(trace2);
		return traces;
	}

	private ArrayList<LinkedList<String>> obtainTraceOfDeadlock(TIDEDeadlock bug) {
		DLockNode l11 = bug.lp1.lock1;//1
		DLockNode l12 = bug.lp1.lock2;
		DLockNode l21 = bug.lp2.lock1;//1
		DLockNode l22 = bug.lp2.lock2;
		int tid1 = bug.tid1;
		int tid2 = bug.tid2;
		ArrayList<LinkedList<String>> traces = new ArrayList<>();
		LinkedList<String> trace1 = obtainTraceOfINode(tid1, l11, bug, 1);
		trace1.add("   >> nested with ");
//		trace1.add(l12.toString());
		//writeDownMyInfo(trace1, l12, bug);
		String sub12 = l12.toString();
		IFile file12 = l12.getFile();
		int line12 = l12.getLine();
		trace1.addLast(sub12);
		bug.addEventIFileToMap(sub12, file12);
		bug.addEventLineToMap(sub12, line12);

		LinkedList<String> trace2 = obtainTraceOfINode(tid2, l21, bug, 2);
		trace2.add("   >> nested with ");
//		trace2.add(l22.toString());
		//writeDownMyInfo(trace2, l22, bug);
		String sub22 = l22.toString();
		IFile file22 = l22.getFile();
		int line22 = l22.getLine();
		trace2.addLast(sub22);
		bug.addEventIFileToMap(sub22, file22);
		bug.addEventLineToMap(sub22, line22);

		traces.add(trace1);
		traces.add(trace2);
		return traces;
	}

	private void printSubTrace(LinkedList<String> trace) {
		for (String sub : trace) {
			System.out.println(sub);
		}
	}

	private LinkedList<String> obtainTraceOfINode(int tid, INode rw1, ITIDEBug bug, int idx) {
		LinkedList<String> trace = new LinkedList<>();
		HashSet<INode> traversed = new HashSet<>();
		SHBGraph shb = ReproduceBenchmarks.engine.shb;
//		if(DEBUG){
//			shb = Test.engine.shb;
//		}else{
//			shb = TIDECGModel.bugEngine.shb;
//		}
		boolean check = writeDownMyInfo(trace, rw1, bug);
		CGNode node = rw1.getBelonging();
		SHBEdge edge = shb.getIncomingEdgeWithTid(node, tid);
		INode parent = null;
		if(edge == null){
			StartNode startNode = TIDECGModel.bugEngine.mapOfStartNode.get(tid);
			if(startNode != null){
				parent = startNode;
				tid = startNode.getParentTID();
//				writeDownMyInfo(trace, startNode, bug);
			}else{
				return trace;
			}
		}else{
			parent = edge.getSource();
		}
		while(parent != null){
			boolean intoJDK = writeDownMyInfo(trace, parent, bug);
			if(check){
				if(!intoJDK){
					replaceRootCauseForRace(trace, parent, bug, idx);
					check = false;
				}
			}
			CGNode node_temp = parent.getBelonging();
			if(node_temp != null){
				//this is a kid thread start node
				if(!node.equals(node_temp)){
					node = node_temp;
					edge = shb.getIncomingEdgeWithTid(node, tid);
					if(edge == null){
						//run method: tsp
						if(node_temp.getMethod().getName().toString().contains("run")){
							StartNode startNode = TIDECGModel.bugEngine.mapOfStartNode.get(tid);
							tid = startNode.getParentTID();
							edge = shb.getIncomingEdgeWithTid(node, tid);
							if(edge == null){
								break;
							}
						}else
							break;
					}
					parent = edge.getSource();
					if(parent instanceof StartNode){
						tid = ((StartNode) parent).getParentTID();
					}
				}else{//recursive calls
					HashSet<SHBEdge> edges = shb.getAllIncomingEdgeWithTid(node, tid);
					for (SHBEdge edge0 : edges) {
						if(!edge.equals(edge0)){
							parent = edge0.getSource();
							if(parent instanceof StartNode){
								tid = ((StartNode) parent).getParentTID();
							}
							break;
						}
					}
				}
			}else
				break;
		}
		return trace;
	}

	private void replaceRootCauseForRace(LinkedList<String> trace, INode root, ITIDEBug bug, int idx) {
		//because trace comes into jdk libraries, which cannot create markers in jdk jar,
		//replace root cause to variables in user programs
 		if(root instanceof MethodNode){
			MethodNode call = (MethodNode) root;
			SSAAbstractInvokeInstruction inst = call.getInvokeInst();
			CGNode rCgNode = call.getBelonging();
			SSAInstruction[] insts = rCgNode.getIR().getInstructions();
			int param = inst.getUse(0);
			if(param != -1){
				//find root variable
				SSAInstruction rootV = rCgNode.getDU().getDef(param);
				String instSig = null;
				String sig = null;
				int sourceLineNum = -1;
				IFile file = null;
				if(rootV instanceof SSAFieldAccessInstruction){
					IMethod method = rCgNode.getMethod();
					try {
						if(rCgNode.getIR().getMethod() instanceof IBytecodeMethod){
							int bytecodeindex = ((IBytecodeMethod) rCgNode.getIR().getMethod()).getBytecodeIndex(inst.iindex);
							sourceLineNum = (int)rCgNode.getIR().getMethod().getLineNumber(bytecodeindex);
						}else{
							SourcePosition position = rCgNode.getMethod().getSourcePosition(inst.iindex);
							sourceLineNum = position.getFirstLine();
							if(position instanceof JdtPosition){
								file = ((JdtPosition) position).getEclipseFile();
							}
						}
					} catch (InvalidClassFileException e) {
						e.printStackTrace();
					}
					String classname = ((SSAFieldAccessInstruction)rootV).getDeclaredField().getDeclaringClass().getName().toString();
					String fieldname = ((SSAFieldAccessInstruction)rootV).getDeclaredField().getName().toString();
					sig = classname.substring(1)+"."+fieldname;
					String typeclassname = method.getDeclaringClass().getName().toString();
					instSig =typeclassname.substring(1)+":"+sourceLineNum;
				}else {
					System.out.println();
				}
				//replace
				TIDERace race = (TIDERace) bug;
				MemNode node = null;
				if(idx == 1){
					node = race.node1;
				}else{
					node = race.node2;
				}
				//race
				race.initsig = sig;
				race.setUpSig(sig);
				//node
				node.setLine(sourceLineNum);;
				node.setSig(instSig);
				node.setPrefix(sig);
				node.setInst(rootV);
				node.setBelonging(rCgNode);
				//trace: replace with a read/write node
				String classname = rCgNode.getMethod().getDeclaringClass().toString();
				String methodname = rCgNode.getMethod().getName().toString();
				String replace = null;
				if(node instanceof ReadNode){
					replace = "Reading at line " + sourceLineNum + " in " + classname.substring(classname.indexOf(':') +3, classname.length()) + "." + methodname;
				}else if(node instanceof WriteNode){
					replace =  "Writing at line " + sourceLineNum + " in " + classname.substring(classname.indexOf(':') +3, classname.length()) + "." + methodname;
				}
				trace.removeLast();
				trace.addFirst(replace);
			}else
				System.out.println();
		}else
			System.out.println();
	}

	private boolean writeDownMyInfo(LinkedList<String> trace, INode node, ITIDEBug bug){
		if(node instanceof MemNode){
			if(node.toString().contains("Ljava/util/"))
				return true;
		}else if(node instanceof MethodNode){
			MethodNode mnode = (MethodNode) node;
			String caller = mnode.getBelonging().getMethod().getDeclaringClass().getName().getPackage().toString();
			String callee = mnode.getTarget().getMethod().getDeclaringClass().getName().getPackage().toString();
			if(callee.equals("java/util") && caller.equals("java/util")){
				return true;
			}
		}
		String sub = null;
		IFile file = null;
		int line = 0;
		if(node instanceof ReadNode){
			sub = ((ReadNode)node).toString();
			file = ((ReadNode)node).getFile();
			line = ((ReadNode)node).getLine();
		}else if(node instanceof WriteNode){
			sub = ((WriteNode)node).toString();
			file = ((WriteNode)node).getFile();
			line = ((WriteNode)node).getLine();
		}else if(node instanceof SyncNode){
			sub = ((SyncNode)node).toString();
			file = ((SyncNode)node).getFile();
			line = ((SyncNode)node).getLine();
		}else if(node instanceof MethodNode){
			sub = ((MethodNode) node).toString();
			file = ((MethodNode) node).getFile();
			line = ((MethodNode) node).getLine();
		}else if(node instanceof StartNode){
			sub = ((StartNode) node).toString();
			file = ((StartNode) node).getFile();
			line = ((StartNode) node).getLine();
		}else{
			sub = node.toString();
		}
		trace.addFirst(sub);
		bug.addEventIFileToMap(sub, file);
		bug.addEventLineToMap(sub, line);
		return false;
	}

	private IFile getFileFromSig(IPath fullPath, String sig)//":"
	{
		String name = sig.substring(0,sig.indexOf(':'));
		if(name.contains("$"))
			name=name.substring(0, name.indexOf("$"));
		name=name+".java";

//		IPath path = file.getFullPath();//file.getProjectRelativePath();
//			System.out.println("path "+path+"\n name: "+name);

		IPath path = fullPath.append("src/").append(name);

		IFile file = ResourcesPlugin.getWorkspace().getRoot().getFile(path);
		return file;
	}

	private Iterable<Entrypoint> entryPoints;
	@Override
	protected Iterable<Entrypoint> getEntrypoints(AnalysisScope analysisScope, IClassHierarchy classHierarchy) {
		if(entryPoints==null)
		{
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
