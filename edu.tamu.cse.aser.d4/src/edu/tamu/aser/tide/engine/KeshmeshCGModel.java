/**
 * This file is licensed under the University of Illinois/NCSA Open Source License. See LICENSE.TXT for details.
 */
package edu.tamu.aser.tide.engine;

import java.io.IOException;
import java.util.Collection;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaProject;

import com.ibm.wala.eclipse.cg.model.WalaProjectCGModel;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.graph.InferGraphRoots;

public class KeshmeshCGModel extends WalaProjectCGModel {

	public AnalysisCache getCache()
	{
		return engine.getCache();
	}
	public AnalysisOptions getOptions()
	{
		return engine.getOptions();
	}
	public KeshmeshCGModel(IJavaProject project, String exclusionsFile, int objectSensitivityLevel, String mainMethodSignature) throws IOException, CoreException {
		super(project, exclusionsFile);
		engine = new EclipseProjectAnalysisEngine(project, objectSensitivityLevel, mainMethodSignature);
		engine.setExclusionsFile(exclusionsFile);
	}

	@Override
	protected Iterable<Entrypoint> getEntrypoints(AnalysisScope analysisScope, IClassHierarchy classHierarchy) {
		throw new UnsupportedOperationException();
	}

	@Override
	protected Collection<CGNode> inferRoots(CallGraph cg) throws WalaException {
		return InferGraphRoots.inferRoots(cg);
	}

	public PointerAnalysis getPointerAnalysis() {
		return engine.getPointerAnalysis();
	}

	public IClassHierarchy getClassHierarchy() {
		return engine.getClassHierarchy();
	}

	public Iterable<Entrypoint> getEntryPoints() {
		return ((EclipseProjectAnalysisEngine) engine).getEntryPoints();
	}

}
