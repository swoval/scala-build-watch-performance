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

The results below are taken from 10 iterations. Before the first iteration,
the tool warms up the jvm with 5 iterations whose results are discareded. The
total time is the time between forking the process and all of the test iterations
completing.

### linux
project | min (ms) | max (ms) | mean (ms) | total (ms)
:------- | :------: | :------: | :------: | :------:
gradle-5.4.1 (3 source files) | 2680 | 2988 | 2796 | 78046
mill-0.3.6 (3 source files) | 4772 | 5200 | 5021 | 131054
sbt-0.13.17 (3 source files) | 1580 | 2284 | 1772 | 153263
sbt-1.3.0 (3 source files) | 364 | 668 | 502 | 80426
gradle-5.4.1 (5003 source files) | 3948 | 4368 | 4125 | 112568
mill-0.3.6 (5003 source files) | 6812 | 7744 | 7230 | 155183
sbt-0.13.17 (5003 source files) | 2448 | 3560 | 2634 | 84141
sbt-1.3.0 (5003 source files) | 1040 | 1308 | 1123 | 66833

### mac
project | min (ms) | max (ms) | mean (ms) | total (ms)
:------- | :------: | :------: | :------: | :------:
gradle-5.4.1 (3 source files) | 8842 | 11470 | 10340 | 162813
mill-0.3.6 (3 source files) | 3716 | 4107 | 3969 | 99997
sbt-0.13.17 (3 source files) | 1332 | 1988 | 1601 | 44007
sbt-1.3.0 (3 source files) | 324 | 470 | 371 | 28189
gradle-5.4.1 (5003 source files) | 9046 | 11345 | 10329 | 279254
mill-0.3.6 (5003 source files) | 6504 | 8985 | 7170 | 139627
sbt-0.13.17 (5003 source files) | 3164 | 3726 | 3375 | 96043
sbt-1.3.0 (5003 source files) | 1410 | 1681 | 1530 | 62114

Note that gradle performs very poorly because it uses the built in jvm
[WatchService](https://docs.oracle.com/javase/8/docs/api/java/nio/file/WatchService.html)
to monitor file. On osx, the defualt `WatchService` is a slow polling implementation.


### windows
 project | min (ms) | max (ms) | mean (ms) | total (ms)
:------- | :------: | :------: | :------: | :------:
gradle-5.4.1 (3 source files) | 2410 | 2619 | 2491 | 69213
mill-0.3.6 (3 source files) | 3982 | 4380 | 4140 | 108310
sbt-0.13.17 (3 source files) | 1466 | 1904 | 1550 | 43391
sbt-1.3.0 (3 source files) | 324 | 524 | 396 | 35296
gradle-5.4.1 (5003 source files) | 3798 | 4106 | 3896 | 96041
mill-0.3.6 (5003 source files) | 7199 | 7958 | 7621 | 155524
sbt-0.13.17 (5003 source files) | 3548 | 4122 | 3680 | 93753
sbt-1.3.0 (5003 source files) | 1148 | 1313 | 1234 | 61056
