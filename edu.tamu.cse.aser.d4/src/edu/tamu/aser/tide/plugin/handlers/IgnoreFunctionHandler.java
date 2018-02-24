package edu.tamu.aser.tide.plugin.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.common.NotDefinedException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.ITextEditor;

import edu.tamu.aser.tide.engine.TIDEEngine;
import edu.tamu.aser.tide.plugin.ChangedItem;
import edu.tamu.aser.tide.views.EchoDLView;
import edu.tamu.aser.tide.views.EchoRaceView;
import edu.tamu.aser.tide.views.EchoReadWriteView;

//"Ignore This Method"
public class IgnoreFunctionHandler extends AbstractHandler {

	public ConvertHandler cHandler;
	public EchoRaceView echoRaceView;
	public EchoReadWriteView echoRWView;
	public EchoDLView echoDLView;
	public TIDEEngine engine;

	public IgnoreFunctionHandler() {
		super();
		cHandler = edu.tamu.aser.tide.plugin.Activator.getDefault().getConvertHandler();
	}


	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		boolean ignore = false;
		String command = "";
		try {
			command = event.getCommand().getName();
		} catch (NotDefinedException e) {
			e.printStackTrace();
		}
		if(command.equals("Consider This Method")){
			ignore = false;
		}else if(command.equals("Ignore This Method")){//ignore this variable
			ignore = true;
		}else{
			return null;
		}

		if(cHandler == null){
			cHandler = edu.tamu.aser.tide.plugin.Activator.getDefault().getConvertHandler();
			if(cHandler == null)
				return null;
		}
		echoRaceView = cHandler.getEchoRaceView();
		echoRWView = cHandler.getEchoReadWriteView();
		echoDLView = cHandler.getEchoDLView();
		engine = cHandler.getCurrentModel().getBugEngine();

//		ISelection sel0 = HandlerUtil.getActiveMenuSelection(event);
//		TreeSelection treeSel0 = (TreeSelection) sel0;
//		Object element0 = treeSel0.getFirstElement();

		try {
			ITextEditor activeEditor = (ITextEditor) PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActiveEditor();
			IJavaElement element = JavaUI.getEditorInputJavaElement(activeEditor.getEditorInput());
			if (element instanceof ICompilationUnit ) {
				ITextSelection sel = (ITextSelection) activeEditor.getSelectionProvider().getSelection();
				IJavaElement selected = ((ICompilationUnit) element).getElementAt(sel.getOffset());
				if (selected != null && selected.getElementType() == IJavaElement.METHOD) {
					// When the selected code is a method
					IPath path = selected.getPath();
					IFile file = ResourcesPlugin.getWorkspace().getRoot().getFile(path);
					IJavaProject javaProject = selected.getJavaProject();
					//packageName classname methodname
					ChangedItem ignore_method = new ChangedItem();
					String methodname = selected.getElementName();
					IJavaElement parent = selected.getParent();
					String classname = parent.getElementName();
					IJavaElement gparent = parent.getParent().getParent();
					String packagename = gparent.getElementName();
					if(packagename.contains(".java")){
						packagename = "";
					}
					ignore_method.methodName = methodname;
					ignore_method.className = classname;
					ignore_method.packageName = packagename;
					if(ignore){//Ignore function
						System.err.println("FROM IGNORE FUNCTION: " + packagename + " " + classname + " " + methodname);
						cHandler.handleIgnoreMethod(javaProject, file, ignore_method);
					}else{//consider function
						System.err.println("FROM CONSIDER FUNCTION: " + packagename + " " + classname + " " + methodname);
						cHandler.handleConsiderMethod(javaProject, file, ignore_method);
					}
//					System.out.println("Ignore function removed");
				} else {
					// dealing with other situations
					System.err.println("should not respond to this selection: " + selected.getElementType());
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

}
