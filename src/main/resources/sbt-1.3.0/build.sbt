import scala.concurrent.duration._

scalaVersion := "2.12.8"
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.5.16",
  "org.scalatest" %% "scalatest" % "3.0.5" % "test",
)
watchAntiEntropy := 0.millis
//ThisBuild / turbo := true
testFrameworks := new TestFramework("org.scalatest.tools.Framework") :: Nil
