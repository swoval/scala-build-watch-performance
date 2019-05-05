package sbt.benchmark

import org.scalatest.{ FlatSpec, Matchers }

class AkkaPerfTest extends FlatSpec {
  "Run" should "exit" in {
    AkkaMain.main(Array.empty[String])
    assert(true)
  }
}
