package sbt.benchmark

import java.io.IOException
import java.nio.file.{Files, Paths}
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.collection.JavaConverters._

object SimpleMain {
  def run(args: Array[String]): Unit = {
    val count =
      try Files.walk(WatchFile.blahPath).iterator.asScala.toVector.length - 1
      catch {
        case e: IOException => 0
      }
    Files.write(WatchFile.path, (count.toString + s"\n${System.currentTimeMillis}").getBytes);
  }
}

