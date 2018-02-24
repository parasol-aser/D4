package edu.tamu.aser.tide.plugin.handlers;

import java.awt.MenuBar;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.internal.resources.File;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SynchronizedStatement;
import org.eclipse.jdt.internal.core.SourceType;
import org.eclipse.jdt.ui.ProblemsLabelDecorator.ProblemsLabelChangedEvent;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorRegistry;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;
//import org.eclipse.swt.widgets.DirectoryDialog;
//import org.eclipse.swt.widgets.Shell;





import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.texteditor.AbstractTextEditor;

import com.google.common.base.Stopwatch;
import com.google.common.io.Files;
import com.ibm.wala.analysis.pointers.BasicHeapGraph;
import com.ibm.wala.cast.ipa.callgraph.AstCallGraph.AstCGNode;
import com.ibm.wala.cast.ir.ssa.AstIRFactory.AstIR;
import com.ibm.wala.cast.ir.ssa.SSAConversion;
import com.ibm.wala.cast.java.ipa.callgraph.JavaSourceAnalysisScope;
import com.ibm.wala.cast.java.translator.jdt.JDTSourceLoaderImpl;
import com.ibm.wala.cfg.ControlFlowGraph;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.ClassLoaderImpl;
import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IClassLoader;
import com.ibm.wala.classLoader.Module;
import com.ibm.wala.eclipse.cg.model.WalaProjectCGModel;
import com.ibm.wala.fixedpoint.impl.AbstractFixedPointSolver;
import com.ibm.wala.ide.classloader.EclipseSourceFileModule;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.callgraph.impl.ExplicitCallGraph;
import com.ibm.wala.ipa.callgraph.impl.ExplicitCallGraph.ExplicitNode;
import com.ibm.wala.ipa.callgraph.propagation.HeapModel;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.NormalAllocationInNode;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSACFG.BasicBlock;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAFieldAccessInstruction;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSAMonitorInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.ssa.SSAOptions;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.graph.InferGraphRoots;
import com.ibm.wala.util.intset.IntIterator;
import com.ibm.wala.util.intset.MutableIntSet;
import com.ibm.wala.util.intset.OrdinalSet;
import com.ibm.wala.util.io.FileProvider;
import com.ibm.wala.util.strings.Atom;
import com.ibm.wala.util.strings.StringStuff;

import edu.tamu.aser.tide.engine.AnalysisUtils;
import edu.tamu.aser.tide.engine.BasicAnalysisData;
import edu.tamu.aser.tide.engine.ITIDEBug;
import edu.tamu.aser.tide.engine.KeshmeshCGModel;
import edu.tamu.aser.tide.engine.TIDECGModel;
import edu.tamu.aser.tide.engine.TIDERace;
import edu.tamu.aser.tide.graph.LockSetEngine;
import edu.tamu.aser.tide.graph.ReachabilityEngine;
import edu.tamu.aser.tide.marker.BugMarker;
import edu.tamu.aser.tide.plugin.Activator;
import edu.tamu.aser.tide.plugin.ChangedItem;
import edu.tamu.aser.tide.plugin.MyJavaElementChangeCollector;
import edu.tamu.aser.tide.trace.MemNode;
import edu.tamu.aser.tide.trace.HandlerChanges;
import edu.tamu.aser.tide.trace.INode;
import edu.tamu.aser.tide.trace.SyncNode;
import edu.tamu.aser.tide.trace.JoinNode;
import edu.tamu.aser.tide.trace.LockPair;
import edu.tamu.aser.tide.trace.ReadNode;
import edu.tamu.aser.tide.trace.ReporterChanges;
import edu.tamu.aser.tide.trace.StartNode;
import edu.tamu.aser.tide.trace.WriteNode;
import edu.tamu.aser.tide.views.EchoDLView;
import edu.tamu.aser.tide.views.EchoRaceView;
import edu.tamu.aser.tide.views.EchoReadWriteView;
import edu.tamu.aser.tide.views.ExcludeView;
import scala.collection.immutable.Nil;

public class ConvertHandler extends AbstractHandler {

	public static final String DESC_MAIN = "([Ljava/lang/String;)V";
//	public ExcludeView excludeView;
	public EchoRaceView echoRaceView;
	public EchoReadWriteView echoRWView;
	public EchoDLView echoDLView;
//	private static String EXCLUSIONS =
//			//wala
////			"java\\/awt\\/.*\n" +
////			"javax\\/swing\\/.*\n" +
////			"sun\\/awt\\/.*\n" +
////			"sun\\/swing\\/.*\n" +
////			"com\\/sun\\/.*\n" +
////			"sun\\/.*\n" +
////			"org\\/netbeans\\/.*\n" +
////			"org\\/openide\\/.*\n" +
////			"com\\/ibm\\/crypto\\/.*\n" +
////			"com\\/ibm\\/security\\/.*\n" +
////			"org\\/apache\\/xerces\\/.*\n" +
////			"java\\/security\\/.*\n" + "";
//	//echo
//	"javax\\/.*\n" +
//	"java\\/.*\n" +
//	"sun\\/.*\n" +
//	"sunw\\/.*\n" +
//	"com\\/sun\\/.*\n" +
//	"com\\/ibm\\/.*\n" +
//	"com\\/apple\\/.*\n" +
//	"com\\/oracle\\/.*\n" +
//	"apple\\/.*\n" +
//	"org\\/xml\\/.*\n" +
//	"jdbm\\/.*\n" +
//	"";


	public ConvertHandler() throws PartInitException{
		super();
		Activator.getDefault().setCHandler(this);
	}

	public EchoRaceView getEchoRaceView(){
		return echoRaceView;
	}

	public EchoDLView getEchoDLView(){
		return echoDLView;
	}

	public EchoReadWriteView getEchoReadWriteView(){
		return echoRWView;
	}

	private HashMap<IJavaProject,TIDECGModel> modelMap = new HashMap<IJavaProject,TIDECGModel>();
	private TIDECGModel currentModel;
//	private IFile currentFile;
	private IJavaProject currentProject;

//	public HandlerChanges hChanges = new HandlerChanges();
	public HashSet<CGNode> changedNodes = new HashSet<>();
	public HashSet<CGNode> changedModifiers = new HashSet<>();
	public HashSet<CGNode> ignoreNodes = new HashSet<>();
	public HashSet<CGNode> considerNodes = new HashSet<>();

	long start_time = System.currentTimeMillis();

	public TIDECGModel getCurrentModel(){
		return currentModel;
	}

//	public IFile getCurrentFile(){
//		return currentFile;
//	}

	public IJavaProject getCurrentProject(){
		return currentProject;
	}

	/**
	 * consider ignored functions back
	 * @param javaProject
	 * @param file
	 * @param consider_method
	 */
	public void handleConsiderMethod(IJavaProject javaProject, IFile file, ChangedItem consider_method) {
		considerNodes.clear();
		TIDECGModel model = modelMap.get(javaProject);
		if(model == null)
			return;
		IClassHierarchy cha = model.getClassHierarchy();

		try{
			IClassLoader parent = cha.getLoader(ClassLoaderReference.Application);
			IClassLoader loader_old = cha.getLoader(JavaSourceAnalysisScope.SOURCE);

			ClassLoaderImpl cl = new JDTSourceLoaderImpl(JavaSourceAnalysisScope.SOURCE, parent, cha.getScope().getExclusions(), cha);
			List<Module> modules = new LinkedList<Module>();
			modules.add(EclipseSourceFileModule.createEclipseSourceFileModule(file));
			cl.init(modules);
			Iterator<IClass> iter = cl.iterateAllClasses();
			while(iter.hasNext()){
				IClass javaClass = iter.next();
				String className = javaClass.getName().getClassName().toString();
				Atom apackage = javaClass.getName().getPackage();
				String apackage_s = null;
				if(apackage != null){
					apackage_s = apackage.toString().replace('/', '.');
				}

				if((apackage==null&&consider_method.packageName.isEmpty())
						||apackage!=null&&apackage_s.equals(consider_method.packageName)){

					if(className.contains(consider_method.className)){

						for(com.ibm.wala.classLoader.IMethod m : javaClass.getDeclaredMethods()){
							String mName = m.getName().toString();//JEFF TODO

							if(mName.equals(consider_method.methodName)){
								TypeName typeName = m.getDeclaringClass().getName();
								IClass class_old = loader_old.lookupClass(typeName);
								//									System.out.println("class_old hash: " + System.identityHashCode(class_old)) ;
								if(class_old==null)
									return;//TODO: other kinds of changes

								com.ibm.wala.classLoader.IMethod m_old = class_old.getMethod(m.getSelector());// cha.resolveMethod(m.getReference());
								CGNode node = model.getOldCGNode(m_old);
								considerNodes.add(node);
							}
						}
					}
				}
			}
			if(considerNodes.size() != 0){
				letUsConsider(file, model);
			}
		}catch(Exception e){
			e.printStackTrace();
		}
	}


	/**
	 * ignore function/method
	 * @param ignore_method
	 */
	public void handleIgnoreMethod(IJavaProject javaProject, IFile file, ChangedItem ignore_method) {
		ignoreNodes.clear();
		TIDECGModel model = modelMap.get(javaProject);
		if(model == null)
			return;
		IClassHierarchy cha = model.getClassHierarchy();

		try{
			IClassLoader parent = cha.getLoader(ClassLoaderReference.Application);
			IClassLoader loader_old = cha.getLoader(JavaSourceAnalysisScope.SOURCE);

			ClassLoaderImpl cl = new JDTSourceLoaderImpl(JavaSourceAnalysisScope.SOURCE, parent, cha.getScope().getExclusions(), cha);
			List<Module> modules = new LinkedList<Module>();
			modules.add(EclipseSourceFileModule.createEclipseSourceFileModule(file));
			cl.init(modules);
			Iterator<IClass> iter = cl.iterateAllClasses();
			while(iter.hasNext()){
				IClass javaClass = iter.next();
				String className = javaClass.getName().getClassName().toString();
				Atom apackage = javaClass.getName().getPackage();
				String apackage_s = null;
				if(apackage != null){
					apackage_s = apackage.toString().replace('/', '.');
				}

				if((apackage==null&&ignore_method.packageName.isEmpty())
						||apackage!=null&&apackage_s.equals(ignore_method.packageName)){

					if(className.contains(ignore_method.className)){

						for(com.ibm.wala.classLoader.IMethod m : javaClass.getDeclaredMethods()){
							String mName = m.getName().toString();//JEFF TODO

							if(mName.equals(ignore_method.methodName)){
								TypeName typeName = m.getDeclaringClass().getName();
								IClass class_old = loader_old.lookupClass(typeName);
								//									System.out.println("class_old hash: " + System.identityHashCode(class_old)) ;
								if(class_old==null)
									return;//TODO: other kinds of changes

								com.ibm.wala.classLoader.IMethod m_old = class_old.getMethod(m.getSelector());// cha.resolveMethod(m.getReference());
								CGNode node = model.getOldCGNode(m_old);
								ignoreNodes.add(node);
							}
						}
					}
				}
			}
			if(ignoreNodes.size() != 0){
				letUsIgnore(file, model);
			}
		}catch(Exception e){
			e.printStackTrace();
		}
	}


	/**
	 * TriggerCheckHandler
	 * @param javaProject
	 * @param collectedFiles
	 * @param sigChanges
	 */
	public void handleMethodChanges(IJavaProject javaProject, HashMap<String, IFile> sigFiles,
			HashMap<String, ArrayList<ChangedItem>> sigChanges) {
		//see the changes
//		for (ArrayList<ChangedItem> changedItems : sigChanges.values()) {
//			for (ChangedItem changedItem : changedItems) {
//				System.out.println("CHANGED ITEM: " + changedItem.packageName + " " + changedItem.className + " " + changedItem.methodName);
//			}
//		}

		changedNodes.clear();
		changedModifiers.clear();

		TIDECGModel model = modelMap.get(javaProject);
		if(model == null)
			return;

		IFile file0 = null;
		Set<String> sigs = sigChanges.keySet();
		for (String sig : sigs) {
			IFile file = sigFiles.get(sig);
			if(file0 == null)
				file0 = file;
			ArrayList<ChangedItem> changedItems = sigChanges.get(sig);
			discoverChangedCGNodes(javaProject, file, changedItems);
		}

		boolean ptachanges = !AbstractFixedPointSolver.changes.isEmpty();
		if(!changedNodes.isEmpty() || ptachanges || !changedModifiers.isEmpty()){//hChanges.isEmpty()
			//process further check when lock/start changes or pts changes
			letUsRock2(javaProject, file0, model, ptachanges, true);
		}

		try {
			Thread.currentThread().sleep(10);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

	}


	/**
	 * handle program changes
	 * @param javaProject
	 * @param file
	 * @param changedItems
	 */
	public void handleMethodChange(IJavaProject javaProject, IFile file, ArrayList<ChangedItem> changedItems) {
		//see the changes
		for (Iterator iterator = changedItems.iterator(); iterator.hasNext();) {
			ChangedItem changedItem = (ChangedItem) iterator.next();
			System.out.println("CHANGED ITEM: " + changedItem.packageName + " " + changedItem.className + " " + changedItem.methodName);
		}

		changedNodes.clear();
		changedModifiers.clear();

		TIDECGModel model = modelMap.get(javaProject);
		if(model == null)
			return;

		discoverChangedCGNodes(javaProject, file, changedItems);

		boolean ptachanges = !AbstractFixedPointSolver.changes.isEmpty();
		if(!changedNodes.isEmpty() || ptachanges || !changedModifiers.isEmpty()){//hChanges.isEmpty()
			//process further check when lock/start changes or pts changes
			letUsRock2(javaProject, file, model, ptachanges, false);
		}

		try {
			Thread.currentThread().sleep(10);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private void discoverChangedCGNodes(IJavaProject javaProject, IFile file, ArrayList<ChangedItem> changedItems ){
		TIDECGModel model = modelMap.get(javaProject);
		if(model == null)
			return;

		ArrayList<ChangedItem> furItems = new ArrayList<>();
		IClassHierarchy cha = model.getClassHierarchy();

		try{
			IClassLoader parent = cha.getLoader(ClassLoaderReference.Application);
			IClassLoader loader_old = cha.getLoader(JavaSourceAnalysisScope.SOURCE);

			ClassLoaderImpl cl = new JDTSourceLoaderImpl(JavaSourceAnalysisScope.SOURCE, parent, cha.getScope().getExclusions(), cha);
			List<Module> modules = new LinkedList<Module>();
			modules.add(EclipseSourceFileModule.createEclipseSourceFileModule(file));
			cl.init(modules);
			boolean onlyModifier = false;
			Iterator<IClass> iter = cl.iterateAllClasses();
			while(iter.hasNext()){
				IClass javaClass = iter.next();
				String className = javaClass.getName().getClassName().toString();
				Atom apackage = javaClass.getName().getPackage();
				String apackage_s = null;
				if(apackage != null){
					apackage_s = apackage.toString().replace('/', '.');
				}
				//list all involved packages/class/method
				for(com.ibm.wala.classLoader.IMethod m : javaClass.getDeclaredMethods()){
					String mName = m.getName().toString();
					if(apackage_s!= null)
						System.out.println(apackage_s + " " + className + " " + mName);
				}


				for (Iterator<ChangedItem> iterator = changedItems.iterator(); iterator.hasNext();) {
					ChangedItem changedItem = (ChangedItem) iterator.next();

					if((apackage==null&&changedItem.packageName.isEmpty())
							||apackage!=null&&apackage_s.equals(changedItem.packageName)){

						if(className.contains(changedItem.className)){

							for(com.ibm.wala.classLoader.IMethod m : javaClass.getDeclaredMethods()){
								String mName = m.getName().toString();//JEFF TODO

								if(mName.equals(changedItem.methodName)){
									TypeName typeName = m.getDeclaringClass().getName();
									IClass class_old = loader_old.lookupClass(typeName);

									if(class_old==null)
										return;//TODO: other kinds of changes

									com.ibm.wala.classLoader.IMethod m_old = class_old.getMethod(m.getSelector());// cha.resolveMethod(m.getReference());
									//save new method
									class_old.updateMethod(m.getSelector(), m);

									IR ir_old = model.getCache().getSSACache().findOrCreateIR(m_old, Everywhere.EVERYWHERE, model.getOptions().getSSAOptions());
									IR ir = model.getCache().getSSACache().findOrCreateIR(m, Everywhere.EVERYWHERE, model.getOptions().getSSAOptions());

									if(compareIRs(ir_old, ir, furItems)){//only compare the inner ir
										if(m.isSynchronized() == m_old.isSynchronized()){
											//changes are not in this ir, should be in hidden new body,
											//e.g. see sunflow/LigherServer/calculatePhotonse:  new thread/runnable.
											continue;
										}else{
											//only modifer changes
											onlyModifier = true;
										}
									}
									CGNode node = model.getOldCGNode(m_old);
									model.updatePointerAnalysis(node,ir_old,ir);
									node = model.updateCallGraph(m_old,m,ir);
									if(onlyModifier){
										changedModifiers.add(node);
									}else{
										changedNodes.add(node);
									}
									onlyModifier = false;
									System.out.println();
									System.err.println("Changed Item: " + changedItem.packageName + " " + changedItem.className + " " + changedItem.methodName);
								}
							}
						}
					}
				}
			}
		}catch(Exception e){
			e.printStackTrace();
		}

		if(!furItems.isEmpty()){
			discoverChangedCGNodes(javaProject, file, furItems);
		}
	}

	private boolean compareIRs(IR ir_old, IR ir, ArrayList<ChangedItem> furItems) {
		SSAInstruction[] insts_old = ir_old.getInstructions();
		SSAInstruction[] insts = ir.getInstructions();
		if(insts.length == insts_old.length){
			int size = insts.length;
			for(int i=0; i<size; i++){
				SSAInstruction old = insts_old[i];
				SSAInstruction newI = insts[i];
				if(old != null && newI != null){
					if(old.toString().equals(newI.toString())){
						//changes are not in this ir, should be in hidden new body,
						if(newI instanceof SSAAbstractInvokeInstruction){
							SSAAbstractInvokeInstruction invoke = (SSAAbstractInvokeInstruction) newI;
							String cname = invoke.getCallSite().getDeclaredTarget().getDeclaringClass().getName().getClassName().toString();
							if(cname.contains("anonymous subclass of java.lang.Object")){
								ChangedItem item = new ChangedItem();
								item.packageName = invoke.getCallSite().getDeclaredTarget().getDeclaringClass().getName().getPackage().toString().replace('/', '.');
								item.methodName = "run";
								item.className = cname;
								if(!furItems.contains(item))
									furItems.add(item);
							}
						}
						continue;
					}else{
						return false;
					}
				}else if(old == null && newI == null){
					continue;
				}else {
					return false;
				}
			}
			return true;
		}else
			return false;
	}



	private int num_of_detection = 0;

	private void letUsRock(IJavaProject javaProject, final IFile file, final TIDECGModel model){
		num_of_detection = 1;
		//TODO: fork a new Thread to do this
		new Thread(new Runnable(){
			@Override
			public void run() {
				HashSet<ITIDEBug> bugs = new HashSet<ITIDEBug>();
				//Detect Bugs
				if(num_of_detection == 1){
					//initial
					System.err.println("INITIAL DETECTION >>>");
					bugs =  model.detectBug();
				}else{
					System.out.println("wrong call");
				}
				//update UI
				model.updateGUI(javaProject, file, bugs, true);
				System.err.println("Total Time: "+(System.currentTimeMillis()-start_time));
			}
		}).start();
	}

	private void letUsRock2(IJavaProject javaProject, final IFile file, final TIDECGModel model, boolean ptachanges, boolean trigger){
		num_of_detection++;
		//TODO: fork a new Thread to do this
		new Thread(new Runnable(){
			@Override
			public void run() {
				start_time = System.currentTimeMillis();
				HashSet<ITIDEBug> bugs = new HashSet<ITIDEBug>();
				//Detect Bugs
				if(num_of_detection == 1){
					System.out.println("wrong call inc");
				}else{
					//incremental
					System.err.println("DETECTION AGAIN >>>");
					bugs = model.detectBugAgain(changedNodes, changedModifiers, ptachanges);
				}
				//clear pta changes
		        model.clearChanges();
				//update UI
				model.updateGUI(javaProject, file, bugs, false);
				if(trigger){
					Activator.getDefault().getDefaultCollector().resetCollectedChanges();
				}
				System.err.println("Incremental Time: "+(System.currentTimeMillis()-start_time));
				System.out.println();
			}
		}).start();
	}

	private void letUsIgnore(IFile file, TIDECGModel model) {
		if(num_of_detection <= 0){
			return;
		}
		new Thread(new Runnable(){
			@Override
			public void run() {
				HashSet<ITIDEBug> removedbugs = model.ignoreCGNodes(ignoreNodes);
				//update UI
				//remove the marker from editor
				model.removeBugMarkersForIgnore(removedbugs);
				//remove the bug from echoview
				model.updateEchoViewForIgnore();
				System.err.println("Total Time: "+(System.currentTimeMillis()-start_time));
			}
		}).start();
	}



	private void letUsConsider(IFile file, TIDECGModel model) {
		if(num_of_detection <= 0){
			return;
		}
		new Thread(new Runnable(){
			@Override
			public void run() {
				HashSet<ITIDEBug> addbugs = model.considerCGNodes(considerNodes);
				//update UI
				//remove the marker from editor
				try {
					model.addBugMarkersForConsider(addbugs, file);
				} catch (CoreException e) {
					e.printStackTrace();
				}
				//remove the bug from echoview
				model.updateEchoViewForConsider();
				System.err.println("Total Time: "+(System.currentTimeMillis()-start_time));
			}
		}).start();
	}


	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
//		Shell shell = HandlerUtil.getActiveShell(event);
//		Collection menus = HandlerUtil.getActiveMenus(event);
		ISelection sel = HandlerUtil.getActiveMenuSelection(event);
		IStructuredSelection selection = (IStructuredSelection) sel;
		Object firstElement = selection.getFirstElement();
		if (firstElement instanceof ICompilationUnit) {

			ICompilationUnit cu = (ICompilationUnit) firstElement;
			if(hasMain(cu)){
				test(cu, selection);//initial
			}else {
				//      MessageDialog.openInformation(shell, "Info",
				//          "Please select a Java source file");
			}
		}

		return null;
	}


	public void rewriteExcludeFile() {
		//leave for test(ICompilationUnit cu, IStructuredSelection selection)
	}

	public void test(ICompilationUnit cu, IStructuredSelection selection){
		try{
			IJavaProject javaProject = cu.getJavaProject();
			String mainSig = getSignature(cu);
			//excluded in text file
//			TIDECGModel model = new TIDECGModel(javaProject, "EclipseDefaultExclusions.txt", mainSig);
			//excluded in String
//			String deafaultDefined = excludeView.getDefaultText();
//			String userDefined = excludeView.getChangedText();
//			String new_exclusions = null;
//			if(userDefined.length() > 0){
//	            //append new added in excludeview
//				StringBuilder stringBuilder = new StringBuilder();
//				stringBuilder.append(deafaultDefined);
//				stringBuilder.append(userDefined);
//				new_exclusions = stringBuilder.toString();//combined
//				//write back to EclipseDefaultExclusions.txt
//				java.io.File file = new java.io.File("/Users/Bozhen/Documents/Eclipse2/Test_both_copy/edu.tamu.cse.aser.echo/data/EclipseDefaultExclusions.txt");
//				FileWriter fileWriter = new FileWriter(file, false);
//				BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
//				bufferedWriter.write(new_exclusions);
//				bufferedWriter.close();
//			}
			TIDECGModel model = new TIDECGModel(javaProject, "EclipseDefaultExclusions.txt", mainSig);
//			System.out.println("model is " + System.identityHashCode(model));
//			long start_time = System.currentTimeMillis();
			model.buildGraph();
			System.err.println("Call Graph Construction Time: "+(System.currentTimeMillis()-start_time));
			modelMap.put(javaProject, model);
//			excludeView.setProgramInfo(cu, selection);
			IFile file = ResourcesPlugin.getWorkspace().getRoot().getFile(cu.getPath());
			//set echoview to menuhandler
			echoRaceView = model.getEchoRaceView();
			echoRWView = model.getEchoRWView();
			echoDLView = model.getEchoDLView();
			//set current model
			currentModel = model;
//			currentFile = file;
			currentProject = javaProject;
			//concurrent
			letUsRock(javaProject, file, model);
			Activator.getDefaultReporter().initialSubtree(cu, selection, javaProject);
			//for trigger button
			MyJavaElementChangeCollector collector = Activator.getDefault().getDefaultCollector();
			collector.resetCollectedChanges();

		}catch(Exception e){
			e.printStackTrace();
		}
	}


	private boolean hasMain(ICompilationUnit cu){
		try{

			for(IJavaElement e: cu.getChildren())
			{
				if(e instanceof SourceType)
				{
					SourceType st = (SourceType)e;
					for (IMethod m: st.getMethods())
						if((m.getFlags()&Flags.AccStatic)>0
								&&(m.getFlags()&Flags.AccPublic)>0
								&&m.getElementName().equals("main")
								&&m.getSignature().equals("([QString;)V"))
						{
							return true;
						}
				}
			}

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;
	}
	private String getSignature(ICompilationUnit cu)
	{

		try {
			String name = cu.getElementName();
			int index = name.indexOf(".java");
			name = name.substring(0,index);
			for(IType t :cu.getTypes())
			{
				//			System.out.println(t.getFullyQualifiedName());
				//		}
				//		 String elementName = cu.getElementName();
				//		 String packageNameString;
				//		packageNameString = cu.getPackageDeclarations()[0].getElementName();
				//		 String classname = packageNameString+"."+elementName;
				String tName = t.getElementName();
				if(name.equals(tName))
					return t.getFullyQualifiedName()+".main"+DESC_MAIN;
			}
		} catch (JavaModelException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return "";

	}

//	private void traverseNode(CGNode n, BasicAnalysisData analysis)
//	{
//		//System.out.println("Node name: "+n.getMethod().getName());
//
//		//SSACFG cfg = n.getIR().getControlFlowGraph();
//		//BasicBlock b = cfg.entry();
//
//		//List<SSAInstruction> insts = b.getAllInstructions();
//
//		//cfg.getSuccNodes(b);
//		SSAInstruction[] insts = n.getIR().getInstructions();
//		for(int i=0;i<insts.length;i++)
//		{
//			SSAInstruction inst = insts[i];
//			if(inst!=null)
//			{
//				//System.out.println(inst.toString());
//
//				if(inst instanceof SSAFieldAccessInstruction)
//				{
//					String classname = ((SSAFieldAccessInstruction)inst).getDeclaredField().getDeclaringClass().getName().toString();
//					String fieldname = ((SSAFieldAccessInstruction)inst).getDeclaredField().getName().toString();
//					String sig = classname.substring(1)+"."+fieldname;
//
//					int sourceLineNum = 0;
//					if(n.getMethod() instanceof IBytecodeMethod)
//					{
//						try{
//							IBytecodeMethod method = (IBytecodeMethod)n.getMethod();
//							int bytecodeIndex = method.getBytecodeIndex(inst.iindex);
//							sourceLineNum = method.getLineNumber(bytecodeIndex);
//
//						}catch(Exception e)
//						{
//							e.printStackTrace();
//						}
//					}
//					String typeclassname =  n.getMethod().getDeclaringClass().getName().toString();
//					String instSig =typeclassname.substring(1)+":"+sourceLineNum;
//
//
//					if(inst instanceof SSAGetInstruction)//read
//					{
//						HashMap<Integer, String> threadRInst = analysis.variableReadMap.get(sig);
//						if(threadRInst==null)
//						{
//							threadRInst = new HashMap<Integer, String>();
//							analysis.variableReadMap.put(sig, threadRInst);
//						}
//						threadRInst.put(analysis.curTID,instSig);
//						//
//						//					HashMap<Integer, String> threadWInst = analysis.variableWriteMap.get(sig);
//						//					if(threadWInst!=null)
//						//					{
//						//						for(int ID: threadWInst.keySet())
//						//							if(ID!=analysis.curTID)
//						//								System.err.println("Race: "+sig+"\n"+threadWInst.get(ID)+"--"+instSig);
//						//					}
//
//						//add node to trace
//						analysis.trace.add(new ReadNode(analysis.getIncrementGID(),analysis.curTID,sig,instSig,sourceLineNum));
//					}
//					else//write
//					{
//						HashMap<Integer, String> threadWInst = analysis.variableWriteMap.get(sig);
//						if(threadWInst==null)
//						{
//							threadWInst = new HashMap<Integer, String>();
//							analysis.variableWriteMap.put(sig, threadWInst);
//
//						}
//						else
//						{
//							for(int ID: threadWInst.keySet())
//								if(ID!=analysis.curTID)
//									System.err.println("Race: "+sig+"\n"+threadWInst.get(ID)+"--"+inst.toString());
//
//						}
//						threadWInst.put(analysis.curTID, instSig);
//						//
//						//					HashMap<Integer, String> threadRInst = analysis.variableReadMap.get(sig);
//						//					if(threadRInst!=null)
//						//					{
//						//						for(int ID: threadRInst.keySet())
//						//							if(ID!=analysis.curTID)
//						//								System.err.println("Race: "+sig+"\n"+threadRInst.get(ID)+"--"+instSig);
//						//					}
//
//						//add node to trace
//						analysis.trace.add(new WriteNode(analysis.getIncrementGID(),analysis.curTID,sig,instSig,sourceLineNum));
//					}
//
//				}
//				else if (inst instanceof SSAInvokeInstruction)
//				{
//					CallSiteReference csr = ((SSAInvokeInstruction)inst).getCallSite();
//					MethodReference mr = csr.getDeclaredTarget();
//					//System.out.println(mr.getSignature());
//
//					//demo.MyThread.start()V
//					//Need to check class type
//
//
//					//if (AnalysisUtils.implementsRunnableInterface(iclass) || AnalysisUtils.extendsThreadClass(iclass))
//					{
//
//						com.ibm.wala.classLoader.IMethod imethod = analysis.classHierarchy.resolveMethod(mr);
//						if(imethod!=null)
//						{
//							String sig = imethod.getSignature();
//							//System.out.println("Invoke Inst: "+sig);
//							if(sig.equals("java.lang.Thread.start()V"))
//							{
//								//new thread is started
//								//is there a call-graph from this node to run node
//
//								//						Set<CGNode> nodes = analysis.callGraph.getPossibleTargets(n, csr);
//								//						for(CGNode n2 : nodes)
//								//						{
//								//							System.out.println(n2);
//								//						}
//
//								//find the node corresponding to the forked thread
//								//System.out.println(mr.getSignature());
//
//								//						IClass iclass = analysis.classHierarchy.lookupClass(mr.getDeclaringClass());
//								//						String classname = iclass.getName().getClassName().toString();
//								//						String classpackage = iclass.getName().getPackage().toString();
//								//						System.out.println("Thread class: "+iclass.getName().toString()+"--"
//								//+classpackage+"."+classname);
//								//						String fullname = classpackage+"."+classname;
//								String name = mr.getDeclaringClass().getName().toString();
//								CGNode node = analysis.threadSigNodeMap.get(name);
//								analysis.threadNodes.add(node);
//
//								//find loops in this method!!
//								//node.getIR().getControlFlowGraph();
//
//								//add node to trace
//								StartNode startNode = new StartNode(analysis.getIncrementGID(),analysis.curTID,node.getGraphNodeId());
//								analysis.trace.add(startNode);
//								analysis.mapOfStartNode.put(analysis.curTID, startNode);
//							}
//							else if(sig.equals("java.lang.Thread.join()V"))
//							{
//								String name = mr.getDeclaringClass().getName().toString();
//								CGNode node = analysis.threadSigNodeMap.get(name);
//
//								//add node to trace
//								analysis.trace.add(new JoinNode(analysis.getIncrementGID(),analysis.curTID,node.getGraphNodeId()));
//
//							}
//						}
//					}
//				}
//				else if(inst instanceof SSAMonitorInstruction)
//				{
//					//lock node: GID, TID, LockID
//					SSAMonitorInstruction monitorInstruction = ((SSAMonitorInstruction) inst);
//					int lockValueNumber = monitorInstruction.getRef();
//					PointerKey lockPointer = analysis.heapModel.getPointerKeyForLocal(n, lockValueNumber);
//					OrdinalSet<InstanceKey> lockObjects = analysis.pointerAnalysis.getPointsToSet(lockPointer);
//					for (InstanceKey instanceKey : lockObjects) {
//						if (instanceKey instanceof NormalAllocationInNode) {
//							NormalAllocationInNode normalAllocationInNode = (NormalAllocationInNode) instanceKey;
//
//							String lock = normalAllocationInNode.getConcreteType().getName().getClassName()+"."+normalAllocationInNode.hashCode();
//
//							//						if (isReturnedByGetClass(normalAllocationInNode)) {
//							//							//					addSynchronizedClassTypeNames(result, normalAllocationInNode);
//							//							result.add(getReceiverTypeName(normalAllocationInNode));
//							//						}
//							//add node to trace
//							if(((SSAMonitorInstruction) inst).isMonitorEnter())
//								analysis.trace.add(new LockNode(analysis.getIncrementGID(),analysis.curTID,lock));
//							else
//								analysis.trace.add(new UnlockNode(analysis.getIncrementGID(),analysis.curTID,lock));
//
//						}
//					}
//
//				}
//				else
//				{
//					//System.out.println("Other Inst: "+inst);
//				}
//			}
//
//		}
//
//		analysis.alreadyProcessedNodes.add(n);
//
//		Iterator<CGNode> it =analysis.callGraph.getSuccNodes(n);
//		while(it.hasNext())
//		{
//			n = it.next();
//			if(!analysis.alreadyProcessedNodes.contains(n))
//				if(AnalysisUtils.isApplicationClass(n.getMethod().getDeclaringClass()))
//					traverseNode(n,analysis);
//		}
//
//	}
	private void testCallGraph(ICompilationUnit cu)
	{
		try{
			IJavaProject javaProject =cu.getJavaProject();
			String mainSig = getSignature(cu);

			TestCallGraph tcg = new TestCallGraph(javaProject,"EclipseDefaultExclusions.txt",0,mainSig);
			tcg.buildCallGraph();
		}catch(Exception e)
		{
			e.printStackTrace();

		}


	}
	private BasicAnalysisData computeAnalysis(IJavaProject javaProject, String mainSig) throws Exception
	{
		//EclipseFileProvider provider = new EclipseFileProvider();
		//String exclusionsFileName = provider.getFileFromPlugin(Activator.getDefault(), "EclipseDefaultExclusions.txt").getAbsolutePath();
		KeshmeshCGModel model = new KeshmeshCGModel(javaProject, "EclipseDefaultExclusions.txt",0,mainSig);
		long start_time = System.currentTimeMillis();
		model.buildGraph();
		long stop_time = System.currentTimeMillis();
		System.out.println("CALL_GRAPH_CONSTRUCTION_TIME_IN_MILLISECONDS: "+ String.valueOf(stop_time-start_time));

		CallGraph callGraph = model.getGraph();
		PointerAnalysis pointerAnalysis = model.getPointerAnalysis();
		HeapModel heapModel = pointerAnalysis.getHeapModel();
		BasicHeapGraph heapGraph = new BasicHeapGraph(pointerAnalysis, callGraph);

		LinkedList<CGNode> threadNodes = new LinkedList<CGNode>();

		HashMap<String,CGNode> threadSigNodeMap = new HashMap<String,CGNode>();

		Collection<CGNode> cgnodes = callGraph.getEntrypointNodes();
		for(CGNode n: cgnodes)
		{

			String sig = n.getMethod().getSignature();
			//find the main node
			if(sig.equals(mainSig))
			{
				threadNodes.add(n);//break;
			}
			else
			{
				String name  = n.getMethod().getDeclaringClass().getName().toString();
				threadSigNodeMap.put(name, n);//mr.getSignature()
			}
		}

		BasicAnalysisData analysis = new BasicAnalysisData(model, model.getClassHierarchy(), callGraph, pointerAnalysis, heapModel, heapGraph,threadNodes,threadSigNodeMap);
		return analysis;
	}
//	private void detectRace(ICompilationUnit cu)
//	{
//		long start_time = System.currentTimeMillis();
//		try {
//			IJavaProject javaProject = cu.getJavaProject();
//			BasicAnalysisData analysis = analysisMap.get(javaProject);
//			//if(analysis==null)
//			{
//				String mainSig = getSignature(cu);
//
//				analysis = computeAnalysis(javaProject,mainSig);
//				analysisMap.put(javaProject,analysis);
//			}
//
//
//			runRaceAlgorithm(cu, analysis);
//		}
//		catch (Exception e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//		long stop_time = System.currentTimeMillis();
//
//		System.out.println("RACE_DETECTION_TIME_IN_MILLISECONDS: "+ String.valueOf(stop_time-start_time));
//
//	}

//	private void runRaceAlgorithm(ICompilationUnit cu, BasicAnalysisData analysis)
//			throws CoreException {
//		while(!analysis.threadNodes.isEmpty())
//		{
//			CGNode n = analysis.threadNodes.removeFirst();
//			analysis.curTID = n.getGraphNodeId();
//			analysis.alreadyProcessedNodes.clear();
//			traverseNode(n,analysis);
//		}
//
//		//analyze trace
//
//		//1. find shared variables
//		HashSet<String> sharedFields = new HashSet<String>();
//		for(String sig: analysis.variableWriteMap.keySet())
//		{
//			Set<Integer> writeTids = analysis.variableWriteMap.get(sig).keySet();
//			if(writeTids.size()>1)
//			{
//				sharedFields.add(sig);
//			}
//			else
//			{
//				Set<Integer> readTids = analysis.variableReadMap.get(sig).keySet();
//				if(readTids!=null)
//				{
//					Set<Integer> set = new HashSet<Integer>(readTids);
//					set.addAll(writeTids);
//					if(set.size()>1)
//					{
//						sharedFields.add(sig);
//					}
//				}
//			}
//		}
//
//		//2. remove local nodes
//
//		HashMap<String, LinkedList<ReadNode>> sigReadNodes = new HashMap<String, LinkedList<ReadNode>>();
//		HashMap<String, LinkedList<WriteNode>> sigWriteNodes = new HashMap<String, LinkedList<WriteNode>>();
//		HashMap<Integer, LinkedList<SyncNode>> threadSyncNodes = new HashMap<Integer, LinkedList<SyncNode>>();
//
//		ReachabilityEngine reachEngine = new ReachabilityEngine();
//		LockSetEngine lockEngine = new LockSetEngine();
//		HashMap<String,LockNode> lockcurrentNode = new HashMap<String,LockNode>();
//
//		for(int i=0;i<analysis.trace.size();i++)
//		{
//			INode node = analysis.trace.get(i);
//			if(node instanceof SyncNode)
//			{
//				LinkedList<SyncNode> syncNodes = threadSyncNodes.get(node.getTID());
//				if(syncNodes==null)
//				{
//					syncNodes = new LinkedList<SyncNode>();
//					threadSyncNodes.put(node.getTID(),syncNodes);
//				}
//				syncNodes.add((SyncNode)node);
//
//				if(node instanceof StartNode)
//				{
//					MutableIntSet tid_child = ((StartNode)node).getTID_Child();
//					IntIterator child_iter = tid_child.intIterator();
//					while(child_iter.hasNext()){
//						int child = child_iter.next();
//						reachEngine.addEdge(node.getGID()+"", child+"s");
//						//add the child thread's
//						reachEngine.addEdge(child+"s",child+"e");
//					}
//				}
//				else if (node instanceof JoinNode)
//					reachEngine.addEdge(((JoinNode)node).getTID_End()+"e",node.getGID()+"");
//				else if (node instanceof LockNode)
//				{
//					lockcurrentNode.put(((LockNode)node).getLockString(), (LockNode)node);
//
//				}
//				else if(node instanceof UnlockNode)
//				{
//					LockNode lockNode = lockcurrentNode.get(((UnlockNode)node).getLockString());
//					if(lockNode!=null)
//						lockEngine.add(((UnlockNode)node).getLockString(), node.getTID(), new LockPair(lockNode,(UnlockNode)node));
//
//				}
//
//			}
//			else if(node instanceof MemNode)
//			{
//				String sig = ((MemNode)node).getAddress();
//				if(sharedFields.contains(sig))
//				{
//					if(node instanceof ReadNode)
//					{
//						LinkedList<ReadNode> reads = sigReadNodes.get(sig);
//						if(reads==null)
//						{
//							reads = new LinkedList<ReadNode> ();
//							sigReadNodes.put(sig, reads);
//						}
//						reads.add((ReadNode)node);
//					}
//					else
//					{
//						LinkedList<WriteNode> writes = sigWriteNodes.get(sig);
//						if(writes==null)
//						{
//							writes = new LinkedList<WriteNode> ();
//							sigWriteNodes.put(sig, writes);
//						}
//						writes.add((WriteNode)node);
//					}
//				}
//
//			}
//		}
//
//
//
//		//3. performance race detection with Fork-Join
//
//		for(String sig: sharedFields)
//		{
//			LinkedList<ReadNode> reads = sigReadNodes.get(sig);
//			LinkedList<WriteNode> writes = sigWriteNodes.get(sig);
//
//			for(int j=0;j<writes.size();j++)
//			{
//				WriteNode wnode = writes.get(j);
//
//				for(int i=0;i<reads.size();i++)//write->read
//				{
//					ReadNode rnode = reads.get(i);
//					if(rnode.getTID()!=wnode.getTID()){
//
//						if(!lockEngine.hasCommonLock(rnode.getTID(), rnode.getGID(), wnode.getTID(), wnode.getGID()))
//						{
//							boolean isRace = true;
//
//							//get the nearest fork id and join id
//							LinkedList<SyncNode> list = threadSyncNodes.get(rnode.getTID());
//							if(list!=null)
//								for(int k=0;k<list.size();k++)
//								{
//									SyncNode sn = list.get(k);
//									if(sn instanceof StartNode)
//									{
//										if(sn.getGID()>rnode.getGID())
//										{
//											if(reachEngine.canReach(sn.getGID()+"", wnode.getTID()+"s"))
//											{	isRace = false; break;}
//										}
//									}
//									else
//									{
//										//join
//										if(sn.getGID()<rnode.getGID())
//										{
//											if(reachEngine.canReach(wnode.getTID()+"e",sn.getGID()+""))
//											{	isRace = false; break;}
//										}
//									}
//								}
//							if(isRace)
//							{
//								LinkedList<SyncNode> list2 = threadSyncNodes.get(wnode.getTID());
//								if(list2!=null)
//									for(int k=0;k<list2.size();k++)
//									{
//										SyncNode sn = list2.get(k);
//										if(sn instanceof StartNode)
//										{
//											if(sn.getGID()>wnode.getGID())
//											{
//												if(reachEngine.canReach(sn.getGID()+"", rnode.getTID()+"s"))
//												{	isRace = false; break;}
//											}
//										}
//										else
//										{
//											//join
//											if(sn.getGID()<wnode.getGID())
//											{
//												if(reachEngine.canReach(rnode.getTID()+"e",sn.getGID()+""))
//												{	isRace = false; break;}
//											}
//										}
//									}
//							}
//							if(isRace)
//							{
//								String raceMsg = "Race: "+sig+" ("+rnode.getSig()+", "+wnode.getSig()+")";
//								System.err.println(raceMsg);
//
//								IResource markerTarget = cu.getResource();
//								//IMarker[] existingMarkers = markerTarget.findMarkers(RaceMarker.TYPE_SCARIEST, true, IResource.DEPTH_ZERO);
//
//								Map<String, Object> attributes_r = new HashMap<String, Object>();
//								attributes_r.put(IMarker.LINE_NUMBER, rnode.getLine());
//								attributes_r.put(IMarker.MESSAGE,raceMsg);
//
//								IMarker newMarker_r = markerTarget.createMarker(RaceMarker.TYPE_SCARIEST);
//								newMarker_r.setAttributes(attributes_r);
//
//								Map<String, Object> attributes_w = new HashMap<String, Object>();
//
//								attributes_w.put(IMarker.LINE_NUMBER, wnode.getLine()+1);
//								attributes_w.put(IMarker.MESSAGE,raceMsg);
//
//								IMarker newMarker_w = markerTarget.createMarker(RaceMarker.TYPE_SCARIEST);
//								newMarker_w.setAttributes(attributes_w);
//								/*
//								File file= (File) cu.getResource();
//
//
////		                                PrintableString elem = new PrintableString(
////		                                		file.getName(),//filename
////		                                            "line",//line number
////		                                            file);//I
//
//
//				                IEditorRegistry editorRegistry = PlatformUI.getWorkbench()
//				                        .getEditorRegistry();
//				                //IFile file = elem.getFile();
//				                if(file == null)
//				                    return;
//				                String editorId = editorRegistry.getDefaultEditor(
//				                        file.getFullPath().toString()).getId();
//				                IWorkbenchPage page = PlatformUI.getWorkbench()
//				                        .getActiveWorkbenchWindow().getActivePage();
//				                try {
//				                    AbstractTextEditor ePart = (AbstractTextEditor) page
//				                            .openEditor(new FileEditorInput(file),
//				                                    editorId);
//				                    IDocument document = ePart.getDocumentProvider()
//				                            .getDocument(ePart.getEditorInput());
//				                    if (document != null) {
//				                        IRegion lineInfo_r = null, lineInfo_w = null;
//				                        try {
//				                            lineInfo_r = document.getLineInformation(rnode.getLine() - 1);
//				                            lineInfo_w = document.getLineInformation(wnode.getLine() - 1);
//
//				                        } catch (BadLocationException e) {
//				                            // ignored
//				                        }
//				                        if (lineInfo_r != null&&lineInfo_w != null) {
//				                            ePart.selectAndReveal(lineInfo_r.getOffset(),
//				                                    lineInfo_r.getLength());
//				                            ePart.selectAndReveal(lineInfo_w.getOffset(),
//				                                    lineInfo_w.getLength());
//				                        }
//				                    }
//				                } catch (PartInitException e) {
//				                    e.printStackTrace();
//				                }*/
//							}
//						}
//					}
//
//
//				}
//				for(int k=j+1;k<writes.size();k++){
//					WriteNode wnode2 = writes.get(k);
//					if(wnode.getTID()!=wnode2.getTID())
//					{
//						//check fork-join
//					}
//				}
//			}
//		}
//
//
//		//			final SWTTreeViewer v = new SWTTreeViewer();
//		//			v.setGraphInput(heapGraph);
//		//			v.setRootsInput(InferGraphRoots.inferRoots(heapGraph));
//		//			v.run();
//		//			v.getApplicationWindow();
//
//		//			if(callGraph instanceof ExplicitCallGraph)
//		//			{
//		//				ExplicitCallGraph ecg = (ExplicitCallGraph) callGraph;
//		//
//		//			Iterator<CGNode> it = ecg.iterator();
//		//			while(it.hasNext())
//		//			{
//		//				CGNode n = it.next();
//		//				if( n instanceof ExplicitNode)
//		//				{
//		//					ExplicitNode en = (ExplicitNode)n;
//		//				}
//		//								if(AnalysisUtils.isApplicationClass(n.getMethod().getDeclaringClass()))
//		//				System.out.println(n+": "+n.getIR());
//		//			}
//	}
	public static Object[] getClassNameAndLine(CompilationUnit unit, ASTNode n) {
		IType fileName = unit.getTypeRoot().findPrimaryType();
		String s = (fileName == null) ? "" : fileName.toString();
		// String className = (s.indexOf('[') == -1) ? s : s.substring(0,
		// s.indexOf('['));
		String className = unit.getJavaElement().getPath().toString();
		IFile file = null;
		try {
			file = (IFile) unit.getJavaElement()
					.getCorrespondingResource();
		} catch (JavaModelException e) {
			e.printStackTrace();
		}

		int line = unit.getLineNumber(n.getStartPosition());
		Object[] nameLine = { className, String.valueOf(line), file};
		return nameLine;
	}
	//private void tacle(IMethod mainMethod) {
	//	//create and analysis factory
	//	AnalysisFactory anaFac =  new AnalysisFactory();
	//	//get the RTA implementation of ICallGraphAnalysis
	//	ICallGraphAnalysis cga = anaFac.getAnalysis(AnalysisFactory.RTA);
	//
	//
	//	//necessary if there is a loop in the call graph
	//	Set alreadyAnalyzed = new HashSet();
	//	//run the analysis on the passed in main method
	//	cga.run(mainMethod);
	//	List workList = new ArrayList();
	//
	//	//add main to the work list
	//	workList.add(mainMethod);
	//	alreadyAnalyzed.add(mainMethod);
	//
	//	while(!workList.isEmpty()){
	//		//remove any method from the worklist
	//		IMethod current = (IMethod)workList.remove(0);
	//
	//		System.out.println("Caller: "+current);
	//
	//		//get the set of callsites in the current method
	//		Set callS = cga.getCallSites(current);
	//		//arbitrary number of the callsites
	//		if(callS != null){
	//			//iterate over all the callsites in the method
	//			for(Iterator i = callS.iterator(); i.hasNext();){
	//				ICallSite cs = (ICallSite)i.next();
	//				//Get the set of possible target methods for the current callsite
	//				Set csMethods = cs.getCalledIMethods();
	//				if(csMethods != null&&!csMethods.isEmpty()){
	//					//iterator over the possible target methods
	//					for(Iterator iM = csMethods.iterator(); iM.hasNext();){
	//						IMethod next = (IMethod)iM.next();
	//						//Only add user methods to the worklist that have not
	//						//already been analyzed
	//						if(cga.analyzedFromSourceCode(next) && !alreadyAnalyzed.contains(next)){
	//							workList.add(next);
	//							alreadyAnalyzed.add(next);
	//
	//						}
	//						//only add an edge if the target method is user defined
	//						if(cga.analyzedFromSourceCode(next)){
	//							//writeDotFile(out,current,next,mCount+"."+csCount);
	//						}
	//
	//						System.out.println(" ==> "+"Callee: "+next);
	//
	//					}
	//				}
	//			}
	//		}
	//	}
	//}

	private void testRVPredict(ICompilationUnit cu) {
		try
		{

			if(cu.getChildren().length>0)
			{
				String mainclass =  cu.getChildren()[0].getElementName();	//((IType) JavaCore.create(cu.getResource())).getFullyQualifiedName() ;
				for(int i=1;i<cu.getChildren().length;i++)
				{
					mainclass += "."+cu.getChildren()[i].getElementName();
				}


				java.io.File ws = ResourcesPlugin.getWorkspace().getRoot().getLocation().toFile();
				//System.out.println(cu.getPath().toOSString());
				IJavaProject javaProject = cu.getJavaProject();
				//get classpath
				String classpath = ws.toString()+javaProject.getOutputLocation().makeAbsolute().toOSString();


				final IClasspathEntry[] resolvedClasspath = javaProject.getReferencedClasspathEntries();
				for (IClasspathEntry classpathEntry : resolvedClasspath) {
					classpath+=System.getProperty("file.separator")+(classpathEntry.getPath().makeAbsolute().toOSString());
				}

				//    		ILaunchManager mgr = DebugPlugin.getDefault().getLaunchManager();
				//    		ILaunchConfigurationType lct = mgr.getLaunchConfigurationType(IOpcodeConstants.LAUNCH_CFG_TYPE);
				//
				//			ILaunchConfiguration cfg = lct.newInstance(null, "test");
				//			ILaunchConfigurationWorkingCopy wc = cfg.getWorkingCopy();
				//			wc.setAttribute(IExternalToolConstants.ATTR_LOCATION,command);
				//			wc.setAttribute(IExternalToolConstants.ATTR_WORKING_DIRECTORY, "${workspace_loc:" + javaProject.getPath().toOSString() +"}");
				//			wc.setAttribute(IExternalToolConstants.ATTR_TOOL_ARGUMENTS, params);
				//			cfg = wc.doSave();
				//			cfg.launch(ILaunchManager.RUN_MODE, null, false, true);
				//			cfg.delete();


				//System.out.println(mainclass);

				//System.out.println(classpath);

				String cmd = "rv-predict -cp "+classpath+ " "+mainclass;
				///home/smhuang/RV/rv-predict/bin/rv-predict
				//configure working directory

				Process pr = Runtime.getRuntime().exec(cmd);

				BufferedReader in = new BufferedReader(new
						InputStreamReader(pr.getErrorStream()));
				String line;
				while ((line = in.readLine()) != null) {
					System.out.println(line);
				}
				in.close();
				pr.waitFor();


			}
		}catch(Exception e)
		{
			e.printStackTrace();
		}
	}



	//  private void createOutput(Shell shell, Object firstElement) {
	//    String directory;
	//    ICompilationUnit cu = (ICompilationUnit) firstElement;
	//    IResource res = cu.getResource();
	//    boolean newDirectory = true;
	//    directory = getPersistentProperty(res, path);
	//
	//    if (directory != null && directory.length() > 0) {
	//      newDirectory = !(MessageDialog.openQuestion(shell, "Question",
	//          "Use the previous output directory?"));
	//    }
	//    if (newDirectory) {
	//      DirectoryDialog fileDialog = new DirectoryDialog(shell);
	//      directory = fileDialog.open();
	//
	//    }
	//    if (directory != null && directory.length() > 0) {
	//      setPersistentProperty(res, path, directory);
	//      write(directory, cu);
	//    }
	//  }

	protected String getPersistentProperty(IResource res, QualifiedName qn) {
		try {
			return res.getPersistentProperty(qn);
		} catch (CoreException e) {
			return "";
		}
	}

	protected void setPersistentProperty(IResource res, QualifiedName qn,
			String value) {
		try {
			res.setPersistentProperty(qn, value);
		} catch (CoreException e) {
			e.printStackTrace();
		}
	}

	private void write(String dir, ICompilationUnit cu) {
		try {
			cu.getCorrespondingResource().getName();
			String test = cu.getCorrespondingResource().getName();
			// Need
			String[] name = test.split("\\.");
			String htmlFile = dir + "\\" + name[0] + ".html";
			FileWriter output = new FileWriter(htmlFile);
			BufferedWriter writer = new BufferedWriter(output);
			writer.write("<html>");
			writer.write("<head>");
			writer.write("</head>");
			writer.write("<body>");
			writer.write("<pre>");
			writer.write(cu.getSource());
			writer.write("</pre>");
			writer.write("</body>");
			writer.write("</html>");
			writer.flush();
		} catch (JavaModelException e) {
		} catch (IOException e) {
			e.printStackTrace();
		}

	}



}
