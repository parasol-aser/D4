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
package com.ibm.wala.eclipse.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.internal.core.JarPackageFragmentRoot;

import com.ibm.wala.ide.util.JdtUtil;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.collections.HashMapFactory;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.graph.Graph;

public class WalaToJavaEltConverter {

  /**
   * =Jakarta Commons CLI/test<org.apache.commons.cli{BugsTest.java[BugsTest
   */
  public static Map<Integer, IJavaElement> convert(Graph graph, IJavaProject javaProject) throws JavaModelException {

    Map<Integer, IJavaElement> capaNodeToJdt = HashMapFactory.make();
    if (javaProject != null) {
      Iterator capaIt = graph.iterator();
      List<IType> jdtClasses = getJdtClasses(javaProject);

      while (capaIt.hasNext()) {
        CGNode capaMethod = (CGNode) capaIt.next();
        String capaClassName = getLongClassName(capaMethod);

        Iterator<IType> jdtClassesIt = jdtClasses.iterator();
        while (jdtClassesIt.hasNext()) {
          IType jdtClass = jdtClassesIt.next();

          String jdtClassName = getLongClassName(jdtClass);

          if (capaClassName.equals(jdtClassName)) {

            String capaMethodName = getMethodName(capaMethod);
            String[] capaParamTypes = getParamTypes(capaMethod);
            IMethod method = jdtClass.getMethod(capaMethodName, capaParamTypes);

            capaNodeToJdt.put(capaMethod.getGraphNodeId(), method);
          }
        }
      }
    }
    return capaNodeToJdt;
  }

  private static List<IType> getJdtClasses(IJavaProject javaProject) {
    List<IType> result = new ArrayList<IType>();
    Collection<ICompilationUnit> cus = getJavaCompilationUnits(javaProject);
    for (ICompilationUnit cu : cus) {
      IType[] types = JdtUtil.getClasses(cu);
      for (IType type : types) {
        result.add(type);
      }
    }
    return result;
  }

  /**
   * return the Collection of all compilation units in a java project
   */
  public static Collection<ICompilationUnit> getJavaCompilationUnits(IJavaProject javaProject) {
    List<ICompilationUnit> cuResult = new ArrayList<ICompilationUnit>();
    try {
      if (javaProject.hasChildren()) {
        IPackageFragmentRoot[] packFragRoots = javaProject.getPackageFragmentRoots();
        for (int i = 0; i < packFragRoots.length; i++) {
          IPackageFragmentRoot packFragRoot = packFragRoots[i];
          if (packFragRoot instanceof JarPackageFragmentRoot == false && packFragRoot.hasChildren()) {
            IJavaElement[] packFrags = packFragRoot.getChildren();
            for (int j = 0; j < packFrags.length; j++) {
              ICompilationUnit[] cus = ((IPackageFragment) packFrags[j]).getCompilationUnits();
              if (cus != null) {
                Collections.addAll(cuResult, cus);
              }
            }
          }
        }
      }
    } catch (JavaModelException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return cuResult;
  }

  public static String getLongClassName(CGNode capaMethod) {
    String capaTypeName = capaMethod.getMethod().getDeclaringClass().getName().toString().substring(1);
    return capaTypeName;
  }

  public static String getLongClassName(IType jdtClass) {
    String jdtTypeName = jdtClass.getFullyQualifiedName();
    return jdtTypeName.replace('.', '/');
  }

  @SuppressWarnings("unused")
  private static boolean hasSameMethodName(CGNode capaMethod, IMethod jdtMethod) {
    String capaMethodName = getMethodName(capaMethod);
    String jdtMethodName = getMethodName(jdtMethod);
    return capaMethodName.equals(jdtMethodName);
  }

  private static String getMethodName(CGNode capaMethod) {
    String capaMethodName = capaMethod.getMethod().getName().toString();
    if (capaMethodName.equals("<init>")) {
      capaMethodName = capaMethod.getMethod().getDeclaringClass().getName().toString().substring(
          capaMethod.getMethod().getDeclaringClass().getName().toString().lastIndexOf('/') + 1,
          capaMethod.getMethod().getDeclaringClass().getName().toString().length());
    }
    return capaMethodName;
  }

  private static String getMethodName(IMethod jdtMethod) {
    return jdtMethod.getElementName();
  }

  private static String typeName(TypeReference capaParamType) {
    if (capaParamType.isArrayType()) {
      // get the array element
      return typeName(capaParamType.getArrayElementType()) + "[]";

    } else if (capaParamType.isPrimitiveType()) {
      String capaType = capaParamType.getName().toString();
      if (capaType.equals("C"))
        return "char";
      else if (capaType.equals("Z"))
        return "boolean";
      else if (capaType.equals("I"))
        return "int";
      else if (capaType.equals("B"))
        return "byte";
      else if (capaType.equals("D"))
        return "double";
      else if (capaType.equals("F"))
        return "float";
      else if (capaType.equals("J"))
        return "long";
      else if (capaType.equals("S"))
        return "short";
      else {
        Assertions.UNREACHABLE();
        return null;
      }
    } else if (capaParamType.isClassType()) {
      return capaParamType.getName().toString().substring(capaParamType.getName().getPackage().length() + 2,
          capaParamType.getName().toString().length());

    } else {
      Assertions.UNREACHABLE();
      return null;
    }
  }

  private static String[] getParamTypes(CGNode capaMethod) {

    List<TypeReference> capaParams = getParams(capaMethod);
    Iterator<TypeReference> capaParamsIt = capaParams.iterator();
    String[] result = new String[capaParams.size()];

    // check the type of each param
    for (int i = 0; capaParamsIt.hasNext(); i++) {
      result[i] = Signature.createTypeSignature(typeName(capaParamsIt.next()), false);
    }
    return result;
  }

  private static List<TypeReference> getParams(CGNode node) {
    int nbrParamsIncludingThisRef = node.getMethod().getNumberOfParameters();
    List<TypeReference> result = new ArrayList<TypeReference>(nbrParamsIncludingThisRef);
    int startParamNbr = 0;
    if (node.getMethod().isStatic() || node.getMethod().isClinit()) {
      startParamNbr = 0;
    } else {
      startParamNbr = 1;
    }

    for (int i = startParamNbr; i < nbrParamsIncludingThisRef; i++) {
      result.add(node.getMethod().getParameterType(i));
    }
    return result;
  }

  @SuppressWarnings("unused")
  private static String[] getSimpleParamTypes(IMethod jdtMethod) {
    String[] paramTypes = jdtMethod.getParameterTypes();
    String[] humanReadableTypes = new String[paramTypes.length];
    for (String paramType : paramTypes) {
      JdtUtil.getHumanReadableType(paramType);
    }
    return humanReadableTypes;
  }
}
