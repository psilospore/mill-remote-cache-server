package main

import argonaut._
import cats.data.NonEmptyList
import cats.effect.{Blocker, ConcurrentEffect, ContextShift, IO}
import cats.syntax.apply._
import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.services.s3.model.{ObjectMetadata, PutObjectRequest, S3ObjectInputStream, S3ObjectSummary}
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import org.http4s.argonaut._

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.util.Try


/**
 * TODO
 * - configs
 * - other caching implementations GoogleCloudCacheService, LocalCacheService, etc
 */

import main.CacheService._

trait CacheService[F[_]] {

  //TODO Either
  def upload(pathFrom: PathFrom, hashCode: Hash, stream: fs2.Stream[F, Byte]): F[Boolean]

  def get(pathFrom: PathFrom, hashCode: Hash): fs2.Stream[F, Byte]

  def cached(): F[Cached]
}

object CacheService {
  type PathFrom = String
  type Hash = Int
  type ErrorOr[V] = Either[NonEmptyList[String], V] //TODO use this
  type ErrorOrUnit = ErrorOr[Unit]

  case class Cached(hashesAvailable: Map[PathFrom, List[Hash]])

  implicit val cachedJsonEncoder = CodecJson.casecodec1(Cached.apply, Cached.unapply)("hashesAvailable").Encoder
  implicit val cachedEntityEncoder = jsonEncoderOf[IO, Cached]
}

class AmazonCacheService(client: AmazonS3)(implicit c: ConcurrentEffect[IO], cs: ContextShift[IO]) extends CacheService[IO] {

  import AmazonCacheService._

  //  def initialize(): IO[ErrorOr[Unit]] = TODO create bucket if doesn't exist

  override def cached(): IO[Cached] = {
    IO.shift *> IO {
      val res = client.listObjects(BUCKETNAME)
      println(s"S3 Response: $res")
      val objects: List[S3ObjectSummary] = res.getObjectSummaries().asScala.toList
      Cached(
        objects.map(_.getKey.split("-").toList match {
          case pathL :+ hash => (pathL.mkString("/"), hash)
        }).groupMap(_._1)(_._2.toInt)
      )


    }
  }

  override def get(pathFrom: PathFrom, hashCode: Hash): fs2.Stream[IO, Byte] = {
    val objectContentIO: IO[S3ObjectInputStream] = IO.shift *> IO {
      val res = client.getObject(BUCKETNAME, s"$pathFrom/$hashCode") //TODO use ErrorOr in case of failure
      println(s"Successfully fetched $pathFrom/$hashCode")
      println(s"S3 Response: $res")
      res.getObjectContent
    }
    fs2.io.readInputStream[IO](objectContentIO, 1000, Blocker.liftExecutionContext(ExecutionContext.global))
  }

  override def upload(pathFrom: PathFrom, hashCode: Hash, stream: fs2.Stream[IO, Byte]): IO[Boolean] = {
    fs2.io.toInputStreamResource(stream).use(s => {
      val o = new ObjectMetadata()
      o.setContentEncoding("gzip")
      o.setContentLength(o.getContentLength)
      val putObjectRequest = new PutObjectRequest(
        "cache",
        s"pathFrom/$hashCode",
        s,
        o
      )

      IO.shift *> IO {
        Try {
          val res = client.putObject(putObjectRequest)
          println(s"Successfully uploaded $pathFrom/$hashCode")
          println(s"S3 Response: $res")
        } isSuccess
      }
    })
  }
}

object AmazonCacheService {
  val BUCKETNAME = "millcache" //TODO config

  def createClient: IO[AmazonS3] = {
    IO { //TODO read from config
      val credentials = new AWSStaticCredentialsProvider(new BasicAWSCredentials("TODO", "TODO"))
      AmazonS3ClientBuilder.standard().withRegion("us-east-1").withCredentials(credentials).build()
    }
  }

}

