package edu.tamu.aser.tide.plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.ElementChangedEvent;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IElementChangedListener;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaElementDelta;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTMatcher;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.internal.core.JavaElementDelta;
import org.eclipse.jdt.internal.core.SourceMethod;
import org.eclipse.jdt.internal.core.SourceType;
import org.eclipse.jface.viewers.IStructuredSelection;

import edu.tamu.aser.tide.plugin.handlers.ConvertHandler;


public class MyJavaElementChangeReporter implements IElementChangedListener {

	public MyASTVisitor myASTVisitor = new MyASTVisitor();
	public ASTParser parser = ASTParser.newParser(AST.JLS8);

	public void initialSubtree(ICompilationUnit elem, IStructuredSelection selection, IJavaProject project){
		parser.setProject(project);
		parser.setSource(elem);
		ASTNode unit = parser.createAST(null);
		if(unit!=null)
			unit.accept(myASTVisitor);
		myASTVisitor.reSetChangedItem();
	}


	private void traverseAndPrint(IJavaElementDelta deltas) {
		switch (deltas.getKind()) {
		case IJavaElementDelta.ADDED:
			IJavaElementDelta[] added = deltas.getAddedChildren();
			System.out.println(added + " was added");
			break;
		case IJavaElementDelta.REMOVED:
			IJavaElementDelta[] deled = deltas.getRemovedChildren();
			System.out.println(deled + " was removed");
			break;
		case IJavaElementDelta.CHANGED:
			IJavaElementDelta[] changed = deltas.getChangedChildren();
			if ((deltas.getFlags() == IJavaElementDelta.F_CHILDREN)) {
				System.out.println(deltas + " The change was in its children");
				for (int i = 0; i < changed.length; i++) {
					IJavaElementDelta delta2 = changed[i];
					System.out.println(changed + " send to check");
					traverseAndPrint(changed[0]);
				}
			}else if ((deltas.getFlags() == IJavaElementDelta.F_CONTENT) ) {
				System.out.println(deltas + " The change was in its content !!!! ");
				IJavaElementDelta elementDelta = (IJavaElementDelta) deltas.getElement();
				System.out.println(elementDelta.toString() + " this is element delta.");
				//
				parser.setSource((ICompilationUnit)elementDelta);
				ASTNode unit = parser.createAST(null);

				if(unit!=null)
					unit.accept(myASTVisitor);
//				ChangedItem item = myASTVisitor.getChangedItem();
//				System.out.println(item.methodName + " this is changed method. 000");
				myASTVisitor.reSetChangedItem();

			}else if(deltas.getFlags() == IJavaElementDelta.F_PRIMARY_RESOURCE){
				System.out.println(deltas.getFlags() + " this is the flag...........");
				ICompilationUnit elementDelta = (ICompilationUnit) deltas.getElement();
				System.out.println(elementDelta.toString() + " this is element delta.");
				//
				parser.setSource(elementDelta);
				ASTNode unit = parser.createAST(null);

				if(unit!=null)
					unit.accept(myASTVisitor);
//				ChangedItem item = myASTVisitor.getChangedItem();
//				System.out.println(item.methodName + " this is changed method. ");
				myASTVisitor.reSetChangedItem();

			}
			/* Others flags can also be checked */
			break;
		}
	}

	public void elementChanged2(ElementChangedEvent event){
		IJavaElementDelta delta = event.getDelta();
		traverseAndPrint(delta);
	}

//	public boolean work = false;
//	public void work(boolean work) {
//		this.work = work;
//	}

	@Override
	public void elementChanged(ElementChangedEvent event) {
//		if(!work)
//			return;
		IJavaElementDelta delta= event.getDelta();
		IJavaElementDelta[] deltas = delta.getAffectedChildren();
		while(deltas.length>0){
			delta = deltas[0];
			deltas = delta.getAffectedChildren();
		}
		//traverseAndPrint(delta);

		//if (delta instanceof JavaElementDelta)
		{
			IJavaElement elem = ((JavaElementDelta)delta).getElement();

			if(elem instanceof ICompilationUnit) {
				parser.setSource((ICompilationUnit)elem);
				ASTNode unit = parser.createAST(null);

				if(unit!=null)
					unit.accept(myASTVisitor);

			}else if (elem instanceof SourceMethod) {//method modifier change, not working
				String methodName = ((SourceMethod)elem).getElementName();

				IJavaElement elem2 = ((SourceMethod)elem).getParent();
				String className = ((SourceType)elem2).getElementName();
				String packageName = ((SourceType)elem2).getPackageFragment().getElementName();

				myASTVisitor.setChangedItem(packageName, className, methodName);
			}

			if(myASTVisitor.hasChanged()){
				final IPath path = delta.getElement().getPath();
				IFile file = ResourcesPlugin.getWorkspace().getRoot().getFile(path);
				final IJavaProject javaProject = delta.getElement().getJavaProject();
				ArrayList<ChangedItem> changedItems = myASTVisitor.getChangedItem();
				if(changedItems != null){
					ConvertHandler chandler = Activator.getDefault().getConvertHandler();
					if(chandler != null){
						chandler.handleMethodChange(javaProject,file,changedItems);//, myASTVisitor.rChanges
					}
				}
				myASTVisitor.reSetChangedItem();
			}
		}
	}

	public class MyASTVisitor extends ASTVisitor{
		private HashMap<String,MethodDeclaration> subtrees = new HashMap<String,MethodDeclaration>();
		private ArrayList<ChangedItem> changedItems = new ArrayList<>();
		private    boolean active;

		//check when lock/thread/method stmts: 0 -> not change; 1 -> new added; -1 -> new del; 2 -> objchange;
//		private ReporterChanges rChanges = new ReporterChanges();

		public MyASTVisitor() {
		}

		public ArrayList<ChangedItem> getChangedItem(){
			return changedItems;
		}

		public boolean hasChanged(){
			return active;
		}

		public void setChangedItem(String packageName, String className, String methodName){
			ChangedItem new_change = new ChangedItem();
			new_change.packageName = packageName;
			new_change.className = className;
			new_change.methodName = methodName;

			if(!changedItems.contains(new_change)){
				changedItems.add(new_change);
				active = true;
			}
		}

//		public void setChangedItem(String packageName, String className, String methodName){
//			changed.packageName = packageName;
//			changed.className = className;
//			changed.methodName = methodName;
//
//			active = true;
//		}

		public void reSetChangedItem(){
			active = false;
			changedItems.clear();
//			rChanges.clear();
//			changed.packageName = "";//empty
//			changed.className = "";
//			changed.methodName = "";
		}

		@Override
		public boolean visit(VariableDeclarationStatement node) {
			for (Iterator iter = node.fragments().iterator(); iter.hasNext();) {
				VariableDeclarationFragment fragment = (VariableDeclarationFragment) iter.next();
				// ... store these fragments somewhere
			}
			return false; // prevent that SimpleName is interpreted as reference
		}

		@Override
		public boolean visit(FieldDeclaration node) {
			return false;
		};

		@Override
		public boolean visit(MethodDeclaration node) {
			ChangedItem new_change = new ChangedItem();

			String methodName = node.getName().toString();//method name
			ASTNode parent = node.getParent();
			String className = ((TypeDeclaration)parent).getName().toString();
			ASTNode gparent = parent.getParent();
			String packageName = "";
			if(gparent instanceof CompilationUnit){
				PackageDeclaration pkage = ((CompilationUnit)gparent).getPackage();
				if(pkage != null){
					packageName = pkage.getName().toString();
				}
			}

			if(methodName.equals(className))//TODO:  support static constructor?
				methodName = "<init>";

			//full signature
			String fullName = packageName+"."+className+"."+methodName;

			//System.out.println(node);
			// Finding match for this methods name(mName) in saved method subtrees...
			boolean methodHasChanged = false;
			if (subtrees.containsKey(fullName)) {
				// Found match
				// Comparing new subtree to one saved during an earlier event (using ASTNode.subtreeMatch())
				methodHasChanged = !node.subtreeMatch(new ASTMatcher(), subtrees.get(fullName));
			} else {
				// No earlier entry found, definitely changed => added
				methodHasChanged = true;
//				rChanges.add(new_change, 3, fullName, 1);
			}
			if (methodHasChanged) {
				//record changes
				new_change.packageName = packageName;
				new_change.methodName = methodName;
				new_change.className = className;

				//for change are in code but not in ir: e.g. see sunflow/LigherServer/calculatePhotons
//				if(subtrees.containsKey(fullName)){
//					new_change.stmt_n = node.getBody().statements();
//					new_change.stmt_o = subtrees.get(fullName).getBody().statements();
//				}
				if(!changedItems.contains(new_change)){
					changedItems.add(new_change);
					active = true;
				}
//				MethodDeclaration changedmethod = subtrees.get(fullName);

//				if(subtrees.containsKey(fullName)){
//					//see the diff
//					List stmts_n = node.getBody().statements();
//					List stmts_o = subtrees.get(fullName).getBody().statements();
//					compareDifference(stmts_n, stmts_o, new_change);
//				}

				// "changed" is a HashMap of IMethods that have been earlierly identified as changed
				// "added" works similarly but for added methods (using IJavaElementDelta.getAddedChildren())
				//                if (!changed.containsKey(mName) && !added.containsKey(mName)) {
				//                    // Method has indeed changed and is not yet queued for further actions
				//                    changed.put(mName, (IMethod) node.resolveBinding().getJavaElement());
				//                }
			}
			// "subtrees" must be updated with every method's AST subtree in order for this to work
			subtrees.put(fullName, node);
			// continue visiting after first MethodDeclaration

			return false;
		}



	}

}
