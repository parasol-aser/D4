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
import java.util.HashSet;
import java.util.Iterator;

import com.ibm.wala.fixpoint.AbstractStatement;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ipa.callgraph.propagation.PointsToSetVariable;

public class CollectResetResults {
  private static HashSet<ResetResult> results;
  static CollectResetResults cResults;

  private CollectResetResults(HashSet<ResetResult> results){
    this.results = results;
  }

  public static CollectResetResults getInstance(){
    if(results == null){
      cResults = new CollectResetResults(new HashSet<ResetResult>());
    }
    return cResults;
  }

  public void addResetResult(ResetResult r){
    results.add(r);
  }

  public int getSize(){
    return results.size();
  }

  public void clear() {
    results.clear();
  }


}
