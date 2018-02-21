# D4: Fast Concurrency Debugging with Parallel Differential Analysis

This artifact contains the source code of D4, the runnable jar files of ECHO and 14 benchmarks (jar files) from Dacapo-9.12. We provide the evaluation to reproduce the performance results presented in the PLDI'18 paper: D4: Fast Concurrency Debugging with Parallel Differential Analysis (Table 4, 5, 6, 7). Due to the extra long running time to reproduce the statistics in the paper, we also provide a shorter evaluation for reviewers to evaluate the performance, which compares the same aspects but using less amount of time.

For the parallel incremental points-to analysis described in the paper, we have integrated into [wala](https://github.com/april1989/Incremental_Points_to_Analysis.git), which has a better documentation. For this artifact, we have integrated the relevant code into ````com.ibm.wala.core````.

For the static happens-before anlaysis and parallel incremental detection, the code lies in ````edu.tamu.cse.aser.d4````.

### Software Dependencies
- Java 1.7
- a core subset of wala (1.3.4), already included
- Eclipse Mars
- Akka 2.4.17, already included

### Build D4
We provide the source code of D4. You can use Eclipse to import, build and run the source code. 

#### 1. Using Eclipse
After ````git clone https://github.com/parasol-aser/D4.git````, you can import all the projects into Eclipse (all the required dependencies/libraries are included in the github), compile and build using Java 1.7. All the benchmark names from Dacapo-9.12 are listed below:

````avrora, batik, eclipse, fop, h2, jython, luindex, lusearch, pmd, sunflow, tomcat, tradebeans, tradesoap, xalan````.

For each benchmark, we will evaluate the performance of D4-1 and then D4-48. 

#### 2. A Full Version of Evaluation
To reproduce the full data of D4-1 in the paper, please run the main method in ````/edu.tamu.cse.aser.d4/src/edu/tamu/aser/tide/tests/ReproduceBenchmarks.java```` with program argument ````all```` in Run Configration. To reproduce individual benchmark data, please run the main method with the benchmark name as the program argument.

To reproduce the full data of D4-48 (distributed) in the paper, please update the ````master.conf```` (in ````/edu.tamu.cse.aser.d4/src/master.conf````) and ````worker.conf```` (````/edu.tamu.cse.aser.d4remote/src/worker.conf````) with the local machine and remote server hostnames as indicated in the ````.conf```` files. Then, export ````edu.tamu.cse.aser.d4remote```` as a runnable jar with the main method ````/edu.tamu.cse.aser.d4remote/src/edu/tamu/aser/tide/dist/remote/remote/BackendStart.java```` and "Copy required libraries into a sub-folder next to the generated JAR". Transfer the generated jar, sub-folder libraries and ````/edu.tamu.cse.aser.d4remote/data```` to your remote server. Run the jar and wait for the connection between the local machine and the server, and then the evaluation starts.

The generated statistics of D4-1 will be shown in Eclipse console, and the one of D4-48 will be shown in terminal. 

#### 3. A Shorter Version of Evaluation
Reproducing the whole experiment results in the paper may require about 10 days, which is too long for reviewer evaluation. So, we provide a script with a much shorter running time to help the reviewer compare the performance. 

To evaluate D4-1 and D4-48, please run ````ReproduceBenchmarks.java```` with program argument ````all_short````. For each benchmark, please run the main method with the benchmark name + ````_short````, for example, ````avrora_short````.

#### 4. Evaluation of ECHO
We provided runnable jars to evaluate the ECHO performance (including the Reset-Recompute algorithm, Reachability-based algorithm, and its race detection). The jars are in ````/ECHO_jars/````. To reproduce the full data of ECHO, please run the jar with argument ````all````. To reproduce individual benchmark data, please run the main method with the benchmark name. 

We also prepared a short version to evaluate ECHO by using the argument ````all_short```` or the benchmark name + ````_short````. The generated statistics of ECHO will be shown in terminal.

#### 5. Validation of The Evaluation Results
We don't expect absolute values to match with the paper, due to the hardware difference and the stability of the wireless connection. But we expect a similar trend of D4 performance metrics as presented in the paper.

### How to Use D4
There are two novel techniques in D4: incremental pointer analysis and incremental happens-before analysis with its data race and deadlock detection. We have already released the source code of [our incremental pointer analysis](https://github.com/april1989/Incremental_Points_to_Analysis.git), which includes an example of how to use the technique.

Here, we provide an example to illustrate the usage of our incremental happens-before analysis with its data race and deadlock detection.


### Authors
Bozhen Liu, Texas A&M University

Jeff Huang, Texas A&M University

### Paper
[PLDI'18] "D4: Fast Concurrency Debugging with Parallel Differential Analysis"
