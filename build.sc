import $ivy.`com.disneystreaming.smithy4s::smithy4s-mill-codegen-plugin::0.18.21`
import smithy4s.codegen.mill._

import mill._, mill.scalalib._

object Ver {
  val scala   = "3.4.2"
  val http4s  = "0.23.27"
  val decline = "2.4.1"
  val logback = "1.5.6"
}

object cfddns extends RootModule with ScalaModule with Smithy4sModule {
  def scalaVersion   = Ver.scala
  def publishVersion = "0.1.0"
  def scalacOptions  = Seq("-deprecation", "-Wunused:all", "-release", "11")

  override def ivyDeps = Agg(
    ivy"com.monovore::decline-effect:${Ver.decline}",
    ivy"com.disneystreaming.smithy4s::smithy4s-core:${smithy4sVersion()}",
    ivy"com.disneystreaming.smithy4s::smithy4s-http4s:${smithy4sVersion()}",
    ivy"org.http4s::http4s-ember-client:${Ver.http4s}",
    ivy"ch.qos.logback:logback-classic:${Ver.logback}",
  )
}
