/**
 * This file is licensed under the University of Illinois/NCSA Open Source License. See LICENSE.TXT for details.
 */
package edu.tamu.aser.tide.engine;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAMonitorInstruction;
import com.ibm.wala.types.MemberReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.strings.Atom;

/**
 *
 * @author Mohsen Vakilian
 * @author Stas Negara
 *
 */
public class AnalysisUtils {

	public static final String APPLICATION_CLASSLOADER_NAME = "Application"; //$NON-NLS-1$
	public static final String SOURCE_CLASSLOADER_NAME = "Source"; //$NON-NLS-1$

	public static final String EXTENSION_CLASSLOADER_NAME = "Extension"; //$NON-NLS-1$

	public static final String PRIMORDIAL_CLASSLOADER_NAME = "Primordial"; //$NON-NLS-1$

	private static final String OBJECT_GETCLASS_SIGNATURE = "java.lang.Object.getClass()Ljava/lang/Class;"; //$NON-NLS-1$

	/**
	 * The value number of "this" is meaningful only for instance methods.
	 */
	public static final int THIS_VALUE_NUMBER = 1;

	public static IPath getWorkspaceLocation() {
		return ResourcesPlugin.getWorkspace().getRoot().getLocation();
	}

//	public static boolean isProtectedByAnySynchronizedBlock(Collection<InstructionInfo> safeSynchronizedBlocks, InstructionInfo instruction) {
//		for (InstructionInfo safeSynchronizedBlock : safeSynchronizedBlocks) {
//			if (instruction.isInside(safeSynchronizedBlock)) {
//				return true;
//			}
//		}
//		return false;
//	}
//
//	public static void collect(IJavaProject javaProject, Collection<InstructionInfo> instructionInfos, CGNode cgNode, InstructionFilter instructionFilter) {
//		if (instructionInfos == null) {
//			throw new RuntimeException("Expected a valid collection to store the results in.");
//		}
//
//		IR ir = cgNode.getIR();
//		if (ir == null) {
//			return;
//		}
//		SSAInstruction[] instructions = ir.getInstructions();
//		for (int instructionIndex = 0; instructionIndex < instructions.length; instructionIndex++) {
//			SSAInstruction instruction = instructions[instructionIndex];
//			InstructionInfo instructionInfo = new InstructionInfo(javaProject, cgNode, instructionIndex);
//			if (instruction != null && (instructionFilter == null || instructionFilter.accept(instructionInfo))) {
//				instructionInfos.add(instructionInfo);
//			}
//		}
//	}

	/**
	 * Remove the code duplication in
	 *
	 * {@link #collect(IJavaProject, Collection, CGNode, InstructionFilter)}
	 *
	 * and
	 *
	 * {@link #contains(IJavaProject, CGNode, InstructionFilter)}.
	 */
//	public static boolean contains(IJavaProject javaProject, CGNode cgNode, InstructionFilter instructionFilter) {
//		IR ir = cgNode.getIR();
//		if (ir == null) {
//			return false;
//		}
//		SSAInstruction[] instructions = ir.getInstructions();
//		for (int instructionIndex = 0; instructionIndex < instructions.length; instructionIndex++) {
//			SSAInstruction instruction = instructions[instructionIndex];
//			InstructionInfo instructionInfo = new InstructionInfo(javaProject, cgNode, instructionIndex);
//			if (instruction != null && (instructionFilter == null || instructionFilter.accept(instructionInfo))) {
//				return true;
//			}
//		}
//		return false;
//	}

	/**
	 * Findbugs needs the name of the class that contains the bug. The class
	 * name that WALA returns includes some additional information such as the
	 * method name in case of anonymous classes. But, Findbugs expects names
	 * that follow the standard Java bytecode convention. This method takes a
	 * class name as reported by WALA and returns the name of the innermost
	 * enclosing non-anonymous class of it. See issue #5 for more details.
	 *
	 * @param typeName
	 * @return
	 */
	public static String getEnclosingNonanonymousClassName(TypeName typeName) {
		String packageName = typeName.getPackage().toString().replaceAll("/", ".");
		int indexOfOpenParen = packageName.indexOf('(');
		if (indexOfOpenParen != -1) {
			int indexOfLastPackageSeparator = packageName.lastIndexOf('.', indexOfOpenParen);
			return packageName.substring(0, indexOfLastPackageSeparator);
		}
		String className = typeName.getClassName().toString();
		int indexOfDollarSign = className.indexOf('$');
		if (indexOfDollarSign != -1) {
			className = className.substring(0, indexOfDollarSign);
		}
		return packageName + "." + className;
	}

	private static boolean isExtension(Atom classLoaderName) {
		return classLoaderName.toString().equals(EXTENSION_CLASSLOADER_NAME);
	}

	private static boolean isPrimordial(Atom classLoaderName) {
		return classLoaderName.toString().equals(PRIMORDIAL_CLASSLOADER_NAME);
	}

	private static boolean isApplication(Atom classLoaderName) {
		return classLoaderName.toString().equals(APPLICATION_CLASSLOADER_NAME)
				||classLoaderName.toString().equals(SOURCE_CLASSLOADER_NAME);
	}

	public static boolean isApplicationClass(IClass klass) {
		return isApplication(klass.getClassLoader().getName());
	}

	public static boolean isLibraryClass(IClass klass) {
		return isExtension(klass.getClassLoader().getName());
	}

	public static boolean isLibraryClass(TypeReference typeReference) {
		return isExtension(typeReference.getClassLoader().getName());
	}

	public static boolean isJDKClass(IClass klass) {
		return isPrimordial(klass.getClassLoader().getName());
	}

	public static boolean isJDKClass(TypeReference typeReference) {
		return isPrimordial(typeReference.getClassLoader().getName());
	}

	public static boolean isObjectGetClass(MemberReference memberReference) {
		return isObjectGetClass(memberReference.getSignature());
	}

	public static boolean isObjectGetClass(IMethod method) {
		return isObjectGetClass(method.getSignature());
	}

	private static boolean isObjectGetClass(String methodSignature) {
		return methodSignature.equals(OBJECT_GETCLASS_SIGNATURE);
	}

	public static boolean extendsThreadClass(IClass klass) {
		IClass superclass = klass.getSuperclass();
		if (superclass == null) {
			return false;
		}
		if (isThreadClass(superclass)) {
			return true;
		}
		return extendsThreadClass(superclass);
	}

	public static boolean implementsRunnableInterface(IClass klass) {
		for (IClass implementedInterface : klass.getAllImplementedInterfaces()) {
			if (isRunnableInterface(implementedInterface)) {
				return true;
			}
		}
		return false;
	}

	public static boolean isRunnableInterface(IClass interfaceClass) {
		return AnalysisUtils.getEnclosingNonanonymousClassName(interfaceClass.getName()).equals("java.lang.Runnable");
	}

	public static boolean isThreadClass(IClass klass) {
		return AnalysisUtils.getEnclosingNonanonymousClassName(klass.getName()).equals("java.lang.Thread");
	}


	public static String walaTypeNameToJavaName(TypeName typeName) {
		String fullyQualifiedName = typeName.getPackage() + "." + typeName.getClassName();

		//WALA uses $ to refers to inner classes. We have to replace "$" by "." to make it a valid class name in Java source code.
		return fullyQualifiedName.replace("$", ".").replace("/", ".");
	}

//	public static CodePosition getPosition(IJavaProject javaProject, IMethod method, int instructionIndex) {
//		String enclosingClassName = getEnclosingNonanonymousClassName(method.getDeclaringClass().getName());
//		if (method instanceof ShrikeCTMethod) {
//			ShrikeCTMethod shrikeMethod = (ShrikeCTMethod) method;
//			try {
//				IType enclosingType = javaProject.findType(enclosingClassName, new NullProgressMonitor());
//
//				//FIXME: The following check is a workaround for issue #41.
//				if (enclosingType == null || enclosingType.getCompilationUnit() == null) {
//					String message = "Position not found. Could not find the type corresponding to %s in the Java project.";
//					System.err.println(String.format(message, enclosingClassName));
//					throw new RuntimeException(message);
//				}
//
//				IPath fullPath = getWorkspaceLocation().append(enclosingType.getCompilationUnit().getPath());
//				int lineNumber = shrikeMethod.getLineNumber(shrikeMethod.getBytecodeIndex(instructionIndex));
//				return new CodePosition(lineNumber, lineNumber, fullPath, enclosingClassName);
//			} catch (JavaModelException e) {
//				throw new RuntimeException(e);
//			} catch (InvalidClassFileException e) {
//				throw new RuntimeException(e);
//			}
//		}
//		throw new RuntimeException("Unexpected method class: " + method.getClass());
//	}

	public static boolean isMonitorEnter(SSAInstruction ssaInstruction) {
		return ssaInstruction instanceof SSAMonitorInstruction && ((SSAMonitorInstruction) ssaInstruction).isMonitorEnter();
	}

	public static boolean isMonitorExit(SSAInstruction ssaInstruction) {
		return ssaInstruction instanceof SSAMonitorInstruction && !((SSAMonitorInstruction) ssaInstruction).isMonitorEnter();
	}

//	public static boolean doesAllowPropagation(InstructionInfo instructionInfo, IClassHierarchy classHierarchy) {
//		if (!(instructionInfo.getInstruction() instanceof SSAInvokeInstruction)) {
//			throw new RuntimeException("Expected an SSAInvokeInstruction.");
//		}
//		SSAInvokeInstruction invokeInstruction = (SSAInvokeInstruction) instructionInfo.getInstruction();
//		for (int argumentIndex = 0; argumentIndex < invokeInstruction.getNumberOfUses(); ++argumentIndex) {
//			int argumentValueNumber = invokeInstruction.getUse(argumentIndex);
//			if (doesAllowPropagation(argumentValueNumber, instructionInfo.getCGNode(), classHierarchy)) {
//				return true;
//			}
//		}
//		return false;
//	}

	/**
	 *
	 * @param valueNumber
	 * @param cgNode
	 * @return true if the given value number is one of the parameters of the
	 *         method corresponding to the given CGNode. Note that the receiver
	 *         (if the method is not static) is the first parameter of the
	 *         method.
	 */
	private static boolean isParameterOf(int valueNumber, CGNode cgNode) {
		int numberOfParametersOfCaller = cgNode.getMethod().getNumberOfParameters();
		return valueNumber <= numberOfParametersOfCaller;
	}

	private static boolean isDefUseReachableFromParameterOrStaticField(int valueNumber, CGNode enclosingCGNode, IClassHierarchy classHierarchy) {
		if (isParameterOf(valueNumber, enclosingCGNode)) {
			return false;
		}
		DefUse defUse = enclosingCGNode.getDU();
		SSAInstruction instructionDefiningTheValue = defUse.getDef(valueNumber);
		if (instructionDefiningTheValue instanceof SSAGetInstruction) {
			SSAGetInstruction getInstruction = (SSAGetInstruction) instructionDefiningTheValue;
			if (getInstruction.isStatic()) {
				return false; // indirectly accesses a static field
			}
			return isDefUseReachableFromParameterOrStaticField(getInstruction.getRef(), enclosingCGNode, classHierarchy);
		}
		return true;
	}

	private static boolean doesAllowPropagation(int valueNumber, CGNode enclosingCGNode, IClassHierarchy classHierarchy) {
		return !isDefUseReachableFromParameterOrStaticField(valueNumber, enclosingCGNode, classHierarchy);
	}

//	public static IField getAccessedField(BasicAnalysisData basicAnalysisData, SSAFieldAccessInstruction fieldAccessInstruction) {
//		return basicAnalysisData.classHierarchy.resolveField(fieldAccessInstruction.getDeclaredField());
//	}

}
