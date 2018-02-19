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

import com.ibm.wala.ipa.callgraph.propagation.PointsToSetVariable;
import com.ibm.wala.ipa.callgraph.propagation.PropagationSystem;
import com.ibm.wala.util.intset.MutableIntSet;

public class WorkContentForSpecial {
  private PointsToSetVariable first;
  private MutableIntSet targets;
  private boolean isAddition ;
//  private boolean isFirst ;
  private PropagationSystem system;

  public WorkContentForSpecial(PointsToSetVariable first, MutableIntSet targets, boolean isAddition, PropagationSystem system) {
    //, boolean isFirst
    this.first = first;
    this.targets = targets;
    this.isAddition = isAddition;
//    this.isFirst = isFirst;
    this.system = system;
  }

  public PropagationSystem getPropagationSystem (){
    return system;
  }

  public MutableIntSet getTargets(){
    return targets;
  }

  public PointsToSetVariable getUser(){
    return first;
  }

  public boolean getIsAdd(){
    return isAddition;
  }

//  public boolean getIsFirst(){
//    return isFirst;
//  }
}
