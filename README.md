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
#### Run D4 on a local machine
To perform the analysis for benchmark ````sunflow```` with a short version of exclused packages using 8 threads, we run the main method of class ````ReproduceBenchmarks```` in ````edu.tamu.cse.aser.d4```` with the program argument ````sunflow_short 8````.

The exclused packages can be modified by adding/removing package names in ````ShortDefaultExclusions.txt```` of ````edu.tamu.cse.aser.d4````.

#### Run D4 both on a local machine and a server
##### 1. Pack the jar running on server
Replace the hostnames of the local pc (labeled with #local pc) and remote server (labeled with #local pc) in ````master.conf```` and ````worker.conf```` in ````edu.tamu.cse.aser.d4```` and ````edu.tamu.cse.aser.d4remote```` by your pc and server addresses.

Generate a runnable jar of ````BackendStart```` in ````edu.tamu.cse.aser.d4remote```` with all the dependencies copied to a sub-folder.

Transfer the jar, its dependency folder and the data folder of ````edu.tamu.cse.aser.d4remote```` to the remote server. For example, you can use FileZilla.

##### 2. Run D4 
To perform the analysis for benchmark ````sunflow```` with a short version of exclusion packages, we need to run the remote jar with the command: ````java -Dconfig.file=worker.conf -jar backend.jar sunflow_short````. Then, run the main of class ````ReproduceBenchmarks```` in ````edu.tamu.cse.aser.d4```` with the program argument ````sunflow_short````. The analysis is performed with a default thread number of 48. 

A video demo is available at [YouTube]().

### Running D4 with Your Own Application 
We provide an Eclipse plugin that implements the techniques in D4. You can launch the plugin by following:  ````/edu.tamu.cse.aser.d4```` -> ````MANIFEST.MF```` -> Testing -> Launch an Eclipse application. A video demo is available at [YouTube](https://www.youtube.com/watch?v=88W40z15kR4).

In the launched Eclipse workspace, you can create a new project or import your existing project. We provide an example code in ````edu.tamu.cse.aser.plugintests````. You can start the plugin by right-clicking the main class that you want to test, choose "ASER" and "D4", then, the initial detection starts to run. You can go to Window -> Show View -> Others to select our views (i.e. D4 Concurrent Relations, D4 Race List, D4 Deadlock List) that report all the detected bugs. 

Then, you can make some changes in your program to fix the bugs and save the program. The plugin will run our incremental techniques to update the views according to the added/fixed bugs.

For some variables or methods that you do not want to analyze, you can right-click the variables/methods shown in the Outline or the races/concurrent relations to go to D4 Bug Choice -> Ignore This Variable. 


### Authors
Bozhen Liu, Texas A&M University

Jeff Huang, Texas A&M University

### Paper
[PLDI'18] "[D4: Fast Concurrency Debugging with Parallel Differential Analysis](https://parasol.tamu.edu/~jeff/d4.pdf)"
