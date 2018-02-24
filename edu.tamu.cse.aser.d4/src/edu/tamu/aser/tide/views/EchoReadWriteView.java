package edu.tamu.aser.tide.views;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.jface.viewers.TreeViewerRow;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Sash;
import org.eclipse.swt.widgets.Tracker;
import org.eclipse.ui.IEditorRegistry;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.omg.CORBA.Bounds;

import edu.tamu.aser.tide.engine.ITIDEBug;
import edu.tamu.aser.tide.engine.TIDEDeadlock;
import edu.tamu.aser.tide.engine.TIDEEngine;
import edu.tamu.aser.tide.engine.TIDERace;
import edu.tamu.aser.tide.trace.MemNode;
import edu.tamu.aser.tide.trace.ReadNode;
import edu.tamu.aser.tide.trace.WriteNode;

public class EchoReadWriteView extends ViewPart{

	protected TreeViewer rootViewer;
	protected TreeViewer concurentRelationViewer;
	protected BugLabelProvider labelProvider;
	protected BugDetail bugDetail;
//	protected RaceDetail raceDetail;
//	protected DeadlockDetail deadlockDetail;
	protected RelationDetail relationDetail;

	protected Action jumpToLineInEditor;
	protected Action jumpToDetailLine;
	protected Action showDetailTree;

	protected TIDEEngine bugEngine;
	protected HashSet<TIDERace> existingbugs = new HashSet<>();
	protected HashSet<String> existingSigs = new HashSet<>();



	public EchoReadWriteView() {
		super();
	}

	public void setEngine(TIDEEngine bugEngine) {
		this.bugEngine = bugEngine;
	}

	@Override
	public void createPartControl(Composite parent) {
		/* Create a grid layout object so the text and treeviewer
		 * are layed out the way I want. */
		GridLayout layout = new GridLayout();
//		layout.numColumns = 1;
		layout.numColumns = 3;
		layout.verticalSpacing = 2;
		layout.marginWidth = 0;
		layout.marginHeight = 2;
		parent.setLayout(layout);

		/* Create a "label" to display information in. I'm
		 * using a text field instead of a lable so you can
		 * copy-paste out of it. */
//		text = new Text(parent, SWT.READ_ONLY | SWT.SINGLE | SWT.BORDER);
		// layout the text field above the treeviewer
		GridData layoutData = new GridData();
		// layout the tree viewer below the text field
		layoutData = new GridData();
		layoutData.grabExcessHorizontalSpace = true;
		layoutData.grabExcessVerticalSpace = true;
		layoutData.horizontalAlignment = GridData.FILL;
		layoutData.verticalAlignment = GridData.FILL;
//		layoutData.horizontalAlignment = GridData.FILL;
//		text.setLayoutData(layoutData);

		// Create the tree viewer as a child of the composite parent
		rootViewer = new TreeViewer(parent);
		rootViewer.setContentProvider(new BugContentProvider());
		labelProvider = new BugLabelProvider();
		rootViewer.setLabelProvider(labelProvider);
		rootViewer.setUseHashlookup(true);
		rootViewer.getControl().setLayoutData(layoutData);

//		Yanze
//		add a sash widget to adjust treeview width
		Sash sash = new Sash(parent, SWT.VERTICAL);
		GridData sashlayout = new GridData();
		sashlayout.verticalAlignment = GridData.FILL;
		sashlayout.widthHint = 2;
		sash.setLayoutData(sashlayout);
		sash.setBackground(new Color(Display.getCurrent(), 220,220,220));
		sash.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				int sashOriginalX = sash.getBounds().x;
				Rectangle treebound = rootViewer.getControl().getBounds();
				Rectangle relationbound = concurentRelationViewer.getControl().getBounds();
				int delta = event.x - sashOriginalX;
				treebound.width += delta;
				relationbound.x += delta;
				relationbound.width -= delta;
				rootViewer.getControl().setBounds(treebound);
				concurentRelationViewer.getControl().setBounds(relationbound);
			}
		});

//		Yanze
//		add a new treeview
		concurentRelationViewer = new TreeViewer(parent);
		concurentRelationViewer.getControl().setLayoutData(layoutData);
		concurentRelationViewer.setContentProvider(new BugContentProvider());
		concurentRelationViewer.setLabelProvider(labelProvider);
		concurentRelationViewer.setUseHashlookup(true);

		createActions();
		hookListeners();
		initializeTree();

		//create a context menu in the view
		MenuManager manager = new MenuManager();
		Control control = rootViewer.getControl();
		Menu menu = manager.createContextMenu(control);
		control.setMenu(menu);
		getSite().registerContextMenu("edu.tamu.aser.tide.views.echomenu", manager, rootViewer);
	}


	private void initializeTree() {
		//initialize the tree
		bugDetail = new BugDetail(null);
//		Yanze
		relationDetail = new RelationDetail(bugDetail);
		bugDetail.setRelation(relationDetail);
	}

	protected void createActions() {
		jumpToLineInEditor = new Action() {
			@Override
			public void run(){
				ISelection selection = rootViewer.getSelection();
				Object obj = ((IStructuredSelection) selection).getFirstElement();
				if (obj instanceof EventNode) {
					HashMap<String, IFile> map = new HashMap<>();
					HashMap<String, Integer> map2 = new HashMap<>();
					ITreeNode parent = ((EventNode) obj).getParent().getParent();
					if(parent instanceof CSuperWriteNode){
						CSuperWriteNode node = (CSuperWriteNode) parent;
						map = node.event_ifile_map;
						map2 = node.event_line_map;

					} else if (parent instanceof CReadNode) {
						CReadNode node = (CReadNode) parent;
						map = node.event_ifile_map;
						map2 = node.event_line_map;
					}else if (parent instanceof CWriteNode){
						CWriteNode node = (CWriteNode) parent;
						map = node.event_ifile_map;
						map2 = node.event_line_map;
					}

					IFile file = map.get(((EventNode) obj).getName());

					IEditorRegistry editorRegistry = PlatformUI.getWorkbench().getEditorRegistry();
					String editorId = editorRegistry.getDefaultEditor(file.getFullPath().toString()).getId();
					IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
					try {
						AbstractTextEditor ePart = (AbstractTextEditor) page.openEditor(new FileEditorInput(file),editorId);
						IDocument document = ePart.getDocumentProvider().getDocument(ePart.getEditorInput());
						if (document != null) {
							IRegion lineInfo = null;
							try {
								int line = map2.get(((EventNode) obj).getName());
								lineInfo = document.getLineInformation(line - 1);
							} catch (BadLocationException e) {
								e.printStackTrace();
							}
							if (lineInfo != null) {
								ePart.selectAndReveal(lineInfo.getOffset(),
										lineInfo.getLength());
							}
						}
					} catch (PartInitException e) {
						e.printStackTrace();
					}
				}else if(obj instanceof FixNode){
					//TODO: show the fix view?
				}else{
//					true if the node is expanded, and false if collapsed
					 if(rootViewer.getExpandedState(obj)){
						 rootViewer.collapseToLevel(obj, 1);
					 }else{
						 rootViewer.expandToLevel(obj, 1);
					 }
				}
			}
		};
		jumpToDetailLine = new Action() {
			@Override
			public void run(){
				ISelection selection = concurentRelationViewer.getSelection();
				Object obj = ((IStructuredSelection) selection).getFirstElement();
				if (obj instanceof EventNode) {
					HashMap<String, IFile> map = new HashMap<>();
					HashMap<String, Integer> map2 = new HashMap<>();
					ITreeNode parent = ((EventNode) obj).getParent().getParent();
					if(parent instanceof CSuperWriteNode){
						CSuperWriteNode node = (CSuperWriteNode) parent;
						map = node.event_ifile_map;
						map2 = node.event_line_map;
					} else if (parent instanceof CReadNode) {
						CReadNode node = (CReadNode) parent;
						map = node.event_ifile_map;
						map2 = node.event_line_map;
					}else if (parent instanceof CWriteNode){
						CWriteNode node = (CWriteNode) parent;
						map = node.event_ifile_map;
						map2 = node.event_line_map;
					}

					IFile file = map.get(((EventNode) obj).getName());

					IEditorRegistry editorRegistry = PlatformUI.getWorkbench().getEditorRegistry();
					String editorId = editorRegistry.getDefaultEditor(file.getFullPath().toString()).getId();
					IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
					try {
						AbstractTextEditor ePart = (AbstractTextEditor) page.openEditor(new FileEditorInput(file),editorId);
						IDocument document = ePart.getDocumentProvider().getDocument(ePart.getEditorInput());
						if (document != null) {
							IRegion lineInfo = null;
							try {
								int line = map2.get(((EventNode) obj).getName());
								lineInfo = document.getLineInformation(line - 1);
							} catch (BadLocationException e) {
								e.printStackTrace();
							}
							if (lineInfo != null) {
								ePart.selectAndReveal(lineInfo.getOffset(),
										lineInfo.getLength());
							}
						}
					} catch (PartInitException e) {
						e.printStackTrace();
					}
				}else if(obj instanceof FixNode){
					//TODO: show the fix view?
				}else{
//					true if the node is expanded, and false if collapsed
					 if(concurentRelationViewer.getExpandedState(obj)){
						 concurentRelationViewer.collapseToLevel(obj, 1);
					 }else{
						 concurentRelationViewer.expandToLevel(obj, 1);
					 }
				}
			}
		};
		showDetailTree = new Action() {
			@Override
			public void run() {
				ISelection selection = rootViewer.getSelection();
				Object obj = ((IStructuredSelection) selection).getFirstElement();
				if (obj instanceof CSuperWriteNode) {//RelationNode?
					curDetail = ((CSuperWriteNode)obj).getRelationDetail();
					concurentRelationViewer.setInput(curDetail);
					concurentRelationViewer.expandToLevel(curDetail, 1);
				}
			}
		};
	}

	private Object curDetail = null;

	public void initialGUI(Set<ITIDEBug> bugs) {
		//clear all
		if(curDetail != null){
			concurentRelationViewer.remove(curDetail);
		}
		bugDetail.clear();
		existingbugs.clear();
		existingSigs.clear();
		//refresh
		rootViewer.refresh();
		concurentRelationViewer.refresh();
		rootViewer.setInput(translateToInput((HashSet<ITIDEBug>) bugs));
//		rootViewer.expandAll();
		rootViewer.expandToLevel(relationDetail, 1);
	}


	@SuppressWarnings("unchecked")
	public void updateGUI(HashSet<ITIDEBug> addedbugs, HashSet<ITIDEBug> removedbugs) {
		HashSet<ITIDEBug> addedbugsClone = (HashSet<ITIDEBug>)addedbugs.clone();
//		addedbugs.removeAll(removedbugs);
//		removedbugs.removeAll(addedbugsClone);
		//only update changed bugs
		for (ITIDEBug removed : removedbugs) {
			if(existingbugs.contains(removed)){
				if(removed instanceof TIDERace){
					TIDERace race = (TIDERace) removed;
					String sig = race.sig;
					boolean noleft = relationDetail.removeChild(race, sig);//remove one race from relation
					if(noleft){
						relationDetail.removeThisEntry(sig);
						existingSigs.remove(sig);
					}
					existingbugs.remove(removed);
				}
			}else{
				System.err.println("Existing bugs should contain this removed bug.");
			}
		}
		//add new
		addToInput(addedbugs);
		rootViewer.refresh();
		rootViewer.expandToLevel(relationDetail, 1);
		concurentRelationViewer.setInput(null);
		concurentRelationViewer.refresh();
	}


	public void considerBugs(HashSet<ITIDEBug> considerbugs) {
		addToInput(considerbugs);
		rootViewer.refresh();
		rootViewer.expandToLevel(relationDetail, 1);
		concurentRelationViewer.refresh();//?
	}


	public void ignoreBugs(HashSet<ITIDEBug> removedbugs){
		for (ITIDEBug ignore : removedbugs) {
			if(existingbugs.contains(ignore)){
				if(ignore instanceof TIDERace){
					TIDERace race = (TIDERace) ignore;
					String sig = race.sig;
					boolean noleft = relationDetail.removeChild(race, sig);
					if(noleft){
						relationDetail.removeThisEntry(sig);
						existingSigs.remove(sig);
					}
				}
			}else{
				System.err.println("Existing bugs should contain this removed bug.");
			}
		}
		existingbugs.removeAll(removedbugs);

		rootViewer.refresh();
		rootViewer.expandToLevel(relationDetail, 1);
		//reset concurentRelationViewer
		if(curDetail != null){
			concurentRelationViewer.remove(curDetail);
		}
//		concurentRelationViewer.refresh();
	}


	public void updateGUI(Set<ITIDEBug> bugs) {//update all, not efficient
		//remove old
		rootViewer.remove(relationDetail);
		rootViewer.refresh();
		//also remove things in bugdetail
		rootViewer.setInput(translateToInput((HashSet<ITIDEBug>) bugs));
		//		treeViewer.expandAll();
		rootViewer.refresh();
		concurentRelationViewer.refresh();
	}

	private BugDetail translateToInput(HashSet<ITIDEBug> bugs) {
		bugDetail.clear();
		for (ITIDEBug bug : bugs) {
			if(bug instanceof TIDERace){
				TIDERace race = (TIDERace) bug;
				String sig = race.sig;
				if(existingSigs.contains(sig)){
					//previously added
					relationDetail.addChild(race, sig);
				}else{
					//newly added
					relationDetail.createChild(race, sig);
					existingSigs.add(sig);
				}
				existingbugs.add(race);
			}
		}
		return bugDetail;
	}

	private void addToInput(HashSet<ITIDEBug> bugs) {
		for (ITIDEBug bug : bugs) {
			if(bug instanceof TIDERace){
				TIDERace race = (TIDERace) bug;
				String sig = race.sig; // may need to change
				if(existingSigs.contains(sig)){
					//previously added
					relationDetail.addChild(race, sig, true);
				}else{
					//newly added
					relationDetail.createChild(race, sig, true);
					existingSigs.add(sig);
				}
				existingbugs.add(race);
			}
		}
	}

//	private void generateRelations(HashSet<ITIDEBug> bugs){
//		String wholesig;
//		for (ITIDEBug bug : bugs) {
//			if(bug instanceof TIDERace){
////				Yanze
//				TIDERace raceBug = (TIDERace)bug;
//				wholesig = raceBug.initsig;
////				set trace to corresponding nodes
//				raceBug.node1.setTrace(raceBug.traceMsg.get(0));
//				raceBug.node2.setTrace(raceBug.traceMsg.get(1));
//				raceBug.node1.setFileTrace(raceBug.event_ifile_map, raceBug.event_line_map);
//				raceBug.node2.setFileTrace(raceBug.event_ifile_map, raceBug.event_line_map);
//				if (existRelationMap.containsKey(wholesig) ) {
//					HashMap<String, ConcurrentRelation> relation = existRelationMap.get(wholesig);
//					if (raceBug.node1 instanceof WriteNode) {
//						if (relation.containsKey(raceBug.node1.toString())) {
//							relation.get(raceBug.node1.toString()).addConcurrentRW(raceBug.node2);
//						} else {
//							relation.put(raceBug.node1.toString(), new ConcurrentRelation((WriteNode) raceBug.node1));
//							relation.get(raceBug.node1.toString()).addConcurrentRW(raceBug.node2);
//						}
//					}
//					if (raceBug.node2 instanceof WriteNode) {
//						if (relation.containsKey(raceBug.node2.toString())) {
//							relation.get(raceBug.node2.toString()).addConcurrentRW(raceBug.node1);
//						} else {
//							relation.put(raceBug.node2.toString(), new ConcurrentRelation((WriteNode) raceBug.node2));
//							relation.get(raceBug.node2.toString()).addConcurrentRW(raceBug.node1);
//						}
//					}
//				} else {
//					HashMap<String, ConcurrentRelation> relation = new HashMap<String, ConcurrentRelation>();
//					if ( raceBug.node1 instanceof WriteNode ) {
//						WriteNode node1 = (WriteNode) raceBug.node1;
//						relation.put(node1.toString(), new ConcurrentRelation(node1));
//						relation.get(node1.toString()).addConcurrentRW(raceBug.node2);
//					}
//					if ( raceBug.node2 instanceof WriteNode ) {
//						WriteNode node2 = (WriteNode) raceBug.node2;
//						relation.put(node2.toString(), new ConcurrentRelation(node2));
//						relation.get(node2.toString()).addConcurrentRW(raceBug.node1);
//					}
//					existRelationMap.put(wholesig, relation);
//				}
//			}
//		}
//	}



	protected void hookListeners() {
		rootViewer.addDoubleClickListener(new IDoubleClickListener() {
			@Override
			public void doubleClick(DoubleClickEvent event) {
				jumpToLineInEditor.run();
			}
		});
		rootViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			@Override
			public void selectionChanged(SelectionChangedEvent event) {
				showDetailTree.run();
			}
		});
		concurentRelationViewer.addDoubleClickListener(new IDoubleClickListener() {
			@Override
			public void doubleClick(DoubleClickEvent event) {
				jumpToDetailLine.run();
			}
		});
	}

	@Override
	public void setFocus() {
	}


}
