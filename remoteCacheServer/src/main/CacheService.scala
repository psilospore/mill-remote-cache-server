package main

import cats.Monad
import cats.effect.{ConcurrentEffect, ContextShift, IO}
import com.amazonaws.services.s3.model.{ObjectMetadata, PutObjectRequest}
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import cats.syntax.apply._

import scala.util.Try

/**
 * TODO
 * - configs
 * - other caching implementations GoogleCloudCacheService, LocalCacheService, etc
 */


trait CacheService[F[_]] {
  type PathFrom = String
  type Hash = Int

  case class Cached(hashesAvailable: Map[PathFrom, Set[Hash]])

  def upload(pathFrom: PathFrom, hashCode: Hash, stream: fs2.Stream[IO, Byte]): F[Boolean]

  def cached(): F[Cached]
}

class AmazonCacheService(client: AmazonS3)(implicit c: ConcurrentEffect[IO], cs: ContextShift[IO]) extends CacheService[IO] {
  import AmazonCacheService._

  //TODO just storing this in a mutable variable for now. For S3 we would just get all keys in bucket and populate.
  var cached = Cached(Map())

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
  //TODO for now just storing this. For S3 we would just get all keys in bucket and populate.

  def createClient: IO[AmazonS3] = {
    IO { //TODO read from config
      val credentials = new AWSStaticCredentialsProvider(new BasicAWSCredentials("TODO", "TODO"))
      AmazonS3ClientBuilder.standard().withRegion("us-east-1").withCredentials(credentials).build()
    }
  }

}

