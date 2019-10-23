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

  object HashMatcher extends OptionalQueryParamDecoderMatcher[Int]("hash")

  object PathMatcher extends OptionalQueryParamDecoderMatcher[String]("path")

  override def run(args: List[String]): IO[ExitCode] = {
    getDefaultCacheService
      .flatMap(cacheService => {
        val routes = HttpRoutes.of[IO] {
          case GET -> Root / "cached" :? PathMatcher(path) +& HashMatcher(hash) =>
            (for {
              p <- path
              h <- hash
            } yield Ok(cacheService.get(p, h))) getOrElse Ok(cacheService.cached())
          case req@PUT -> Root / "cached" :? PathMatcher(path) +& HashMatcher(hash) =>
            (for {
              p <- path
              h <- hash
            } yield Ok(cacheService.upload(p, h, req.body) void)) getOrElse BadRequest()
        } orNotFound

        BlazeServerBuilder[IO]
          .bindHttp(7000, "localhost")
          .withHttpApp(routes)
          .serve
          .compile
          .drain
          .as(ExitCode.Success)
      }
      )
  }

  //TODO do something else
  def getDefaultCacheService: IO[CacheService[IO]] = AmazonCacheService.createClient.map(new AmazonCacheService(_))
}
