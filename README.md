# D4: Fast Concurrency Debugging with Parallel Differential Analysis

This artifact contains the source code of D4, the runnable jar files of ECHO and 14 benchmarks (jar files) from Dacapo-9.12. We provide the evaluation to reproduce the performance results presented in the PLDI'18 paper: "D4: Fast Concurrency Debugging with Parallel Differential Analysis" (Tables 4, 5, 6, 7). Due to the extra long running time to reproduce the statistics in the paper, we also provide a shorter evaluation for reviewers to evaluate the performance, which compares the same aspects but using less amount of time.

For the parallel incremental points-to analysis described in the paper, we have successfully integrated the code into the popular [WALA](https://github.com/april1989/Incremental_Points_to_Analysis.git) framework. Please checkout the linked repository, which has a more comphrensive documentation. For this artifact, the relevant code is in ````com.ibm.wala.core````.

For the static happens-before anlaysis and parallel incremental detection, the code lies in ````edu.tamu.cse.aser.d4````.

### Software Dependencies
- Java 1.8 to compile
- a core subset of wala (1.3.4), already included
- Eclipse Mars
- Akka 2.4.17, already included

### Build D4 in Eclipse
We provide the source code of D4. You can use Eclipse to import, build and run the source code. 

After ````git clone git@github.com:parasol-aser/D4.git````, you can import all the projects into Eclipse (all the required dependencies/libraries are included in the github), compile and build using Java 1.7. All the benchmark names from Dacapo-9.12 are listed below:

````avrora, batik, eclipse, fop, h2, jython, luindex, lusearch, pmd, sunflow, tomcat, tradebeans, tradesoap, xalan````.

For each benchmark, we will evaluate the performance of D4-1 and then D4-48. 

### D4 Evaluation

#### 1. A Full Version
To reproduce the full data of D4-1 in the paper, in folder ````edu.tamu.cse.aser.d4```` please run the main method in class ````src/edu/tamu/aser/tide/tests/ReproduceBenchmarks.java```` with program argument ````all```` in Run Configration. To reproduce individual benchmark data, please run the main method with the benchmark name as the program argument.

To reproduce the full data of D4-48 (distributed) in the paper, please update the ````master.conf```` (in ````/edu.tamu.cse.aser.d4/src/master.conf````) and ````worker.conf```` (````/edu.tamu.cse.aser.d4remote/src/worker.conf````) with the local machine and remote server hostnames as indicated in the ````.conf```` files. Then, export ````edu.tamu.cse.aser.d4remote```` as a runnable jar with the main method ````/edu.tamu.cse.aser.d4remote/src/edu/tamu/aser/tide/dist/remote/remote/BackendStart.java```` and "Copy required libraries into a sub-folder next to the generated JAR". Transfer the generated jar, sub-folder libraries and ````/edu.tamu.cse.aser.d4remote/data```` to your remote server. Run the jar and wait for the connection between the local machine and the server, and then the evaluation starts.

The generated statistics of D4-1 will be shown in Eclipse console, and the one of D4-48 will be shown in terminal. 

#### 2. A Shorter Version
Reproducing the whole experiment results in the paper may require about 10 days, which is too long for reviewer evaluation. So, we provide a script with a much shorter running time to help the reviewer compare the performance. 

To evaluate D4-1 and D4-48, please run ````ReproduceBenchmarks.java```` with program argument ````all_short````. For each benchmark, please run the main method with the benchmark name + ````_short````, for example, ````avrora_short````.

#### 3. Running D4 on a Single Machine


### ECHO Evaluation
We provided runnable jars to evaluate the ECHO performance (including the Reset-Recompute algorithm, Reachability-based algorithm, and its race detection). The jars are in ````/echo_jars/````. To reproduce the full data of ECHO, please run the jar with argument ````all````. To reproduce individual benchmark data, please run the main method with the benchmark name. 

We also prepared a short version to evaluate ECHO by using the argument ````all_short```` or the benchmark name + ````_short````. The generated statistics of ECHO will be shown in terminal.

Example:
````java 
java -jar echo.jar eclipse_short
````

### About Evaluation Results
We don't expect absolute values to match with the paper, due to the hardware difference and the stability of the wireless connection. But we expect a similar trend of D4 performance metrics as presented in the paper.

### Running D4 in Your Own Application 

#### Technical Details
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
[PLDI'18] "D4: Fast Concurrency Debugging with Parallel Differential Analysis"
