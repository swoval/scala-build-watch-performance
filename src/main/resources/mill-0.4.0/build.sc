import mill._
import mill.scalalib._

object perf extends SbtModule {
  def scalaVersion = "2.12.8"
  def ivyDeps = Agg(
    ivy"com.typesafe.akka::akka-actor:2.5.16",
    ivy"org.apache.spark::spark-sql:2.4.3",
  )

  object test extends Tests {
    def ivyDeps = Agg(
      ivy"org.scalatest::scalatest:3.0.5",
    )
    def testFrameworks = Seq("org.scalatest.tools.Framework")
  }
}
