import mill._, scalalib._, scalafmt._

trait CommonModule extends ScalaModule with ScalafmtModule  {
  def scalaVersion = "2.13.0"
  override def scalacOptions = Seq("-language:postfixOps")
}

object remoteCacheServer extends CommonModule{
  val http4sVersion = "0.21.0-M5"
  val amazonVersion = "1.11.292"

  override def ivyDeps = Agg(
    ivy"org.http4s::http4s-dsl:$http4sVersion",
    ivy"org.http4s::http4s-blaze-server:$http4sVersion",
    ivy"org.http4s::http4s-blaze-client:$http4sVersion",
    ivy"org.http4s::http4s-argonaut:$http4sVersion",
    ivy"com.amazonaws:aws-java-sdk-core:$amazonVersion",
    ivy"com.amazonaws:aws-java-sdk-s3:$amazonVersion"
  )
}
