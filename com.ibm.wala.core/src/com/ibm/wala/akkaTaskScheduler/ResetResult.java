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
package com.ibm.wala.akkaTaskScheduler;

import java.util.ArrayList;

import com.ibm.wala.ipa.callgraph.propagation.PointsToSetVariable;
import com.ibm.wala.util.intset.MutableSharedBitVectorIntSet;
import com.ibm.wala.util.intset.MutableSparseIntSet;

public class ResetResult {
  private PointsToSetVariable user;
  private ArrayList<PointsToSetVariable> next;
  private MutableSparseIntSet localtargets;

  public ResetResult(PointsToSetVariable user, ArrayList<PointsToSetVariable> next) {
    this.user = user;
    this.next = next;
//    this.localtargets = localtargets;
  }

  public ResetResult(PointsToSetVariable user, ArrayList<PointsToSetVariable> next, MutableSparseIntSet localtargets2) {
    this.user = user;
    this.next = next;
    this.localtargets = localtargets2;
  }

  public PointsToSetVariable getUser(){
    return user;
  }

  public ArrayList<PointsToSetVariable> getCheckNext(){
    return next;
  }

  public MutableSparseIntSet getTargets(){
    return localtargets;
  }
}
