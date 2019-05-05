package sbt.benchmark

import akka.actor.ActorSystem
import java.nio.file.{ Files, Paths }
import scala.concurrent.Await
import scala.concurrent.duration._

object AkkaMain {
  def main(args: Array[String]): Unit = {
    Await.result(ActorSystem("akka").terminate(), 5.seconds)
    Files.write(Paths.get("").toRealPath().resolve("watch.out"), "".getBytes);
  }
}

