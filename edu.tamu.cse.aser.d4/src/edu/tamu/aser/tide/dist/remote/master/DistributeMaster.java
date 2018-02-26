package edu.tamu.aser.tide.dist.remote.master;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;


public class DistributeMaster {

  public static ActorRef frontend = null;

  public DistributeMaster() {
  }

//  public static void main(String arg[]) throws InterruptedException {
//    DistributeMaster testMaster = new DistributeMaster();
//    testMaster.startClusterSystem(arg);
//  }

  public void startClusterSystem(String benchmark) {
	  if(frontend == null){
		  startFrontEnd(benchmark);//master
		  try {
			  Thread.sleep(5000);
		  } catch (InterruptedException e) {
			  e.printStackTrace();
		  }
	  }else{
		  System.err.println("Already started the backend in the cluster");
		  frontend.tell("BENCHMARK:" + benchmark, frontend);
	  }
  }

  private void startFrontEnd(String benchmark) {
    final Config config = ConfigFactory.parseString(
        "akka.cluster.roles = [frontend]").withFallback(
        ConfigFactory.load("master"));//"master.conf"

    final ActorSystem system = ActorSystem.create("ClusterSystem", config);
    system.log().info("Frontend will connect the backend in the cluster.");

    frontend = system.actorOf(Props.create(Classification.class), "frontend");

    try {
      Thread.sleep(200);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    while(Classification.backend == null){
      System.err.println("wait for backend registration.");
      try {
        Thread.sleep(2000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }

    System.err.println("Started the backend in the cluster");
    frontend.tell("BENCHMARK:" + benchmark, frontend);
  }

  public void awaitRemoteComplete() {
	  boolean goon = true;
	  while(goon){
		  try {
			  Thread.sleep(10);
		  } catch (InterruptedException e) {
			  e.printStackTrace();
		  }
		  goon = Classification.askstatus();
	  }
  }



}
