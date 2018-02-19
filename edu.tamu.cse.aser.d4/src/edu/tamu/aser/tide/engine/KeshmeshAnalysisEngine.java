package edu.tamu.aser.tide.engine;

import java.util.Iterator;
import java.util.Set;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.ShrikeCTMethod;
import com.ibm.wala.eclipse.cg.model.KObjectSensitiveContextSelector;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.ContextSelector;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.propagation.SSAContextInterpreter;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.callgraph.propagation.cfa.ZeroXCFABuilder;
import com.ibm.wala.ipa.callgraph.propagation.cfa.ZeroXInstanceKeys;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.util.collections.HashSetFactory;


/**
 * 
 * @author Mohsen Vakilian
 * @author Stas Negara
 * 
 */
public class KeshmeshAnalysisEngine {

	public static Iterable<Entrypoint> makeDefaultEntrypoints(ClassLoaderReference classLoaderReference, IClassHierarchy classHierarchy) {
		//Iterable<Entrypoint> mainEntrypoints = Util.makeMainEntrypoints(analysisScope.getApplicationLoader(), classHierarchy);
		//		return new FilteredIterable(mainEntrypoints);
		//		return Util.makeMainEntrypoints(classLoaderReference, classHierarchy);
		return null;//toIterable(findEntryPoints(classHierarchy));
	}

	public static CallGraphBuilder getCallGraphBuilder(AnalysisScope analysisScope, IClassHierarchy classHierarchy, AnalysisOptions analysisOptions, AnalysisCache analysisCache,
			int objectSensitivityLevel) {
		ContextSelector contextSelector = new KObjectSensitiveContextSelector(objectSensitivityLevel);
		//		Util.addDefaultSelectors(analysisOptions, classHierarchy);
		//		Util.addDefaultBypassLogic(analysisOptions, analysisScope, Util.class.getClassLoader(), classHierarchy);
		//		return new KeshmeshCFABuilder(classHierarchy, analysisOptions, analysisCache, contextSelector, null);
		return makeZeroOneCFABuilder(analysisOptions, analysisCache, classHierarchy, analysisScope, contextSelector, null);
	}

	public static SSAPropagationCallGraphBuilder makeZeroOneCFABuilder(AnalysisOptions options, AnalysisCache cache, IClassHierarchy cha, AnalysisScope scope, ContextSelector customSelector,
			SSAContextInterpreter customInterpreter) {
		if (options == null) {
			throw new IllegalArgumentException("options is null");
		}
		//		Util.addDefaultSelectors(options, cha);
		//		Util.addDefaultBypassLogic(options, scope, Util.class.getClassLoader(), cha);

		return ZeroXCFABuilder.make(cha, options, cache, customSelector, customInterpreter, ZeroXInstanceKeys.ALLOCATIONS | ZeroXInstanceKeys.SMUSH_MANY | ZeroXInstanceKeys.SMUSH_THROWABLES);
	}

	public static Iterable<Entrypoint> toIterable(final Set<Entrypoint> entryPoints) {
		return new Iterable<Entrypoint>() {
			public Iterator<Entrypoint> iterator() {
				return entryPoints.iterator();
			}
		};
	}

	public static Set<Entrypoint> findEntryPoints(IClassHierarchy classHierarchy, String entrySignature) {
		final Set<Entrypoint> result = HashSetFactory.make();
		Iterator<IClass> classIterator = classHierarchy.iterator();
		while (classIterator.hasNext()) {
			IClass klass = classIterator.next();
			if (!AnalysisUtils.isJDKClass(klass)) {
				// Logger.log("Visiting class " + klass);
				//String classname = klass.getName().getClassName().toString();
				//String classpackage = klass.getName().getPackage().toString();

				for (IMethod method : klass.getDeclaredMethods()) {
					try {
						if (!(method instanceof ShrikeCTMethod)) {
							throw new RuntimeException("@EntryPoint only works for byte code.");
						}
						//String methodname = method.getName().toString();
						String methodSig = method.getSignature();
						if(methodSig.equals(entrySignature))
						{
//						String signature = classpackage+"."+classname;
//						if(signature.equals(entrySignature))
						result.add(new DefaultEntrypoint(method, classHierarchy));
						//return result;
						// Logger.log("Visiting method " + method);
//						for (Annotation annotation : ((ShrikeCTMethod) method).getAnnotations(true)) 
//						{
//							//	Logger.log("Visiting annotation " + annotation);
//							if (isEntryPointClass(annotation.getType().getName())) {
//								result.add(new DefaultEntrypoint(method, classHierarchy));
//								break;
//							}
//						}
						}
						else if(methodSig.contains("run()V"))
						{
							if (AnalysisUtils.implementsRunnableInterface(klass) || AnalysisUtils.extendsThreadClass(klass)) 
							result.add(new DefaultEntrypoint(method, classHierarchy));

						}
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				}
			}
		}
		return result;
	}

//	private static boolean isEntryPointClass(TypeName typeName) {
//		return (AnalysisUtils.walaTypeNameToJavaName(typeName).equals(EntryPoint.class.getName()));
//	}

}
