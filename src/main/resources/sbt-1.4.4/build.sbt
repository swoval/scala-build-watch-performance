import scala.concurrent.duration._

scalaVersion := "2.13.3"
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.6.8",
  "org.scalatest" %% "scalatest" % "3.0.8" % "test",
)
watchAntiEntropy := 0.millis
