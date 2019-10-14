package main

import cats.effect._
import cats.implicits._
import cats.kernel.instances.hash
import org.http4s.{EntityEncoder, HttpRoutes, QueryParamDecoder}
import org.http4s.syntax._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.blaze._
import main._
import org.http4s.server.middleware._
import org.http4s.argonaut._


object Server extends IOApp {
  object HashMatcher extends QueryParamDecoderMatcher[Int]("hash")

  override def run(args: List[String]): IO[ExitCode] = {
    val cacheService = PrintDebugCacheService
//    getDefaultCacheService
//      .flatMap(cacheService => {
      val routes = HttpRoutes.of[IO] {
        case GET -> Root / "cached" => Ok(
          cacheService.cached()
        )
        case GET -> Root / "cached" / pathFrom :? HashMatcher(hash) => Ok(
          cacheService.get(pathFrom, hash)
        )
        case req @ PUT -> Root / "cached" / pathFrom :? HashMatcher(hash) => Ok(
          cacheService.upload(pathFrom, hash, req.body).void
        )
      } orNotFound

      BlazeServerBuilder[IO]
        .bindHttp(7000, "localhost")
        .withHttpApp(routes)
        .serve
        .compile
        .drain
        .as(ExitCode.Success)
    }
//  )
//  }

  //TODO do something else
  def getDefaultCacheService: IO[CacheService[IO]] = AmazonCacheService.createClient.map(new AmazonCacheService(_))
}
