package edu.tamu.aser.tide.plugin;

import java.util.ArrayList;
import java.util.HashMap;

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
				myASTVisitor.reSetChangedItem();

			}else if(deltas.getFlags() == IJavaElementDelta.F_PRIMARY_RESOURCE){
				System.out.println(deltas.getFlags() + " this is the flag...........");
				ICompilationUnit elementDelta = (ICompilationUnit) deltas.getElement();
				System.out.println(elementDelta.toString() + " this is element delta.");
				parser.setSource(elementDelta);
				ASTNode unit = parser.createAST(null);

				if(unit!=null)
					unit.accept(myASTVisitor);
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

	public boolean work = true;
	public void work(boolean work) {
		this.work = work;
	}

	@Override
	public void elementChanged(ElementChangedEvent event) {
		if(!work)
			return;
		IJavaElementDelta delta= event.getDelta();
		IJavaElementDelta[] deltas = delta.getAffectedChildren();
		while(deltas.length>0){
			delta = deltas[0];
			deltas = delta.getAffectedChildren();
		}

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

	public class MyASTVisitor extends ASTVisitor{
		private HashMap<String,MethodDeclaration> subtrees = new HashMap<String,MethodDeclaration>();
		private ArrayList<ChangedItem> changedItems = new ArrayList<>();
		private    boolean active;

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

		public void reSetChangedItem(){
			active = false;
			changedItems.clear();
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

			if(methodName.equals(className))
				methodName = "<init>";

			//full signature
			String fullName = packageName+"."+className+"."+methodName;

			// Finding match for this methods name(mName) in saved method subtrees...
			boolean methodHasChanged = false;
			if (subtrees.containsKey(fullName)) {
				// Found match
				// Comparing new subtree to one saved during an earlier event (using ASTNode.subtreeMatch())
				methodHasChanged = !node.subtreeMatch(new ASTMatcher(), subtrees.get(fullName));
			} else {
				// No earlier entry found, definitely changed => added
				methodHasChanged = true;
			}
			if (methodHasChanged) {
				//record changes
				new_change.packageName = packageName;
				new_change.methodName = methodName;
				new_change.className = className;

				if(!changedItems.contains(new_change)){
					changedItems.add(new_change);
					active = true;
				}
			}
			// "subtrees" must be updated with every method's AST subtree in order for this to work
			subtrees.put(fullName, node);
			// continue visiting after first MethodDeclaration
			return false;
		}



	}

}
