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

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaProject;

import com.ibm.wala.cast.java.client.JDTJavaSourceAnalysisEngine;
import com.ibm.wala.cast.java.ipa.callgraph.JavaSourceAnalysisScope;
import com.ibm.wala.client.AbstractAnalysisEngine;
import com.ibm.wala.ide.util.EclipseProjectPath;
import com.ibm.wala.ide.util.JavaEclipseProjectPath;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.ClassTargetSelector;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.MethodTargetSelector;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ipa.summaries.BypassClassTargetSelector;
import com.ibm.wala.ipa.summaries.BypassMethodTargetSelector;
import com.ibm.wala.ipa.summaries.XMLMethodSummaryReader;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.config.FileOfClasses;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.strings.Atom;

abstract public class WalaProjectCGModel implements WalaCGModel {

  protected AbstractAnalysisEngine engine;

  protected CallGraph callGraph;

  protected Collection roots;

  protected WalaProjectCGModel(IJavaProject project, final String exclusionsFile) throws IOException, CoreException{

    final EclipseProjectPath ep = JavaEclipseProjectPath.make(project, EclipseProjectPath.AnalysisScopeType.SOURCE_FOR_PROJ);
    this.engine = new JDTJavaSourceAnalysisEngine(project) {
      @Override
      public void buildAnalysisScope() {
        try {
          scope = ep.toAnalysisScope(new JavaSourceAnalysisScope());
          setExclusionsFile(exclusionsFile);
          //scope.setExclusions(FileOfClasses.createFileOfClasses(new File(getExclusionsFile())));

          if(getExclusionsFile()!=null) {
            InputStream is = WalaProjectCGModel.class.getClassLoader().getResourceAsStream(getExclusionsFile());
            scope.setExclusions(new FileOfClasses(is));
          }
        } catch (IOException e) {
          Assertions.UNREACHABLE(e.toString());
        }
      }
      @Override
      protected Iterable<Entrypoint> makeDefaultEntrypoints(AnalysisScope scope, IClassHierarchy cha) {
        return getEntrypoints(scope, cha);
      }

      //JEFF
      @Override
      protected CallGraphBuilder getCallGraphBuilder(IClassHierarchy cha,
          AnalysisOptions options, AnalysisCache cache) {

        return super.getCallGraphBuilder(cha, options, cache);
      }

      private void addCustomBypassLogic(IClassHierarchy classHierarchy, AnalysisOptions analysisOptions) throws IllegalArgumentException {
        ClassLoader classLoader = Util.class.getClassLoader();
        if (classLoader == null) {
          throw new IllegalArgumentException("classLoader is null");
        }

        Util.addDefaultSelectors(analysisOptions, classHierarchy);

        InputStream inputStream = classLoader.getResourceAsStream(Util.nativeSpec);
        XMLMethodSummaryReader methodSummaryReader = new XMLMethodSummaryReader(inputStream, scope);
        MethodTargetSelector customMethodTargetSelector = new BypassMethodTargetSelector( analysisOptions.getMethodTargetSelector(), methodSummaryReader.getSummaries(), methodSummaryReader.getIgnoredPackages(), classHierarchy);
        analysisOptions.setSelector(customMethodTargetSelector);

        ClassTargetSelector customClassTargetSelector = new BypassClassTargetSelector(analysisOptions.getClassTargetSelector(), methodSummaryReader.getAllocatableClasses(), classHierarchy,
            classHierarchy.getLoader(scope.getLoader(Atom.findOrCreateUnicodeAtom("Synthetic"))));
        analysisOptions.setSelector(customClassTargetSelector);
      }
    };
  }

  public void buildGraph() throws WalaException, CancelException {
    try {
      callGraph = engine.buildDefaultCallGraph();
      roots = inferRoots(callGraph);
    } catch (IllegalArgumentException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public CallGraph getGraph() {
    return callGraph;
  }

  public Collection getRoots() {
    return roots;
  }

  public AbstractAnalysisEngine getEngine(){
    return engine;
  }

  abstract protected Iterable<Entrypoint> getEntrypoints(AnalysisScope scope, IClassHierarchy cha);

  abstract protected Collection inferRoots(CallGraph cg) throws WalaException;

}
