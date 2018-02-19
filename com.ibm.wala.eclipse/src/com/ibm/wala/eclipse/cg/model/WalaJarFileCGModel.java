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
package com.ibm.wala.eclipse.cg.model;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import org.eclipse.jface.window.ApplicationWindow;

import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.io.FileProvider;
import com.ibm.wala.ide.ui.SWTTreeViewer;

abstract public class WalaJarFileCGModel implements WalaCGModel {

  /**
   * Specifies the path of the jars files to be analyzed, each jar file
   * separated by ';'
   */
  protected String appJar;

  protected CallGraph callGraph;

  protected Collection roots;

  protected final File exclusionsFile;
  
  /**
   * @param appJar
   *          Specifies the path of the jars files to be analyzed, each jar file
   *          separated by ';'
   */
  public WalaJarFileCGModel(String appJar, File exclusionsFile) {
    this.appJar = appJar;
    this.exclusionsFile = exclusionsFile;
  }

  /**
   * @see CallGraphBuilderImpl.processImpl warning: this is bypassing emf and
   *      may cause problems
   */
  public void buildGraph() throws WalaException, CancelException {
    AnalysisScope escope = createAnalysisScope(exclusionsFile);
    callGraph = createCallGraph(escope);
    roots = inferRoots(callGraph);
  }

  public CallGraph getGraph() {
    return callGraph;
  }

  public Collection getRoots() {
    return roots;
  }

  /**
   * @see SWTCallGraph
   */
  protected AnalysisScope createAnalysisScope(File exclusionsFile) throws WalaException {
    try {
      return AnalysisScopeReader.makeJavaBinaryAnalysisScope(appJar, exclusionsFile /*FileProvider.getFileFromPlugin(CoreTestsPlugin.getDefault(), "Java60RegressionExclusions.txt")*/);
    } catch (IOException e) {
      throw new WalaException(e.toString());
    }
  }

  abstract protected CallGraph createCallGraph(AnalysisScope scope) throws WalaException, CancelException;

  abstract protected Collection inferRoots(CallGraph cg) throws WalaException;

  public ApplicationWindow makeUI(Graph graph, Collection<?> roots) throws WalaException {
    final SWTTreeViewer v = new SWTTreeViewer();
    v.setGraphInput(graph);
    v.setRootsInput(roots);
    v.run();
    return v.getApplicationWindow();
  }
}
