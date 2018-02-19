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

public class NextCheck {
  ArrayList<PointsToSetVariable> next;

  public NextCheck(ArrayList<PointsToSetVariable> next) {
    this.next = next;
  }

  public ArrayList<PointsToSetVariable> getNext(){
    return next;
  }


}
