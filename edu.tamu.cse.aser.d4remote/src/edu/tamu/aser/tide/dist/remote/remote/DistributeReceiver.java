package edu.tamu.aser.tide.dist.remote.remote;

import akka.actor.UntypedActor;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent;
import akka.cluster.ClusterEvent.CurrentClusterState;
import akka.cluster.ClusterEvent.MemberEvent;
import akka.cluster.ClusterEvent.MemberUp;
import akka.cluster.ClusterEvent.UnreachableMember;
import akka.cluster.Member;
import akka.cluster.MemberStatus;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import edu.tamu.aser.tide.tests.ReproduceBenchmark_remote;

public class DistributeReceiver extends UntypedActor{

	LoggingAdapter log = Logging.getLogger(getContext().system(), this);
	Cluster cluster = Cluster.get(getContext().system());
	public static final String BACKEND_REGISTRATION = "BackendRegistration";

	//  subscribe to cluster changes
	@Override
	public void preStart() {
		cluster.subscribe(getSelf(), ClusterEvent.initialStateAsEvents(),
				MemberEvent.class, UnreachableMember.class);
	}

	//re-subscribe when restart
	@Override
	public void postStop() {
		cluster.unsubscribe(getSelf());
	}

	@Override
	public void onReceive(Object message) throws Exception {
		// TODO Auto-generated method stub
//		log.info("backend received message: " + message.toString());
		if (message instanceof CurrentClusterState) {
			CurrentClusterState state = (CurrentClusterState) message;
			for (Member member : state.getMembers()) {
				if (member.status().equals(MemberStatus.up())) {
					register(member);
				}
			}
		} else if (message instanceof MemberUp) {
			MemberUp mUp = (MemberUp) message;
			register(mUp.member());
		}else if(message instanceof String){
			String job = (String) message;
			if(job.contains("-STMT:")){
				ReproduceBenchmark_remote.delete(job.substring(job.indexOf(":") + 1));
				getSender().tell(true, getSelf());
			}else if(job.contains("+STMT:")){
				ReproduceBenchmark_remote.add(job.substring(job.indexOf(":") + 1));
				getSender().tell(true, getSelf());
			}else if(job.contains("METHOD:")){
				boolean notreach = ReproduceBenchmark_remote.locateCGNode(job.substring(job.indexOf(":") + 1));
				if(notreach){
					getSender().tell(true, getSelf());
				}else{
					getSender().tell(false, getSelf());
				}
			}else if(job.contains("BENCHMARK:")){
				ReproduceBenchmark_remote.prepare(job.substring(job.indexOf(":") + 1));
				getSender().tell(true, getSelf());
			}else if(job.contains("PERFORMANCE")){
				ReproduceBenchmark_remote.performance();
				getSender().tell(true, getSelf());
			}
		}else{
			unhandled(message);
		}
	}


	private void register(Member member) {
		if (member.hasRole("frontend")){
			log.info("**********Registered to the frontend complete***********\n");
			getContext().actorSelection(member.address() + "/user/frontend").tell(BACKEND_REGISTRATION, getSelf());
		}
	}



}