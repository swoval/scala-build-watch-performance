import scala.concurrent.duration._

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.5.16",
  "org.scalatest" %% "scalatest" % "3.0.5" % "test",
)
watchAntiEntropy := 0.millis
ThisBuild / turbo := true
