package main

import java.io.ByteArrayInputStream

import argonaut._
import cats.data.NonEmptyList
import cats.effect.{Blocker, ConcurrentEffect, ContextShift, IO}
import cats.syntax.apply._
import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.services.s3.model.{ObjectMetadata, PutObjectRequest, S3ObjectInputStream, S3ObjectSummary}
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import com.amazonaws.util.Base32
import org.http4s.argonaut._

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.io.Source
import scala.util.{Failure, Try}

/**
 * TODO
 * - configs
 * - other caching implementations GoogleCloudCacheService, LocalCacheService, etc
 * - combine caching
 */

import main.CacheService._

trait CacheService[F[_]] {
  def upload(pathFrom: PathFrom, hashCode: Hash, stream: fs2.Stream[F, Byte]): F[Boolean]

  def get(pathFrom: PathFrom, hashCode: Hash): fs2.Stream[F, Byte]

  def cached(): F[Cached]
}

object CacheService {
  type PathFrom = String
  type Hash = BigInt
  type ErrorOr[V] = Either[NonEmptyList[String], V] //TODO use this for better validation
  type ErrorOrUnit = ErrorOr[Unit]

  case class Cached(hashesAvailable: Map[PathFrom, List[Hash]])

  implicit val cachedJsonCodec = CodecJson.casecodec1(Cached.apply, Cached.unapply)("hashesAvailable")
  implicit val cachedEntityEncoder = jsonEncoderOf[IO, Cached]
  implicit val cachedEntityDecoder = jsonOf[IO, Cached]

}

class AmazonCacheService(client: AmazonS3)(implicit c: ConcurrentEffect[IO], cs: ContextShift[IO]) extends CacheService[IO] {

  import AmazonCacheService._

  //  def initialize(): IO[ErrorOr[Unit]] = TODO if first time ever running: create bucket if doesn't exist with correct permissions

  override def cached(): IO[Cached] = {
    IO.shift *> (IO {
      val res = client.listObjects(BUCKETNAME)
      val objects: List[S3ObjectSummary] = res.getObjectSummaries.asScala.toList
      val cached1 = Cached(
        objects.map(_.getKey.split("/").toList match {
          case pathL :+ encodedHash => (pathL.mkString("/"), BigInt(Base32.decode(encodedHash)))
        }).groupMap(_._1)(_._2.toInt)
      )
      println(cached1)
      cached1

    }).redeem(x => {x.printStackTrace(); Cached(Map())} , x => x) //TODO delete
  }

  override def get(pathFrom: PathFrom, hashCode: Hash): fs2.Stream[IO, Byte] = {
    val objectContentIO: IO[S3ObjectInputStream] = IO.shift *> IO {
      val res = client.getObject(BUCKETNAME, s"$pathFrom/${Base32.encodeAsString(hashCode.toByteArray: _*)}") //TODO use ErrorOr in case of failure
      println(s"Successfully fetched $pathFrom-$hashCode")
      println(s"S3 Response: $res")
      res.getObjectContent
    }
    fs2.io.readInputStream[IO](objectContentIO, 1000, Blocker.liftExecutionContext(ExecutionContext.global))
  }

  override def upload(pathFrom: PathFrom, hashCode: Hash, stream: fs2.Stream[IO, Byte]): IO[Boolean] = {
    //TODO IO.shift ?
    stream.compile.toVector.map(b => {
      val o = new ObjectMetadata()
      o.setContentEncoding("gzip")
      o.setContentType("application/octet-stream")
      o.setContentLength(b.size)
      Try {
        val putObjectRequest = new PutObjectRequest(
          BUCKETNAME,
          s"$pathFrom/${Base32.encodeAsString(hashCode.toByteArray: _*)}",
          new ByteArrayInputStream(b.toArray),
          o
        )
        println("before putobject")
        val res = Try {
          client.putObject(putObjectRequest)
        } //It would probably be rare to override something but we could check first
        println(s"Successfully uploaded $pathFrom/$hashCode")
        println(s"S3 Response: $res")
      } match {
        case Failure(exception) => {
          exception.printStackTrace;
          false
        }
        case _ => true
      }
    })
  }
}

object AmazonCacheService {
  val BUCKETNAME = "millcache" //TODO get from config config

  def createClient: IO[AmazonS3] = { //TODO make this better also config
    IO(Source.fromFile(s"${System.getProperty("user.home")}/.aws/credentials")).bracket { source =>
      IO({
        val lines: List[String] = source.getLines().toList
        val keyRegex = "aws_access_key_id = (.*)".r
        val secretRegex = "aws_secret_access_key = (.*)".r
        val credentials = lines match {
          case _ :: keyRegex(k) :: secretRegex(s) :: Nil => new AWSStaticCredentialsProvider(new BasicAWSCredentials(k, s))
        }
        AmazonS3ClientBuilder.standard().withRegion("us-east-1").withCredentials(credentials).build()
      })
    } { source =>
      // Releasing the reader (the finally block)
      IO(source.close())
    }
  }

}

//For printline debugging. Just print requests I'm getting.
object PrintDebugCacheService extends CacheService[IO] {
  override def upload(pathFrom: PathFrom, hashCode: Hash, stream: fs2.Stream[IO, Byte]): IO[Boolean] = {
    println(s"PUT request received for $pathFrom hash $hashCode stream length")
    stream.take(40).compile.toList.map(println).map({ _ => true })
  }

  override def get(pathFrom: PathFrom, hashCode: Hash): fs2.Stream[IO, Byte] = {
    println(s"GET request received for $pathFrom hash $hashCode")
    fs2.Stream.apply[IO, Byte]()
  }

  override def cached(): IO[Cached] = {
    println(s"Get cached request received")
    IO.pure(Cached(Map()))
  }
}

