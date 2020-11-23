import scala.concurrent.duration._

scalaVersion := "2.13.4"
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.6.8",
  "org.scalatest" %% "scalatest" % "3.1.0" % "test",
)
watchAntiEntropy := 0.millis
ThisBuild / turbo := true
