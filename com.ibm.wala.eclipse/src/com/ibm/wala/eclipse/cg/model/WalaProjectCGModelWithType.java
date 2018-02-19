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

import java.io.*;
import java.util.Collection;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.*;

import com.ibm.wala.cast.java.ipa.callgraph.JavaSourceAnalysisScope;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.graph.InferGraphRoots;

/**
 * 
 * @author aying
 */
public class WalaProjectCGModelWithType extends WalaProjectCGModel {
  private final String mainClassName;
  
  public WalaProjectCGModelWithType(IJavaProject project, IType mainType, String exclusionsFile)
    throws IOException, CoreException 
  {
    super(project, exclusionsFile);
    mainClassName = "L" + mainType.getFullyQualifiedName().replace('.', '/');
  }


  @Override
  protected Iterable<Entrypoint> getEntrypoints(AnalysisScope scope, IClassHierarchy cha) {
    
    return com.ibm.wala.ipa.callgraph.impl.Util.makeMainEntrypoints(JavaSourceAnalysisScope.SOURCE, cha, new String[]{mainClassName});
  }

  @Override
  protected Collection inferRoots(CallGraph cg) throws WalaException {
    return InferGraphRoots.inferRoots(cg);
  }

}
