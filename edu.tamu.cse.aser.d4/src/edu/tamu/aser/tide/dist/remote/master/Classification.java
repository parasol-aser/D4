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
package edu.tamu.aser.tide.dist.remote.master;

import java.util.Random;

import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import edu.tamu.aser.tide.tests.ReproduceBenchmarks;

public class Classification extends UntypedActor{

	LoggingAdapter log = Logging.getLogger(getContext().system(), this);

	public static final String BACKEND_REGISTRATION = "BackendRegistration";
	public static ActorRef backend;

	//counter
	private static boolean finished = false;

	@Override
	public void onReceive(Object message) throws Throwable {
		if (message.equals(BACKEND_REGISTRATION)) {
			System.out.println("**********Register the backend workers to frontend**********");
			getContext().watch(getSender());
			backend = getSender();
		}else if(message instanceof String){
			String job = (String) message;
			backend.forward(job, getContext());
		}else if(message instanceof Boolean){
			boolean status = (boolean) message;
			if(status){
				finished = true;
			}else{
				//reach time limit
				finished = true;
				ReproduceBenchmarks.terminateEva();
			}
		}else{
			unhandled(message);
		}
	}

	public static boolean askstatus(){
		if(finished){
			finished = false;
			return false;
		}else{
			return true;
		}
	}



}