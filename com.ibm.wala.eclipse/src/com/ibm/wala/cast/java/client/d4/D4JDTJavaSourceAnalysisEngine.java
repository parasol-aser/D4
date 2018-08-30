/*******************************************************************************
 * Copyright (c) 2007 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.wala.cast.java.client.d4;

import java.io.IOException;
import java.util.HashMap;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;

import com.ibm.wala.cast.ir.ssa.AstIRFactory;
import com.ibm.wala.cast.java.ipa.callgraph.JavaSourceAnalysisScope;
import com.ibm.wala.cast.java.translator.jdt.JDTClassLoaderFactory;
import com.ibm.wala.classLoader.ClassLoaderFactory;
import com.ibm.wala.classLoader.Language;
import com.ibm.wala.ide.client.EclipseProjectSourceAnalysisEngine;
import com.ibm.wala.ide.util.EclipseProjectPath;
import com.ibm.wala.ide.util.JavaEclipseProjectPath;
import com.ibm.wala.ide.util.JdtUtil;
import com.ibm.wala.ide.util.EclipseProjectPath.AnalysisScopeType;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.cfa.ZeroXInstanceKeys;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.util.config.SetOfClasses;

import edu.tamu.wala.increpta.ipa.callgraph.propagation.IPASSAPropagationCallGraphBuilder;
import edu.tamu.wala.increpta.ipa.callgraph.propagation.IPAZeroXCFABuilder;
import edu.tamu.wala.increpta.util.IPAUtil;

public class D4JDTJavaSourceAnalysisEngine<I extends InstanceKey> extends EclipseProjectSourceAnalysisEngine<IJavaProject, I> {
  private boolean dump;

  public D4JDTJavaSourceAnalysisEngine(IJavaProject project) {
    super(project);
  }

  public D4JDTJavaSourceAnalysisEngine(String projectName) {
    this(JdtUtil.getNamedProject(projectName));
  }

  public void setDump(boolean dump) {
    this.dump = dump;
  }

  @Override
  protected ClassLoaderFactory makeClassLoaderFactory(SetOfClasses exclusions) {
      return new JDTClassLoaderFactory(exclusions, dump);
  }

  @Override
  protected AnalysisScope makeAnalysisScope() {
      return new JavaSourceAnalysisScope();
  }

  @Override
  protected ClassLoaderReference getSourceLoader() {
      return JavaSourceAnalysisScope.SOURCE;
  }

  @Override
  public IAnalysisCacheView makeDefaultCache() {
    return new AnalysisCacheImpl(AstIRFactory.makeDefaultFactory());
  }

  @Override
  protected EclipseProjectPath<?, IJavaProject> createProjectPath(
          IJavaProject project) throws IOException, CoreException {
    project.open(new NullProgressMonitor());
      return JavaEclipseProjectPath.make(project, AnalysisScopeType.SOURCE_FOR_PROJ_AND_LINKED_PROJS);
  }

  @Override
  protected CallGraphBuilder getCallGraphBuilder(IClassHierarchy cha,
          AnalysisOptions options, IAnalysisCacheView cache) {
    builder = IPAUtil.makeIPAAstZeroCFABuilder(Language.JAVA, options, cache, cha, scope);
    return builder;
  }

  private CallGraphBuilder builder;

  public CallGraphBuilder getBuilder() {
    return builder;
  }

  public void updatePointerAnalaysis(CGNode node, HashMap<SSAInstruction, ISSABasicBlock> deleted, IR ir_old) {
    ((IPASSAPropagationCallGraphBuilder) builder).updatePointerAnalaysis(node, deleted, ir_old);
  }

}
