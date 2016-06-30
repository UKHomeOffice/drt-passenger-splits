package s3

import java.util.zip.ZipInputStream

import akka.actor.ActorSystem
import akka.testkit.TestKit
import awscala.s3.{Bucket, S3, S3Object, S3ObjectSummary}
import core.ZipUtils
import core.ZipUtils.UnzippedFileContent
import org.specs2.mutable.{Specification, SpecificationLike}
import parsing.PassengerInfoParser

import scala.collection.immutable.Iterable
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.Try


//trait ChromaParser extends ChromaConfig with WithSendAndReceive {
////  self: WithSendAndReceive =>
//  implicit val system: ActorSystem
//
//  import system.dispatcher
//
//  import ChromaParserProtocol._
//
//  def log = LoggerFactory.getLogger(getClass)
//
//  def tokenPipeline: HttpRequest => Future[ChromaToken] = (
//    addHeader(Accept(MediaTypes.`application/json`))
//      ~> sendAndReceive
//      ~> unmarshal[ChromaToken]
//    )
//
//  def livePipeline(token: String): HttpRequest => Future[List[ChromaSingleFlight]] = {
//    println(s"Sending request for $token")
//    val logRequest: HttpRequest => HttpRequest = { r => log.debug(r.toString); r }
//    val logResponse: HttpResponse => HttpResponse = {
//      resp =>
//        log.info("Response Object: "  + resp); resp
//    }
//    val logUnMarshalled: List[ChromaSingleFlight] => List[ChromaSingleFlight] = { resp => log.info("Unmarshalled Response Object: " + resp); resp }
//
//    (
//      addHeaders(Accept(MediaTypes.`application/json`), Authorization(OAuth2BearerToken(token)))
//        ~> logRequest
//        ~> sendReceive
//        ~> logResponse
//        ~> unmarshal[List[ChromaSingleFlight]]
//        ~> logUnMarshalled
//      )
//  }
//
//  def currentFlights: Future[Seq[ChromaSingleFlight]] = {
//    val eventualToken: Future[ChromaToken] = tokenPipeline(Post(tokenUrl, chromaTokenRequestCredentials))
//    def eventualLiveFlights(accessToken: String) = livePipeline(accessToken)(Get(url))
//
//    for {
//      t <- eventualToken
//      cr <- eventualLiveFlights(t.access_token)
//    } yield {
//      cr
//    }
//  }
//
//}
class JsonCreationUnitTestsSpec extends Specification {

  import PassengerInfoParser._
  import FlightPassengerInfoProtocol._
  import spray.json._
  import PassengerInfoParser._

  val sampleJson =
    """{
      |  "EventCode": "DC",
      |  "DeparturePortCode": "BRE",
      |  "VoyageNumberTrailingLetter": "",
      |  "ArrivalPortCode": "STN",
      |  "DeparturePortCountryCode": "DEU",
      |  "VoyageNumber": "3631",
      |  "VoyageKey": "517c62be54d6822e33424d0fd7057449",
      |  "ScheduledDateOfDeparture": "2016-03-02",
      |  "ScheduledDateOfArrival": "2016-03-02",
      |  "CarrierType": "AIR",
      |  "CarrierCode": "FR",
      |  "ScheduledTimeOfArrival": "06:00:00",
      |  "PassengerList": [
      |    {
      |      "DocumentIssuingCountryCode": "MAR",
      |      "PersonType": "P",
      |      "DocumentLevel": "Primary",
      |      "Age": "21",
      |      "DisembarkationPortCode": "STN",
      |      "InTransitFlag": "N",
      |      "DisembarkationPortCountryCode": "GBR",
      |      "NationalityCountryEEAFlag": "",
      |      "DocumentType": "P",
      |      "PoavKey": "000d6ab0f4929d8a92a99b83b0c35cfc",
      |      "NationalityCountryCode": "MAR"
      |    }]
      |}""".stripMargin


  "Can parse something" in {
    val parsedAndConverted = sampleJson.parseJson.convertTo[VoyagePassengerInfo]
    parsedAndConverted should beEqualTo(
      VoyagePassengerInfo(EventCodes.DoorsClosed, "STN", "3631", "FR", "2016-03-02", "06:00:00",
        PassengerInfoJson(Some("P"), "MAR", "", Some("21")) :: Nil)
    )
  }
  "Can produce json from a VoyagePassengerInfo" in {
    val vpi = VoyagePassengerInfo(EventCodes.DoorsClosed, "STN", "3631", "FR", "2016-03-02", "06:00:00",
      PassengerInfoJson(Some("P"), "MAR", "", Some("21")) :: Nil)

    vpi.toJson should beEqualTo(
      """{"ScheduledDateOfArrival":"2016-03-02",
        |"EventCode":"DC","PassengerList":[
        | {"DocumentType":"P","DocumentIssuingCountryCode":"MAR","NationalityCountryEEAFlag":"","Age":"21"}],
        | "ScheduledTimeOfArrival":"06:00:00","CarrierCode":"FR","VoyageNumber":"3631","ArrivalPortCode":"STN"
        | }""".stripMargin.parseJson)
  }
//  "Can produce json from a VoyagePassengerSplits" in {
//
//  }
  getClass.getClassLoader.getResourceAsStream("s3content/")
}

class S3IntegrationSpec extends TestKit(ActorSystem())
  with SpecificationLike {
  test =>

  import awscala._

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


