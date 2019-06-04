import scala.concurrent.duration._

scalaVersion := "2.12.8"
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.5.16",
  "org.apache.spark" %% "spark-sql" % "2.4.3",
  "org.scalatest" %% "scalatest" % "3.0.5" % "test",
)
watchAntiEntropy := 0.millis
