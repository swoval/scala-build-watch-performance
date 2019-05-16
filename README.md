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
for updates. After the tests completes, the tool calculates the average cpu
utilization of the forked build tool process for five seconds and reports the
results.

The results below are taken from 10 iterations. Before the first iteration,
the tool warms up the jvm with 5 iterations whose results are discareded. The
total time is the time between forking the process and all of the test iterations
completing.

### Linux
All tests are run on a travis-ci virtual machine on ubuntu trusty using an ext4
file system.

project | min (ms) | max (ms) | mean (ms) | total (ms) | cpu % |
:------- | -------: | -------: | --------: | ---------: | ----: |
sbt-1.3.0 (3 source files) |` 427 `|` 585 `|` 522 `|` 40840 `| 5.4
sbt-1.3.0 (5003 source files) |` 1101 `|` 1807 `|` 1261 `|` 71880 `| 4.8
sbt-0.13.17 (3 source files) |` 1600 `|` 2073 `|` 1773 `|` 52281 `| 6.4
sbt-0.13.17 (5003 source files) |` 2588 `|` 3709 `|` 2874 `|` 90142 `| 22.2
gradle-5.4.1 (3 source files) |` 2705 `|` 3044 `|` 2846 `|` 80450 `| 0.2
gradle-5.4.1 (5003 source files) |` 4088 `|` 4357 `|` 4182 `|` 116374 `| 0.2
mill-0.3.6 (3 source files) |` 5003 `|` 5256 `|` 5120 `|` 129747 `| 3.2
bloop-1.2.5 (3 source files) |` 7002 `|` 7597 `|` 7214 `|` 148343 `| 1.0
bloop-1.2.5 (5003 source files) |` 7174 `|` 8323 `|` 7601 `|` 165949 `| 20.2
mill-0.3.6 (5003 source files) |` 7072 `|` 7369 `|` 7198 `|` 156293 `| 36.0

### Mac
All tests are run on a travis-ci virtual machine on High Sierra using the HFS+
file system.

project | min (ms) | max (ms) | mean (ms) | total (ms) | cpu % |
:------- | -------: | -------: | --------: | ---------: | ----: |
sbt-1.3.0 (3 source files) |` 394 `|` 534 `|` 459 `|` 38067 `| 1.2
sbt-1.3.0 (5003 source files) |` 1163 `|` 1287 `|` 1231 `|` 73216 `| 1.6
sbt-0.13.17 (3 source files) |` 1659 `|` 2038 `|` 1807 `|` 53694 `| 4.9
gradle-5.4.1 (3 source files) |` 4118 `|` 4485 `|` 4360 `|` 100233 `| 0.1
sbt-0.13.17 (5003 source files) |` 5044 `|` 5561 `|` 5199 `|` 142877 `| 67.3
mill-0.3.6 (3 source files) |` 5172 `|` 5437 `|` 5279 `|` 127697 `| 2.2
bloop-1.2.5 (3 source files) |` 6393 `|` 7217 `|` 6675 `|` 136354 `| 1.1
gradle-5.4.1 (5003 source files) |` 6259 `|` 6699 `|` 6433 `|` 147034 `| 0.1
bloop-1.2.5 (5003 source files) |` 8257 `|` 8951 `|` 8453 `|` 183090 `| 0.4
mill-0.3.6 (5003 source files) |` 8894 `|` 9478 `|` 9155 `|` 189889 `| 58.4

### Windows
All tests are run on an appveyor vm using the Visual Studio 17 disk image (which
should have Windows 10 api compatibility).

project | min (ms) | max (ms) | mean (ms) | total (ms) | cpu % |
:------- | -------: | -------: | --------: | ---------: | ----: |
sbt-1.3.0 (3 source files) |` 328 `|` 505 `|` 402 `|` 30022 `| 1.6
sbt-1.3.0 (5003 source files) |` 1050 `|` 1244 `|` 1142 `|` 59030 `| 2.99
sbt-0.13.17 (3 source files) |` 1398 `|` 1576 `|` 1483 `|` 41565 `| 2.19
gradle-5.4.1 (3 source files) |` 2228 `|` 2445 `|` 2321 `|` 66939 `| 0.0
sbt-0.13.17 (5003 source files) |` 3366 `|` 4005 `|` 3568 `|` 91450 `| 49.2
gradle-5.4.1 (5003 source files) |` 3635 `|` 4343 `|` 3837 `|` 92657 `| 0.0
mill-0.3.6 (3 source files) |` 3991 `|` 4283 `|` 4154 `|` 103170 `| 1.59
mill-0.3.6 (5003 source files) |` 7043 `|` 8030 `|` 7440 `|` 151659 `| 65.19

Note that the bloop fails to run on appveyor.
