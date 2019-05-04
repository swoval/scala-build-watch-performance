package sbt.benchmark

import akka.actor.ActorSystem
import java.nio.file.{ Files, Paths }
import scala.concurrent.Await
import scala.concurrent.duration._

object AkkaMain {
  val root = Paths.get("").toRealPath()
  def main(args: Array[String]): Unit = {
    val now = System.nanoTime
    val system = ActorSystem("akka")
    Await.result(system.terminate(), 5.seconds)
    val elapsed = System.nanoTime - now
    println(s"Took ${elapsed / 1.0e6} ms to run main")
    val outputFile = root.resolve("watch.out")
    println(s"Wrote to $outputFile")
    Files.write(outputFile, "".getBytes);
  }
}
