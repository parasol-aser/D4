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

public class MultiRhs {
  private PointsToSetVariable lhs;
  private PointsToSetVariable rhs;
  private boolean isAddition;

  public MultiRhs(PointsToSetVariable lhs, PointsToSetVariable ptv, boolean isAddition) {
    // TODO Auto-generated constructor stub
    this.rhs = ptv;
    this.lhs = lhs;
    this.isAddition = isAddition;
  }

  public PointsToSetVariable getRhs(){
    return rhs;
  }

  public PointsToSetVariable getLhs(){
    return lhs;
  }

  public boolean getIsAddition(){
    return isAddition;
  }

}
