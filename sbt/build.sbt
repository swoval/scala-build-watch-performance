val sbtWatchPerformance = (project in file(".")).settings(
  name := "sbt-watch-performance",
  scalaVersion := "2.12.8",
  libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-actor" % "2.5.16",
    "org.scalatest" %% "scalatest" % "3.0.5" % "test",
    "com.lihaoyi" %% "utest" % "0.6.6" % "test"
  ),
  testFrameworks := Seq(new TestFramework("utest.runner.Framework")),
)
