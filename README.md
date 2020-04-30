This project provides a benchmarking tool of the common development
feedback loop of edit/compile/test repeat. The tool tests performance
of a simple scala project that has a library dependency on
[akka](https://akka.io). There is a simple [scalatest](http://www.scalatest.org)
test that starts and stops an actor system and then writes a result
code to a file. The benchmark program forks a new instance of the build
tool in continuous test execution mode. For each iteration of the test,
the benchmark tool modifies the source file that contains the output file
of the test with a new output file. This triggers a continuous build in the
forked build tool process. The benchmark program monitors the new test output
until it has been written. It then compares the last modified time of the source
file and the test output file to determine the end to end latency between
modifying the source file and the test completing.

The test also checks the cpu utilization of the continuous build while waiting
for updates. After the tests complete, the tool calculates the average cpu
utilization of the forked build tool process for five seconds and reports the
results.

The results below are taken from 10 iterations. Before the first iteration,
the tool warms up the jvm with 5 iterations whose results are discareded. The
total time is the time between forking the process and all of the test iterations
completing. Results for sbt 1.3.0 were generated in three configurations:
* in-process
* in-process with turbo mode on
* fork

### Linux
All tests are run on a travis-ci virtual machine on ubuntu trusty using an ext4
file system.

project | min (ms) | max (ms) | median (ms) | total (ms) | cpu % |
:------- | -------: | -------: | --------: | ---------: | ----: |
sbt 1.3.0 turbo (3 source files) | 548 | 809 | 667 | 41436 | 4.6
sbt 1.3.0 turbo (5003 source files) | 1205 | 1680 | 1477 | 82742 | 15.6
sbt 1.3.0 (3 source files) | 1363 | 1949 | 1540 | 91434 | 3.4
sbt 0.13.17 (3 source files) | 1759 | 2771 | 1901 | 175888 | 3.4
sbt 1.3.0 (5003 source files) | 2054 | 2650 | 2371 | 100320 | 4.2
sbt 0.13.17 (5003 source files) | 2717 | 3394 | 3063 | 108808 | 21.6
gradle 5.4.1 (3 source files) | 2857 | 3385 | 3178 | 94218 | 1.2
sbt 1.3.0 fork (3 source files) | 5345 | 5932 | 5519 | 124033 | 6.8
mill 0.3.6 (3 source files) | 5447 | 6108 | 5647 | 157493 | 12.2
bloop 1.3.2 (3 source files) | 7213 | 8384 | 7390 | 183295 | 19.9
bloop 1.3.2 (5003 source files) | 8499 | 10065 | 9038 | 230710 | 24.8
gradle 5.4.1 (5003 source files) | 4368 | 4880 | 4547 | 138699 | 1.1
sbt 1.3.0 fork (5003 source files) | 5967 | 6897 | 6604 | 167953 | 7.0
mill 0.3.6 (5003 source files) | 7456 | 8014 | 7649 | 182737 | 15.2

### Mac
All tests are run on a travis-ci virtual machine on High Sierra using the HFS+
file system.

project | min (ms) | max (ms) | median (ms) | total (ms) | cpu % |
:------- | -------: | -------: | --------: | ---------: | ----: |
sbt 1.3.0 turbo (3 source files) | 419 | 661 | 436 | 31473 | 9.2
sbt 1.3.0 (3 source files) | 1092 | 1696 | 1240 | 73459 | 1.6
sbt 1.3.0 turbo (5003 source files) | 1345 | 1587 | 1465 | 78943 | 1.5
sbt 0.13.17 (3 source files) | 1574 | 2240 | 1868 | 152665 | 5.1
sbt 1.3.0 (5003 source files) | 2274 | 2828 | 2492 | 102105 | 1.3
gradle 5.4.1 (3 source files) | 4091 | 4608 | 4321 | 111288 | 6.2
sbt 0.13.17 (5003 source files) | 4608 | 5807 | 4913 | 142809 | 63.9
sbt 1.3.0 fork (3 source files) | 4704 | 5287 | 4941 | 107775 | 2.7
mill 0.3.6 (3 source files) | 4805 | 5199 | 4896 | 128934 | 6.2
bloop 1.3.2 (3 source files) | 5912 | 6677 | 6160 | 152398 | 1.1
sbt 1.3.0 fork (5003 source files) | 6108 | 6663 | 6314 | 163833 | 3.9
gradle 5.4.1 (5003 source files) | 6128 | 6685 | 6383 | 156922 | 0.2
bloop 1.3.2 (5003 source files) | 9354 | 11527 | 10543 | 236860 | 1.4
mill 0.3.6 (5003 source files) | 8397 | 9201 | 8896 | 189477 | 60.0

### Windows
All tests are run on an appveyor vm using the Visual Studio 17 disk image (which
should have Windows 10 api compatibility).

project | min (ms) | max (ms) | median (ms) | total (ms) | cpu % |
:------- | :------: | :------: | :-------: | :--------: | :---: |
sbt 1.3.0 turbo (3 source files) | 379 | 571 | 467 | 28019 | 1.6
sbt 1.3.0 (3 source files) | 1011 | 1322 | 1115 | 46220 | 0.39
sbt 1.3.0 turbo (5003 source files) | 1216 | 1857 | 1320 | 61526 | 0.99
sbt 0.13.17 (3 source files) | 1381 | 1679 | 1445 | 43758 | 3.6
sbt 1.3.0 (5003 source files) | 2184 | 2456 | 2299 | 78429 | 0.19
gradle 5.4.1 (3 source files) | 2353 | 2574 | 2451 | 72313 | 0.0
sbt 0.13.17 (5003 source files) | 3395 | 4266 | 3606 | 97515 | 47.6
sbt 1.3.0 fork (3 source files) | 3710 | 4054 | 3832 | 84962 | 0.19
gradle 5.4.1 (5003 source files) | 3787 | 4010 | 3892 | 100764 | 0.0
mill 0.3.6 (3 source files) | 4221 | 4407 | 4318 | 110936 | 1.59
sbt 1.3.0 fork (5003 source files) | 4862 | 5457 | 5089 | 126370 | 1.8
mill 0.3.6 (5003 source files) | 7548 | 8289 | 7961 | 170190 | 66.59

Note that the bloop fails to run on appveyor.

You can use sbt to run the tests on your own machine with the `run` task. It is
somewhat faster to compile and run by hand. To compile, run
```
javac -cp lib/swoval/file-tree-views-2.1.1.jar src/main/java/build/performance/Main.java -d target/classes
```
from the project root directory. To run, execute:
```
java -classpath
target/classes:src/main/resources:lib/swoval/file-tree-views-2.1.1.jar build.performance.Main -i 50 -w 50 -e 5000 sbt-1.3.10-turbo
```

Command arguments:
* -i -- sets how many test iterations to run (default 5)
* -w -- sets how many jvm warmup iterations to run before the counted test iterations (default 3)
* -e -- sets how many extra sources to add for the second round of testing to simulate large projects (default 5000)
* -c -- how many seconds to sample cpu utilization during idle watch (default 5)
* -t -- sets a timeout in seconds for each test iteration (default 10)
* -j -- a custom java home for an alternate jdk
* the tail arguments will be a space separated list of the tests to run, e.g.
`sbt-1.3.10-turbo gradle-5.4.1`

To test a SNAPSHOT sbt version, just change the version in
src/main/resources/sbt-1.3*/project/build.properties to `1.4.0-SNAPSHOT`.
