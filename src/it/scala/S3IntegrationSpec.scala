package s3

import java.util.zip.ZipInputStream

import akka.actor.ActorSystem
import akka.testkit.TestKit
import awscala.s3.{Bucket, S3, S3Object, S3ObjectSummary}
import core.{CoreActors, ZipUtils}
import core.ZipUtils.UnzippedFileContent
import org.specs2.mutable.{Specification, SpecificationLike}
import org.specs2.specification.AfterAll
import parsing.PassengerInfoParser

import scala.collection.immutable.Iterable
import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, Future}
import scala.util.Try


class AtmosIntegrationSpec extends TestKit(ActorSystem())
  with SpecificationLike
  with AfterAll {
  test =>
  def afterAll(): Unit = system.terminate()

  "This is a manual test of the AtmosPoller to ensure we can connect, open and parse files "   >> {
    val polled = new CoreActors with SimpleAtmosPoller {
      implicit def system: ActorSystem = test.system
    }
    Await.result(polled.streamAllThisToPrintln, FiniteDuration(400, SECONDS))
    false
  }
}


class S3IntegrationSpec extends TestKit(ActorSystem())
  with SpecificationLike
  with AfterAll {
  test =>

  import awscala._

  def afterAll(): Unit = system.terminate()

  implicit val region = Region.EU_WEST_1
  //  val credentials = BasicCredentialsProvider(S3Secrets.accessKeyId, S3Secrets.s3Secret)
  implicit val s3 = S3()(region)
  //  implicit val s3 = S3(credentials)(region)
  //  val bucket: Bucket = s3.createBucket("unique-name-xxx")
  //  bucket.put("sample.txt", new java.io.File("sample.txt"))

  //  import ChromaParser.ChromaParserProtocol._
  //  import scala.concurrent.ExecutionContext.Implicits.global
  import system.dispatcher

  "This is a manual test to help us drive out how to connect to the s3 bucket, " +
    "and unzip it's contents. " >> {
    "We can connect to the S3 bucket" >> {
      println("starting")
      val bucketName: String = "drt-deveu-west-1"
      val result = Bucket(bucketName)
      val toList: Stream[Either[String, S3ObjectSummary]] = result.ls("")
      val objects: Stream[Future[Option[(String, Stream[UnzippedFileContent])]]] = toList collect {
        //      case Left(keyName) => println("key:", keyName)
        case Right(so) =>
          Future {
            val obj: Option[S3Object] = result.get(so.getKey)
            //          println("obj is ", obj)
            obj.map(_.getObjectContent).map {
              zippedFileStream =>
                val unzippedStream: ZipInputStream = new ZipInputStream(zippedFileStream)

                (so.getKey, ZipUtils.unzipAllFilesInStream(unzippedStream))
            }
          }
      }


      import PassengerInfoParser._
      import FlightPassengerInfoProtocol._
      import spray.json._

      val res: Stream[Future[Stream[(String, Try[Iterable[(String, List[PassengerInfoJson], Int)]])]]] = objects.map { future =>
        val futureMapped = future collect {

          case Some(l) => l._2 map { file =>
            println("=" * 80)
            val zipFileName: String = l._1
            println(zipFileName)
            val zipEntryFileName: String = file.filename
            println(zipEntryFileName)
            val parsed: JsValue = file.content.parseJson
            val triedPassengerInfoResponse: Try[VoyagePassengerInfo] = Try(parsed.convertTo[VoyagePassengerInfo])
            (zipFileName + ":" + zipEntryFileName -> triedPassengerInfoResponse.map { passengerInfo =>
              val passengerListByCountryCode = passengerInfo.PassengerList.groupBy(_.DocumentIssuingCountryCode)
              passengerListByCountryCode.map(pair => (pair._1, pair._2, pair._2.length))
            })
          }
        }
        futureMapped
      }
      println(Await.ready(Future.sequence(res.take(5)), Duration.Inf).value.mkString("\n"))
      true
    }
  }
}


