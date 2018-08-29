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
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.internal.core.JavaElementDelta;
import org.eclipse.jdt.internal.core.SourceMethod;
import org.eclipse.jdt.internal.core.SourceType;


public class MyJavaElementChangeCollector implements IElementChangedListener{
	/**
	 * only collect changes here
	 */
	public HashMap<String, ChangedItem> collectedChanges = new HashMap<>();
	//map<IFile, hashset<changeitems>>
	public HashMap<String, IFile> sigFiles = new HashMap<>();
	public HashMap<String, ArrayList<ChangedItem>> sigChanges = new HashMap<>();

	public boolean work = false;
	public void work(boolean work) {
		this.work = work;
	}

	public void resetCollectedChanges(){
		collectedChanges.clear();
		sigFiles.clear();
		sigChanges.clear();
	}

	@SuppressWarnings("restriction")
	@Override
	public void elementChanged(ElementChangedEvent event) {
		if(!work)
			return;
		MyJavaElementChangeReporter reporter = Activator.getDefaultReporter();
		IJavaElementDelta delta= event.getDelta();
		IJavaElementDelta[] deltas = delta.getAffectedChildren();
		while(deltas.length>0){
			delta = deltas[0];
			deltas = delta.getAffectedChildren();
		}

		IJavaElement elem = ((JavaElementDelta)delta).getElement();
		if(elem instanceof ICompilationUnit) {
			reporter.parser.setSource((ICompilationUnit)elem);
			ASTNode unit = reporter.parser.createAST(null);
			if(unit!=null)
				unit.accept(reporter.myASTVisitor);
		}else if (elem instanceof SourceMethod) {//method modifier change, not working
			String methodName = ((SourceMethod)elem).getElementName();
			IJavaElement elem2 = ((SourceMethod)elem).getParent();
			String className = ((SourceType)elem2).getElementName();
			String packageName = ((SourceType)elem2).getPackageFragment().getElementName();
			reporter.myASTVisitor.setChangedItem(packageName, className, methodName);
		}

		if(reporter.myASTVisitor.hasChanged()){
			final IPath path = delta.getElement().getPath();
			IFile file = ResourcesPlugin.getWorkspace().getRoot().getFile(path);
			String sig = null;
			ArrayList<ChangedItem> changedItems = reporter.myASTVisitor.getChangedItem();
			for (ChangedItem changedItem : changedItems) {
				if(sig == null){
					sig = changedItem.packageName+"."+changedItem.className;
				}
				String id = changedItem.packageName+"."+changedItem.className+"."+changedItem.methodName;
				//always store the newest change
				collectedChanges.put(id, changedItem);
			}
			sigFiles.put(sig, file);
			ArrayList<ChangedItem> copy = new ArrayList<>();
			copy.addAll(changedItems);
			sigChanges.put(sig, copy);

			System.out.println("Changes collected:");
			for (ChangedItem changedItem : changedItems) {
				String id = changedItem.packageName+"."+changedItem.className+"."+changedItem.methodName;
				System.out.println(" == " + id);
			}

			reporter.myASTVisitor.reSetChangedItem();
		}
	}

}
