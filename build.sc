import mill._, scalalib._, scalafmt._

trait CommonModule extends ScalaModule with ScalafmtModule  {
  def scalaVersion = "2.13.0"
  override def scalacOptions = Seq("-Ypartial-unification")
}

object remoteCacheServer extends CommonModule{
  val http4sVersion = "0.21.0-M5"
  override def ivyDeps = Agg(
    ivy"org.http4s::http4s-dsl:${http4sVersion}"
  )
}
