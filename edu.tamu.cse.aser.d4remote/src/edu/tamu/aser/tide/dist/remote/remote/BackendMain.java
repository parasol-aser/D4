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
package edu.tamu.aser.tide.dist.remote.remote;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;
import akka.actor.Props;

public class BackendMain {

	public static void main(String[] args) {
		String port = args.length > 0 ? args[0] : "0";
		port = "65501";
		final Config config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port).
				withFallback(ConfigFactory.parseString("akka.cluster.roles = [backend]")).
				withFallback(ConfigFactory.load("worker"));

		ActorSystem system = ActorSystem.create("ClusterSystem", config);
		system.actorOf(Props.create(DistributeReceiver.class), "myBackend");
	}

}
