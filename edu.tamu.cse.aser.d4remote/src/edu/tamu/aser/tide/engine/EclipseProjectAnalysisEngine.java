/**
 * This file is licensed under the University of Illinois/NCSA Open Source License. See LICENSE.TXT for details.
 */
package edu.tamu.aser.tide.engine;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaProject;

import com.ibm.wala.cast.java.client.JDTJavaSourceAnalysisEngine;
import com.ibm.wala.cast.java.ipa.callgraph.JavaSourceAnalysisScope;
import com.ibm.wala.client.AbstractAnalysisEngine;
import com.ibm.wala.ide.util.EclipseProjectPath;
import com.ibm.wala.ide.util.EclipseProjectPath.AnalysisScopeType;
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
import com.ibm.wala.ipa.summaries.MethodSummary;
import com.ibm.wala.ipa.summaries.XMLMethodSummaryReader;
import com.ibm.wala.types.MemberReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.config.FileOfClasses;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.io.FileProvider;
import com.ibm.wala.util.strings.Atom;

/**
 *
 * @author Mohsen Vakilian
 * @author Stas Negara
 *
 */
public class EclipseProjectAnalysisEngine extends AbstractAnalysisEngine {

	private final IJavaProject javaProject;

	private final int objectSensitivityLevel;

	private Iterable<Entrypoint> entryPoints;

	private String entrySignature;

	public EclipseProjectAnalysisEngine(IJavaProject javaProject, int objectSensitivityLevel, String mainMethodSignature) {
		this.javaProject = javaProject;
		this.objectSensitivityLevel = objectSensitivityLevel;
		this.entrySignature = mainMethodSignature;
	}

	public CallGraph getCallGraph()
	{
		return cg;
	}

	@Override
	public void buildAnalysisScope() throws IOException {
		try {
			EclipseProjectPath eclipseProjectPath = JavaEclipseProjectPath.make(javaProject, AnalysisScopeType.NO_SOURCE);
			//scope = eclipseProjectPath.toAnalysisScope(new File(getExclusionsFile()));
	          scope = eclipseProjectPath.toAnalysisScope(new JavaSourceAnalysisScope());
	          InputStream is = EclipseProjectAnalysisEngine.class.getClassLoader().getResourceAsStream(getExclusionsFile());
	            scope.setExclusions(new FileOfClasses(is));

	            //		    EclipseProjectPath ep = JavaEclipseProjectPath.make(javaProject, EclipseProjectPath.AnalysisScopeType.SOURCE_FOR_PROJ);
//
//		          scope = ep.toAnalysisScope(new JavaSourceAnalysisScope());
//
//		          //scope.setExclusions(FileOfClasses.createFileOfClasses(new File(getExclusionsFile())));
//		          if(getExclusionsFile()!=null)
//		          {
//		            InputStream is = EclipseProjectAnalysisEngine.class.getClassLoader().getResourceAsStream(getExclusionsFile());
//		            scope.setExclusions(new FileOfClasses(is));
//		          }
		} catch (CoreException ex) {
			throw new RuntimeException(ex);
		}
	}

	/**
	 *
	 * See com.ibm.wala.ipa.callgraph.impl.Util.addBypassLogic(AnalysisOptions,
	 * AnalysisScope, ClassLoader, String, IClassHierarchy)
	 *
	 * @param classHierarchy
	 * @param analysisOptions
	 * @throws IllegalArgumentException
	 */
	private void addCustomBypassLogic(IClassHierarchy classHierarchy, AnalysisOptions analysisOptions) throws IllegalArgumentException {
		ClassLoader classLoader = Util.class.getClassLoader();
		if (classLoader == null) {
			throw new IllegalArgumentException("classLoader is null");
		}

		Util.addDefaultSelectors(analysisOptions, classHierarchy);

		InputStream inputStream = classLoader.getResourceAsStream(Util.nativeSpec);
		XMLMethodSummaryReader methodSummaryReader = new XMLMethodSummaryReader(inputStream, scope);

		MethodTargetSelector customMethodTargetSelector = getCustomBypassMethodTargetSelector(classHierarchy, analysisOptions, methodSummaryReader);
		analysisOptions.setSelector(customMethodTargetSelector);

		ClassTargetSelector customClassTargetSelector = new BypassClassTargetSelector(analysisOptions.getClassTargetSelector(), methodSummaryReader.getAllocatableClasses(), classHierarchy,
				classHierarchy.getLoader(scope.getLoader(Atom.findOrCreateUnicodeAtom("Synthetic"))));
		analysisOptions.setSelector(customClassTargetSelector);
	}

	private BypassMethodTargetSelector getCustomBypassMethodTargetSelector(IClassHierarchy classHierarchy, AnalysisOptions analysisOptions, XMLMethodSummaryReader summary) {
		return new KeshmeshBypassMethodTargetSelector(analysisOptions.getMethodTargetSelector(), summary.getSummaries(), summary.getIgnoredPackages(), classHierarchy);
	}

	@Override
	protected CallGraphBuilder getCallGraphBuilder(IClassHierarchy classHierarchy, AnalysisOptions analysisOptions, AnalysisCache analysisCache) {
		addCustomBypassLogic(classHierarchy, analysisOptions);
		return KeshmeshAnalysisEngine.getCallGraphBuilder(scope, classHierarchy, analysisOptions, analysisCache, objectSensitivityLevel);
	}

	@Override
	protected Iterable<Entrypoint> makeDefaultEntrypoints(AnalysisScope analysisScope, IClassHierarchy classHierarchy) {
		Set<Entrypoint> entryPointsSet = KeshmeshAnalysisEngine.findEntryPoints(classHierarchy,entrySignature);
		entryPoints = KeshmeshAnalysisEngine.toIterable(entryPointsSet);
		return entryPoints;
	}

	public Iterable<Entrypoint> getEntryPoints() {
		if (entryPoints == null) {
			throw new RuntimeException("getEntryPoints() should be called after makeDefaultEntrypoints().");
		}
		return entryPoints;
	}

}

class KeshmeshBypassMethodTargetSelector extends BypassMethodTargetSelector {

	public KeshmeshBypassMethodTargetSelector(MethodTargetSelector parent, Map<MethodReference, MethodSummary> methodSummaries, Set<Atom> ignoredPackages, IClassHierarchy cha) {
		super(parent, methodSummaries, ignoredPackages, cha);
	}

	@Override
	protected boolean canIgnore(MemberReference m) {
		//FIXME: LCK01BugDetector depends on some JDK classes.
		//		if (AnalysisUtils.isLibraryClass(m.getDeclaringClass()) || (AnalysisUtils.isJDKClass(m.getDeclaringClass()) && !AnalysisUtils.isObjectGetClass(m))) {
		if (AnalysisUtils.isLibraryClass(m.getDeclaringClass())) {
			return true;
		} else {
			return super.canIgnore(m);
		}
	}
}
