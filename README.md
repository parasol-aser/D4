# D4: Fast Concurrency Debugging with Parallel Differential Analysis

D4 is a tool that interatively detects concurrency errors in multithreaded Java programs in the Eclipse IDE. For most code changes, it detects data races and deadlocks instantly, i.e., less than 100ms after the change is introduced. D4 is powered by a distributed system design and a set of parallel incremental algorithms for pointer analysis and happens-before analysis. We have also successfully integrated the pointer analysis code into the popular [WALA](https://github.com/april1989/Incremental_Points_to_Analysis.git) framework.

If you use this resource, please cite our PLDI'18 paper: "D4: Fast Concurrency Debugging with Parallel Differential Analysis".

### Software Dependencies
- Java 1.8 to compile
- a core subset of wala (1.3.4), already included
- Eclipse PDE
- Akka 2.4.17, already included

### Build D4 in Eclipse

````git clone git@github.com:parasol-aser/D4.git```` (may take a couple of minutes depending on the network speed)

Next, import all the projects into Eclipse (all the required dependencies/libraries are included in the github), compile and build using Java 1.8. This project is an Eclipse plugin project, please install "Eclipse PDE". Goto Eclipse -> Help -> Eclipse Marketplace, search "Eclipse PDE" and install. 

### D4 Docker Image
We provide a docker image running D4 on a local machine with a user-defined number of threads. To download the image, please run 
````docker pull aprildocker/d4_ubuntu_java8:firsttry```` 
in your terminal. If you see an error: ````Please login prior to pull: Login with your Docker ID to push and pull images from Docker Hub.````, please goto [https://hub.docker.com/](https://hub.docker.com/) to register an account and login.

### Running D4

### BOZHEN: add a tutorial here (plus a video demo)


### Running D4 with Your Own Application 
We provide an Eclipse plugin that implements the techniques in D4. You can launch the plugin by following:  ````/edu.tamu.cse.aser.d4```` -> ````MANIFEST.MF```` -> Testing -> Launch an Eclipse application. 

In the launched Eclipse workspace, you can create a new project or import your existing project. We provide an example code in ````edu.tamu.cse.aser.plugintests````. You can start the plugin by right-clicking the main class that you want to test, choose "ASER" and "ECHO", then, the initial detection starts to run. You can go to Window -> Show View -> Others to select our views (i.e. ECHO Concurrent Relations, ECHO Race List, ECHO Deadlock List) that report all the detected bugs. 

Then, you can make some changes in your program to fix the bugs and save the program. The plugin will run our incremental techniques to update the views according to the added/fixed bugs.

For some variables or methods that you do not want to analyze, you can right-click the variables/methods shown in the Outline or the races/concurrent relations to go to ECHO Bug Choice -> Ignore This Variable. 


### D4 Evaluation 

#### 1. on a single machine
You can run D4 using multi-threads on a single machine. In folder ````edu.tamu.cse.aser.d4````, run the main method in class ````src/edu/tamu/aser/tide/tests/ReproduceBenchmarks.java```` with program argument ````all_short```` and the number of threads you would like to test. For example, ````all_short 8```` or ````avrora_short 8```` (this took a couple of hours on our machine).

#### 2. on a distributed server

To run the distributed D4, update the ````master.conf```` (in ````/edu.tamu.cse.aser.d4/src/master.conf````) and ````worker.conf```` (````/edu.tamu.cse.aser.d4remote/src/worker.conf````) with the local machine and remote server hostnames. For example, ````master.conf```` is shown below. You need to change the ````hostname```` and ````port```` under ````#local pc```` to your local machine's, and change the address under ````#remote server```` to your remote server's name and port.

````
akka {
  actor {
    provider = "akka.cluster.ClusterActorRefProvider"
  }
  remote {
    maximum-payload-bytes = 30000000 bytes
    log-remote-lifecycle-events = off
    netty.tcp {
      #local pc
      hostname = "10.231.215.111"
      port = 4551
    }
  }

  cluster {
    seed-nodes = [
      #local machine
      "akka.tcp://ClusterSystem@10.231.215.111:4551",
      #remote server
      "akka.tcp://ClusterSystem@128.194.136.121:65501"
    ]
````

Then, export ````edu.tamu.cse.aser.d4remote```` as a runnable jar with the main method ````/edu.tamu.cse.aser.d4remote/src/edu/tamu/aser/tide/dist/remote/remote/BackendStart.java```` and the option "Copy required libraries into a sub-folder next to the generated JAR". Transfer the generated jar, sub-folder libraries and ````/edu.tamu.cse.aser.d4remote/data```` to your remote server. Run the jar and wait for the connection between the local machine and the server, and then the evaluation starts.

#### 3. using the Docker Image
To run D4 in the image, please run ````docker run -it aprildocker/d4_ubuntu_java8:firsttry $tool_name $benchmark_name $num_of_threads```` in your terminal. For example, ````docker run -it aprildocker/d4_ubuntu_java8:firsttry d4 fop_short 4```` will run D4 on benchmark ````fop_short```` with 4 threads. ````docker run -it aprildocker/d4_ubuntu_java8:firsttry echo sunflow_short 1```` will run ECHO on benchmark ````sunflow_short```` with 1 threads. Currently, ECHO can only run with 1 threads. 


#### More Technical Details
There are two main technical components in D4: differential pointer analysis and differential happens-before analysis with data race and deadlock detection. We have already released the source code of [our differential pointer analysis](https://github.com/april1989/Incremental_Points_to_Analysis.git), which includes an example of how to use the technique.

Here, we provide an example to illustrate the usage of our differential happens-before analysis with data race and deadlock detection.

After running the whole program pointer analysis, we can build our concurrency bug detection engine ````TIDEEngine```` that includes our static happens-before analysis.
````java 
//initialize the akka system for concurrency bug detection with n number of thread pool workers
ActorSystem akkasys = ActorSystem.create();
ActorRef bughub = akkasys.actorOf(Props.create(BugHub.class, n), "bughub");
//create a new engine for a target program with:
//mainSignature as its main method signature
//cg as its CallGraph, flowgraph as its PropagationGraph, pta as its PointerAnalysis; all are from previous pointer analysis
TIDEEngine engine = new TIDEEngine(mainSignature, cg, flowgraph, pta, bughub);
//start the concurrency bug detection for the first time, and obtain the detected bugs
Set<ITIDEBug> bugs = engine.detectBothBugs(ps);
````
Then, user need to specify four sets: ```delInsts``` to store all the deleted SSAInstructions, ```addInsts``` to store all the added onces, ````changedNodes```` denotes which CGNodes the instructions belong to, and  ````changedModifiers```` denotes which CGNodes have changed their modifiers.
```java
CGNode targetNode = node;
HashSet<SSAInstruction> delInsts = new HashSet<>();
HashSet<SSAInstruction> addInsts = new HashSet<>();
HashSet<CGNode> changedNodes = new HashSet<>();
HashSet<CGNode> changedModifiers= new HashSet<>();
````

```delInsts``` and ```addInsts``` are used for differential pointer analysis, and we can know whether the relative points-to sets have changed (use ````ptachanges```` to indicate). Then, we can run the incremental bug detection on the engine and obtain the new results after the instruction change.
````java
Set<ITIDEBug> bugs = engine.updateEngine(changedNodes, changedModifier, ptachanges, ps);
````

### Authors
Bozhen Liu, Texas A&M University

Jeff Huang, Texas A&M University

### Paper
[PLDI'18] "[D4: Fast Concurrency Debugging with Parallel Differential Analysis](https://parasol.tamu.edu/~jeff/d4.pdf)"
