# D4: Fast Concurrency Debugging with Parallel Differential Analysis

D4 is a tool that interatively detects concurrency errors in multithreaded Java programs in the Eclipse IDE. For most code changes, it detects data races and deadlocks instantly, i.e., less than 100ms after the change is introduced. D4 is powered by a set of parallel incremental algorithms for pointer analysis and happens-before analysis. We have also successfully integrated the pointer analysis code into the popular [WALA](https://github.com/april1989/Incremental_Points_to_Analysis.git) framework.

A video demo of D4 is here:

[![IMAGE ALT TEXT](https://img.youtube.com/vi/sAF4WYl7ANU/hqdefault.jpg)](https://www.youtube.com/watch?v=sAF4WYl7ANU&t=148s "D4 Demo")

If you use this resource, please cite our PLDI'18 paper: "D4: Fast Concurrency Debugging with Parallel Differential Analysis".

### Software Dependencies
- Java 1.8 to compile
- Eclipse PDE
- [WALA 1.3.4](https://github.com/wala/WALA) (included)
- [Akka](https://akka.io/) (included)

### Build D4 

````git clone git@github.com:parasol-aser/D4.git```` and import all the projects into Eclipse. 

### Run the Eclipse plugin of D4 
Launch the plugin:  ````/edu.tamu.cse.aser.d4```` -> ````MANIFEST.MF```` -> Testing -> Launch an Eclipse application. 

#### Concurrency bug detection

The whole program detection can be triggered by: Right-click a main class in Package Explorer, select ASER -> D4.
The incremental detection can be triggered by save the changed files.

#### D4 views

Please go to Window -> Show View -> Others to display our views (i.e., D4 Concurrent Relations, D4 Race List, D4 Deadlock List). 

#### Ignore/Consider a variable/method

If you do not want to analyze some variables or methods, right-click the variables/methods shown in the Outline view or the bugs/relations in our views, select D4 -> Ignore This Variable/Method. If you want to consider them later, right-click the variables/methods shown in the Outline view, select D4 -> Consider This Variable/Method.


### D4 Docker Image
We provide a docker image running D4 on a local machine with a user-defined number of threads. To download the image, please run 
````docker pull aprildocker/d4_ubuntu_java8:firsttry```` 
in your terminal. 

### Authors
Bozhen Liu, Texas A&M University

Jeff Huang, Texas A&M University

### Paper
[PLDI'18] "[D4: Fast Concurrency Debugging with Parallel Differential Analysis](https://parasol.tamu.edu/~jeff/d4.pdf)"
