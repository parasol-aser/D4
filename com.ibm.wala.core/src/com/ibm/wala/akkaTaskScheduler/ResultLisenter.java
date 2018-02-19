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

import com.ibm.wala.fixedpoint.impl.AbstractFixedPointSolver;

import akka.actor.UntypedActor;

public class ResultLisenter extends UntypedActor{

  @Override
  public void onReceive(Object message){
    if(message instanceof CollectResults){
      CollectResults results = (CollectResults) message;
//      System.out.println("Collected Results: " );
//      for(int i=0; i<results.getSize(); i++){
//        System.out.println("\t " + results.get(i).toString());
//      }
      getContext().system().shutdown();
    }else if(message instanceof CollectResetResults){
      CollectResetResults results = (CollectResetResults) message;
//      System.out.println("SIZE: " + results.getSize());
//      AbstractFixedPointSolver.addToWorklist(results.getAllWorklist());
      results.clear();
      getContext().system().shutdown();
    }else if(message instanceof String){
      getContext().system().shutdown();
    }else{
      unhandled(message);
    }
  }
}
