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


### Authors
Bozhen Liu, Texas A&M University

Jeff Huang, Texas A&M University

### Paper
[PLDI'18] "[D4: Fast Concurrency Debugging with Parallel Differential Analysis](https://parasol.tamu.edu/~jeff/d4.pdf)"
