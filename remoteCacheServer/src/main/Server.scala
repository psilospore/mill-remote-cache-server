package main

import cats.effect._
import cats.implicits._
import cats.kernel.instances.hash
import org.http4s.{HttpRoutes, QueryParamDecoder}
import org.http4s.syntax._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.blaze._
import main._
import org.http4s.server.middleware._

class Server extends IOApp {
  object HashMatcher extends QueryParamDecoderMatcher[Int]("hash")

  override def run(args: List[String]): IO[ExitCode] = {
    getDefaultCacheService.flatMap(cacheService => {
      val routes = GZip(HttpRoutes.of[IO] {
        case GET -> Root / "cached" / pathFrom :? HashMatcher(hash) => Ok(

        )
        case req @ PUT -> Root / "cached" / pathFrom :? HashMatcher(hash) => Ok(
          cacheService.upload(pathFrom, hash, req.body).void
        )
      } orNotFound)

      BlazeServerBuilder[IO]
        .bindHttp(8080, "localhost")
        .withHttpApp(routes)
        .serve
        .compile
        .drain
        .as(ExitCode.Success)
    })
  }

  //TODO clean later
  def getDefaultCacheService: IO[CacheService[IO]] = AmazonCacheService.createClient.map(new AmazonCacheService(_))
}
