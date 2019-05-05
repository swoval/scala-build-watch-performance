import mill._
import mill.scalalib._

object AkkaTest extends SbtModule {
  def scalaVersion = "2.12.8"
  def ivyDeps = Agg(ivy"com.typesafe.akka::akka-actor:2.5.16")

  object test extends Tests {
    def ivyDeps = Agg(
      ivy"org.scalatest::scalatest:3.0.5",
    )
    def testFrameworks = Seq("org.scalatest.tools.Framework")
  }
}
