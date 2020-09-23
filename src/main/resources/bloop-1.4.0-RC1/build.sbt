import scala.concurrent.duration._

scalaVersion := "2.13.3"
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.5.16",
  "org.scalatest" %% "scalatest" % "3.0.5" % "test",
)
bloopConfigDir := baseDirectory.value / ".bloop"
