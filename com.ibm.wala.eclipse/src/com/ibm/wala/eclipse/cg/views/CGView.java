/*******************************************************************************
 * Copyright (c) 2002 - 2006 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.wala.eclipse.cg.views;

import java.io.*;
import java.util.Collection;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.viewers.IOpenListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeSelection;
import org.eclipse.jface.viewers.OpenEvent;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.ViewPart;

import com.ibm.wala.cast.loader.AstMethod;
import com.ibm.wala.cast.tree.CAstSourcePositionMap.Position;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.eclipse.Activator;
import com.ibm.wala.eclipse.cg.model.WalaCGModel;
import com.ibm.wala.eclipse.cg.model.WalaJarFileCGModelWithMain;
import com.ibm.wala.eclipse.cg.model.WalaProjectCGModelWithMain;
import com.ibm.wala.eclipse.cg.model.WalaProjectCGModelWithType;
import com.ibm.wala.eclipse.cg.model.WalaWebPageCGModel;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.ide.util.JdtUtil;
import com.ibm.wala.eclipse.util.WalaToJavaEltConverter;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.io.FileProvider;

/**
 * This sample class demonstrates how to plug-in a new workbench view. The view
 * shows data obtained from the model. The sample creates a dummy model on the
 * fly, but a real implementation would connect to the model available either in
 * this or another plug-in (e.g. the workspace). The view is connected to the
 * model using a content provider.
 * <p>
 * The view uses a label provider to define how model objects should be
 * presented in the view. Each view can present the same model objects using
 * different labels and icons, if needed. Alternatively, a single label provider
 * can be shared between views in order to ensure that objects of the same type
 * are presented in the same way everywhere.
 * <p>
 * 
 * @author aying
 */

public class CGView extends ViewPart {

  public static final String ID = "com.ibm.wala.eclipse.cg.views.CGView";

  private TreeViewer viewer;

  private static final String defaultExclusionsFile;
  
  static {
    String file = null;
    try {  
      file = FileProvider.getFileFromPlugin(Activator.getDefault(), "EclipseDefaultExclusions.txt").getAbsolutePath();
    } catch (IOException e) {
    }
    defaultExclusionsFile = file;
  }
 
  /**
   * The constructor.
   */
  public CGView() {
  }

  /**
   * This is a callback that will allow us to create the viewer and initialize
   * it.
   */
  @Override
  public void createPartControl(Composite parent) {
    IFile selectedJar = getSelectedJar();
    if (selectedJar != null) {
      createJarViewer(parent, selectedJar);

    } else {
      IFile selectedScript = getSelectedScript();
      if (selectedScript != null) {
        createScriptViewer(parent, selectedScript);

      } else {
        IJavaProject selectedProject = getSelectedProject();
        if (selectedProject != null) {
          createViewer(parent, selectedProject);

        } else {
          IType selectedType = getSelectedType();
          if (selectedType != null) {
            createViewer(parent, selectedType);
          }
        }
      }
    }
  }

  private IType getSelectedType() {
    ISelection currentSelection = getSite().getWorkbenchWindow().getSelectionService().getSelection();

    if (currentSelection instanceof IStructuredSelection) {
      Object selected = ((IStructuredSelection) currentSelection).getFirstElement();
      if (selected instanceof IType) {
        return (IType) selected;
      }
    }
    return null;
  }

  private IJavaProject getSelectedProject() {
    ISelection currentSelection = getSite().getWorkbenchWindow().getSelectionService().getSelection();

    if (currentSelection instanceof IStructuredSelection) {
      Object selected = ((IStructuredSelection) currentSelection).getFirstElement();
      if (selected instanceof IJavaProject) {
        return (IJavaProject) selected;
      }
    }
    return null;
  }

  private IFile getSelectedJar() {
    ISelection currentSelection = getSite().getWorkbenchWindow().getSelectionService().getSelection();

    if (currentSelection instanceof IStructuredSelection) {
      Object selected = ((IStructuredSelection) currentSelection).getFirstElement();
      if (selected instanceof IFile && ((IFile) selected).getFileExtension().equals("jar")) {
        return (IFile) selected;
      }
    }
    return null;
  }

  private IFile getSelectedScript() {
    ISelection currentSelection = getSite().getWorkbenchWindow().getSelectionService().getSelection();

    if (currentSelection instanceof IStructuredSelection) {
      Object selected = ((IStructuredSelection) currentSelection).getFirstElement();
      if (selected instanceof IFile && ((IFile) selected).getFileExtension().equals("html")) {
        return (IFile) selected;
      }
    }
    return null;
  }

  private void createJarViewer(Composite parent, IFile jarFile) {
    // get the selected jar file
    String applicationJar = jarFile.getRawLocation().toString();
    IJavaProject project = JdtUtil.getJavaProject(jarFile);

    // compute the call graph
    WalaCGModel model = new WalaJarFileCGModelWithMain(applicationJar, new File(defaultExclusionsFile));
    createViewer(parent, project, model);
  }

  private void createScriptViewer(Composite parent, IFile htmlFile) {
    // get the selected script file
    String scriptPathName = htmlFile.getRawLocation().toString();
    // IProject project = htmlFile.getProject();

    // compute the call graph
    WalaCGModel model = new WalaWebPageCGModel(scriptPathName);
    createViewer(parent, null, model);
  }
  
  private void createViewer(Composite parent, IJavaProject project) 
     
  {
    // compute the call graph
    try {
      WalaCGModel model = new WalaProjectCGModelWithMain(project, defaultExclusionsFile);
      createViewer(parent, project, model);
    } catch (JavaModelException e) {
      // todo: proper eclipse error reporting
      throw new RuntimeException(e); 
    } catch (IOException e) {
      // todo: proper eclipse error reporting
      throw new RuntimeException(e); 	
    } catch (CoreException e) {
      // todo: proper eclipse error reporting
      throw new RuntimeException(e); 
     }
  }

  private void createViewer(Composite parent, IType type) {
    try {
      IJavaProject project = type.getJavaProject();

      // compute the call graph
      WalaCGModel model = new WalaProjectCGModelWithType(project, type, defaultExclusionsFile);
      createViewer(parent, project, model);
    } catch (JavaModelException e) {
      // todo: proper eclipse error reporting
      throw new RuntimeException(e); 
    } catch (IOException e) {
      // todo: proper eclipse error reporting
      throw new RuntimeException(e); 	
    } catch (CoreException e) {
      // todo: proper eclipse error reporting
      throw new RuntimeException(e); 
    }
  }

  private void createViewer(Composite parent, IJavaProject project, WalaCGModel model) {
    try {
      model.buildGraph();
      Collection roots = model.getRoots();
      CallGraph graph = model.getGraph();

      // convert call graph nodes to Eclipse JDT elements
      final Map<Integer, IJavaElement> capaNodeIdToJavaElement = WalaToJavaEltConverter.convert(model.getGraph(), project);

      // create the tree view
      viewer = new TreeViewer(parent,SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
      viewer.setContentProvider(new CGContentProvider(graph, roots));
      viewer.setLabelProvider(new CGJavaLabelProvider(capaNodeIdToJavaElement));
      viewer.setInput(getViewSite());
      viewer.addOpenListener(new IOpenListener() {
        // open the file when element in the tree is clicked
        public void open(OpenEvent e) {
          ISelection sel = e.getSelection();
          if (sel instanceof ITreeSelection) {
            ITreeSelection treeSel = (ITreeSelection) sel;
            Object selectedElt = treeSel.getFirstElement();
            if (selectedElt instanceof CGNode) {
              try {
                CGNode capaNode = (CGNode) selectedElt;
                IJavaElement jdtElt = capaNodeIdToJavaElement.get(capaNode.getGraphNodeId());
                if (jdtElt != null) {
                  JavaUI.revealInEditor(JavaUI.openInEditor(jdtElt), jdtElt);
                }
              } catch (PartInitException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
              } catch (JavaModelException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
              }
            } else if (selectedElt instanceof Pair) {
              Pair nodeAndCallSite = (Pair) selectedElt;
              CGNode caller = (CGNode) nodeAndCallSite.fst;
              CallSiteReference site = (CallSiteReference) nodeAndCallSite.snd;
              IMethod method = caller.getMethod();

              if (method instanceof AstMethod) {
                Position sourcePos = ((AstMethod) method).getSourcePosition(site.getProgramCounter());
                org.eclipse.jdt.core.IMethod jdtMethod = (org.eclipse.jdt.core.IMethod) capaNodeIdToJavaElement.get(caller
                    .getGraphNodeId());
                ICompilationUnit compilationUnit = jdtMethod.getCompilationUnit();
                CompilationUnit ast = getASTRoot(compilationUnit);
                int startPos = ast.getPosition(sourcePos.getFirstLine(), sourcePos.getFirstCol());
                int endPos = ast.getPosition(sourcePos.getLastLine(), sourcePos.getLastCol());
                try {
                  IEditorPart editorPart = EditorUtility.openInEditor(jdtMethod);
                  EditorUtility.revealInEditor(editorPart, startPos, endPos - startPos);
                } catch (PartInitException e1) {
                  // TODO Auto-generated catch block
                  e1.printStackTrace();
                }
              }
            }
          }
        }
      });
    } catch (JavaModelException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (WalaException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (CancelException e) {
      e.printStackTrace();
    }
  }

  /**
   * Passing the focus request to the viewer's control.
   */
  @Override
  public void setFocus() {
    if (viewer != null && viewer.getControl() != null) {
      viewer.getControl().setFocus();
    }
  }

  private CompilationUnit getASTRoot(ICompilationUnit compilationUnit) {
    ASTParser astParser = ASTParser.newParser(AST.JLS3);
    astParser.setSource(compilationUnit);
    astParser.setResolveBindings(true);
    CompilationUnit astRoot = (CompilationUnit) astParser.createAST(null);
    return astRoot;
  }

}
