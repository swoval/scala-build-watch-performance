package sbt.benchmark

import org.scalatest.{ FlatSpec, Matchers }
import utest._

object AkkaPerfTest extends TestSuite {
  val tests = Tests {
    'run - {
      AkkaMain.main(Array.empty[String])
      1 ==> 1
    }
  }
}
