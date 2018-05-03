# D4: Fast Concurrency Debugging with Parallel Differential Analysis

D4 is a tool that interatively detects concurrency errors in multithreaded Java programs in the Eclipse IDE. For most code changes, it detects data races and deadlocks instantly, i.e., less than 100ms after the change is introduced. D4 is powered by a distributed system design and a set of parallel incremental algorithms for pointer analysis and happens-before analysis. We have also successfully integrated the pointer analysis code into the popular [WALA](https://github.com/april1989/Incremental_Points_to_Analysis.git) framework.

We provide an Eclipse plugin that implements the techniques in D4 and a video demo to introduce its features: 
<iframe width="560" height="315" src="https://www.youtube.com/embed/sAF4WYl7ANU" frameborder="0" allow="autoplay; encrypted-media" allowfullscreen></iframe>
If you use this resource, please cite our PLDI'18 paper: "D4: Fast Concurrency Debugging with Parallel Differential Analysis".

### Software Dependencies
- Java 1.8 to compile

### Build D4 in Eclipse

````git clone git@github.com:parasol-aser/D4.git```` (may take a couple of minutes depending on the network speed)

Next, import all the projects into Eclipse (all the required dependencies/libraries are included in the github), compile and build using Java 1.8. This project is an Eclipse plugin project, please install "Eclipse PDE". Goto Eclipse -> Help -> Eclipse Marketplace, search "Eclipse PDE" and install. 

### D4 Docker Image
We provide a docker image running D4 on a local machine with a user-defined number of threads. To download the image, please run 
````docker pull aprildocker/d4_ubuntu_java8:firsttry```` 
in your terminal. 

### Running the Eclipse plugin of D4 
You can launch the plugin by following:  ````/edu.tamu.cse.aser.d4```` -> ````MANIFEST.MF```` -> Testing -> Launch an Eclipse application. 

As we introduced in the video demo, in the launched Eclipse workspace, you can create a new project or import your existing project. Right-click a main class select ASER -> D4 to start the initial detection of the plugin. Please go to Window -> Show View -> Others to display our views (i.e., D4 Concurrent Relations, D4 Race List, D4 Deadlock List) that report all the detected bugs. 

After some changes in your program to fix the bugs and save the program, the plugin will run in background to update the views.

If you do not want to analyze some variables or methods, right-click the variables/methods shown in the Outline view or the race list/concurrent relations, select D4 -> Ignore This Variable/Method. If you want to consider them later, right-click the variables/methods shown in the Outline view, select D4 -> Consider This Variable/Method.


### Running D4 
#### Run D4 on a local machine
To perform the analysis for benchmark ````sunflow```` using 8 threads with a list of exclused packages, run the main method of class ````ReproduceBenchmarks```` in ````edu.tamu.cse.aser.d4```` with the program argument ````sunflow_short 8````.

The exclused packages can be modified by adding/removing package names in ````ShortDefaultExclusions.txt```` from ````edu.tamu.cse.aser.d4````.

#### Run D4 both on a local machine and a server
##### Pack the jar running on server
Replace the hostnames of the local pc (labeled with #local pc) and remote server (labeled with #local pc) in ````master.conf```` (in ````edu.tamu.cse.aser.d4````) and ````worker.conf```` (in ````edu.tamu.cse.aser.d4remote````) with your pc and server IP addresses.

Generate a runnable jar of ````BackendStart```` in ````edu.tamu.cse.aser.d4remote````, and transfer it to your server together with the data folder of ````edu.tamu.cse.aser.d4remote````.

##### Start D4 on both sides
To perform the analysis for benchmark ````sunflow```` with a list of exclusion packages, run the remote jar with the command: ````java -Dconfig.file=worker.conf -jar backend.jar sunflow_short````. Then, run the main method of class ````ReproduceBenchmarks```` in ````edu.tamu.cse.aser.d4```` with the program argument ````sunflow_short````. The analysis is performed with a default thread number of 48. 


### Authors
Bozhen Liu, Texas A&M University

Jeff Huang, Texas A&M University

### Paper
[PLDI'18] "[D4: Fast Concurrency Debugging with Parallel Differential Analysis](https://parasol.tamu.edu/~jeff/d4.pdf)"
