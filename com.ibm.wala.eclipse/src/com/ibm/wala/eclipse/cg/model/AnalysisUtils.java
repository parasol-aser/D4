
package com.ibm.wala.eclipse.cg.model;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAMonitorInstruction;
import com.ibm.wala.types.MemberReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.strings.Atom;

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


	public static boolean isMonitorEnter(SSAInstruction ssaInstruction) {
		return ssaInstruction instanceof SSAMonitorInstruction && ((SSAMonitorInstruction) ssaInstruction).isMonitorEnter();
	}

	public static boolean isMonitorExit(SSAInstruction ssaInstruction) {
		return ssaInstruction instanceof SSAMonitorInstruction && !((SSAMonitorInstruction) ssaInstruction).isMonitorEnter();
	}



}
