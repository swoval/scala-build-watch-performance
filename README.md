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
:------- | :------: | :------: | :-------: | :--------: | :---: |
sbt-1.3.10-turbo (3 source files) | 322 | 561 | 395 | 26344 | 2.4
sbt-1.3.10-turbo (5003 source files) | 842 | 1147 | 947 | 51897 | 2.2
sbt-1.3.10 (3 source files) | 874 | 1278 | 1085 | 41381 | 2.4
sbt-0.13.17 (3 source files) | 1294 | 1788 | 1403 | 43059 | 1.4
sbt-1.3.10 (5003 source files) | 1426 | 2381 | 1603 | 66916 | 2.4
gradle-5.4.1 (3 source files) | 2067 | 2495 | 2216 | 65365 | 0.2
sbt-0.13.17 (5003 source files) | 2035 | 2780 | 2320 | 73575 | 24.8
gradle-5.4.1 (5003 source files) | 3180 | 3347 | 3230 | 93091 | 1.1
sbt-1.3.10-fork (3 source files) | 3465 | 3796 | 3628 | 84522 | 4.4
mill-0.6.2 (3 source files) | 3771 | 3983 | 3874 | 101696 | 8.8
sbt-1.3.10-fork (5003 source files) | 3987 | 4422 | 4233 | 110730 | 4.4
bloop-1.4.0-RC1 (3 source files) | 4806 | 5680 | 5021 | 129687 | 18.4
mill-0.6.2 (5003 source files) | 5247 | 5593 | 5375 | 123035 | 14.2
bloop-1.4.0-RC1 (5003 source files) | 5606 | 6983 | 5792 | 158626 | 24.5

### Mac
All tests are run on a travis-ci virtual machine on High Sierra using the HFS+
file system.

project | min (ms) | max (ms) | median (ms) | total (ms) | cpu % |
:------- | :------: | :------: | :-------: | :--------: | :---: |
sbt-1.3.10-turbo (3 source files) | 394 | 611 | 453 | 30552 | 4.2
sbt-1.3.10 (3 source files) | 1101 | 1540 | 1241 | 47715 | 1.4
sbt-1.3.10-turbo (5003 source files) | 1207 | 1437 | 1307 | 68467 | 1.7
sbt-0.13.17 (3 source files) | 1536 | 1771 | 1665 | 53277 | 1.8
sbt-1.3.10 (5003 source files) | 2224 | 2499 | 2349 | 89168 | 1.8
gradle-5.4.1 (3 source files) | 4143 | 4502 | 4298 | 103339 | 0.3
sbt-1.3.10-fork (3 source files) | 4350 | 5106 | 4615 | 107939 | 4.1
mill-0.6.2 (3 source files) | 4774 | 5182 | 5050 | 126937 | 3.3
sbt-0.13.17 (5003 source files) | 4609 | 5392 | 5131 | 139712 | 61.2
sbt-1.3.10-fork (5003 source files) | 5720 | 6161 | 5892 | 152428 | 4.0
bloop-1.4.0-RC1 (3 source files) | 5655 | 6897 | 6340 | 150744 | 0.5
gradle-5.4.1 (5003 source files) | 5849 | 6537 | 6383 | 150934 | 0.2
mill-0.6.2 (5003 source files) | 8679 | 9452 | 8926 | 197095 | 59.8
bloop-1.4.0-RC1 (5003 source files) | 10003 | 11632 | 10472 | 246350 | 1.1

### Windows
All tests are run on an appveyor vm using the Visual Studio 17 disk image (which
should have Windows 10 api compatibility).

project | min (ms) | max (ms) | median (ms) | total (ms) | cpu % |
:------- | :------: | :------: | :-------: | :--------: | :---: |
sbt-1.3.10-turbo (3 source files) | 361 | 605 | 408 | 27871 | 1.39
sbt-1.3.10 (3 source files) | 955 | 1549 | 1069 | 42300 | 1.59
sbt-1.3.10-turbo (5003 source files) | 1149 | 1506 | 1237 | 60945 | 0.99
sbt-0.13.17 (3 source files) | 1437 | 1616 | 1506 | 45045 | 1.8
sbt-1.3.10 (5003 source files) | 2037 | 2720 | 2138 | 79363 | 0.4
gradle-5.4.1 (3 source files) | 2298 | 2511 | 2393 | 67899 | 0.0
sbt-0.13.17 (5003 source files) | 3394 | 3942 | 3489 | 95652 | 47.19
sbt-1.3.10-fork (3 source files) | 3691 | 4043 | 3819 | 90155 | 0.6
gradle-5.4.1 (5003 source files) | 3689 | 4134 | 3868 | 99622 | 0.0
mill-0.6.2 (3 source files) | 3953 | 4204 | 4052 | 107691 | 1.59
sbt-1.3.10-fork (5003 source files) | 4774 | 5273 | 5055 | 126160 | 0.0
bloop-1.4.0-RC1 (3 source files) | 6364 | 7508 | 6826 | 157417 | 0.0
mill-0.6.2 (5003 source files) | 7206 | 8001 | 7290 | 162586 | 63.19
bloop-1.4.0-RC1 (5003 source files) | 8619 | 11432 | 9491 | 229791 | 0.0

### Building locally

You can use sbt to run the tests on your own machine with the `run` task. It is
somewhat faster to compile and run by hand. To compile, run
```
javac -cp lib/swoval/file-tree-views-2.1.1.jar src/main/java/build/performance/Main.java -d target/classes
```
from the project root directory. To run, execute:
```
java -classpath target/classes:src/main/resources:lib/swoval/file-tree-views-2.1.1.jar build.performance.Main -i 50 -w 50 -e 5000 sbt-1.3.10-turbo
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
