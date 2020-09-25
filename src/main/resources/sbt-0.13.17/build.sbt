import scala.concurrent.duration._

scalaVersion := "2.12.12"
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.6.8",
  "org.scalatest" %% "scalatest" % "3.0.8" % "test",
  "com.lihaoyi" %% "utest" % "0.6.6" % "test"
)
