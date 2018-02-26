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
import java.util.Collection;

import com.ibm.wala.cast.ir.ssa.AstIRFactory;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.graph.InferGraphRoots;

/**
 * @author aying
 */
public class WalaJarFileCGModelWithMain extends WalaJarFileCGModel {

  /*
   * @see WalaCGModel
   */
  public WalaJarFileCGModelWithMain(String appJar, File exclusionsFile) {
    super(appJar, exclusionsFile);
  }

  /**
   * @see SWTCallGraph
   */
  @Override
  protected CallGraph createCallGraph(AnalysisScope scope) throws WalaException, CancelException {

    IClassHierarchy cha = ClassHierarchy.make(scope);

    Iterable<Entrypoint> entrypoints = com.ibm.wala.ipa.callgraph.impl.Util.makeMainEntrypoints(scope, cha);
    AnalysisOptions options = new AnalysisOptions(scope, entrypoints);

    // //
    // build the call graph
    // //
    com.ibm.wala.ipa.callgraph.CallGraphBuilder builder = Util.makeZeroCFABuilder(options, new AnalysisCache(AstIRFactory
        .makeDefaultFactory()), cha, scope, null, null);
    CallGraph cg = builder.makeCallGraph(options, null);
    return cg;
  }

  /**
   * @see SWTCallGraph
   */
  @Override
  protected Collection inferRoots(CallGraph cg) throws WalaException {
    return InferGraphRoots.inferRoots(cg);
  }
}
