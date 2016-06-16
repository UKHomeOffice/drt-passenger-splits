package core

import java.nio.charset.StandardCharsets
import java.nio.charset.StandardCharsets._
import java.util.zip.{ZipEntry, ZipInputStream}

import akka.actor.ActorSystem
import akka.testkit.TestKit
import awscala.s3.{S3Object, S3ObjectSummary, Bucket, S3}
import com.amazonaws.services.s3.model.S3ObjectInputStream
import core.ChromaParser.{ChromaSingleFlight, ChromaToken, ChromaParserProtocol}
import core.PassengerInfoParser.PassengerInfo
import core.ZipUtils.UnzippedFileContent
import http.WithSendAndReceive
import org.slf4j.LoggerFactory
import org.specs2.mutable.SpecificationLike
import spray.client.pipelining._
import spray.http.HttpHeaders.{Accept, Authorization}
import spray.http.{HttpResponse, HttpRequest, MediaTypes, OAuth2BearerToken}
import spray.httpx.SprayJsonSupport._
import spray.json.DefaultJsonProtocol

import scala.collection.immutable.Iterable
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Success, Try}

object ChromaParser {

  case class ChromaToken(access_token: String, token_type: String, expires_in: Int)

  case class ChromaSingleFlight(Operator: String,
                                Status: String,
                                EstDT: String,
                                ActDT: String,
                                EstChoxDT: String,
                                ActChoxDT: String,
                                Gate: String,
                                Stand: String,
                                MaxPax: Int,
                                ActPax: Int,
                                TranPax: Int,
                                RunwayID: String,
                                BaggageReclaimId: String,
                                FlightID: Int,
                                AirportID: String,
                                Terminal: String,
                                ICAO: String,
                                IATA: String,
                                Origin: String,
                                SchDT: String)

  object ChromaParserProtocol extends DefaultJsonProtocol {
    implicit val chromaTokenFormat = jsonFormat3(ChromaToken)
    implicit val chromaSingleFlightFormat = jsonFormat20(ChromaSingleFlight)
  }

}

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
import ChromaParserProtocol._
import PassengerInfoParser._

class ChromaParserSpec extends
  SpecificationLike {


  //  sequential
  "Parse chroma response" >> {
    "Given a chroma response list with a single Flight we can parse it" in {
      import spray.json._
      val chromaFlightJson: JsValue =
        """
          |[{
          |"Operator":"Flybe"
          	 |,"Status":"On Chocks"
          	 |,"EstDT":"2016-06-02T10:55:00Z"
          	 |,"ActDT":"2016-06-02T10:55:00Z"
          	 |,"EstChoxDT":"2016-06-02T11:01:00Z"
          	 |,"ActChoxDT":"2016-06-02T11:05:00Z"
          	 |,"Gate":"46"
          	 |,"Stand":"44R"
          	 |,"MaxPax":78
          	 |,"ActPax":51
          	 |,"TranPax":0
          	 |,"RunwayID":"05L"
          	 |,"BaggageReclaimId":"05"
          	 |,"FlightID":14710007
          	 |,"AirportID":"MAN"
          	 |,"Terminal":"T3"
          	 |,"ICAO":"BEE1272"
          	 |,"IATA":"BE1272"
          	 |,"Origin":"AMS"
          	 |,"SchDT":"2016-06-02T09:55:00Z"}]
        """.stripMargin.parseJson

      chromaFlightJson.convertTo[List[ChromaSingleFlight]] should beEqualTo(List(
        ChromaSingleFlight("Flybe",
          "On Chocks",
          "2016-06-02T10:55:00Z",
          "2016-06-02T10:55:00Z",
          "2016-06-02T11:01:00Z",
          "2016-06-02T11:05:00Z",
          "46",
          "44R",
          78,
          51,
          0,
          "05L",
          "05",
          14710007,
          "MAN",
          "T3",
          "BEE1272",
          "BE1272",
          "AMS",
          "2016-06-02T09:55:00Z")))
    }
    //    {
    //      import spray.json._
    //      val chromaFlightJson: JsValue =
    //        """
    //          |[{
    //          |"Operator":"Flybe"
    //          	 |,"Status":"On Chocks"
    //          	 |,"EstDT":"2016-06-02T10:55:00Z"
    //          	 |,"ActDT":"2016-06-02T10:55:00Z"
    //          	 |,"EstChoxDT":"2016-06-02T11:01:00Z"
    //          	 |,"ActChoxDT":"2016-06-02T11:05:00Z"
    //          	 |,"Gate":"46"
    //          	 |,"Stand":"44R"
    //          	 |,"MaxPax":78
    //          	 |,"ActPax":51
    //          	 |,"TranPax":0
    //          	 |,"RunwayID":"05L"
    //          	 |,"BaggageReclaimId":"05"
    //          	 |,"FlightID":14710007
    //          	 |,"AirportID":"MAN"
    //          	 |,"Terminal":"T3"
    //          	 |,"ICAO":"BEE1272"
    //          	 |,"IATA":"BE1272"
    //          	 |,"Origin":"AMS"
    //          	 |,"SchDT":"2016-06-02T09:55:00Z"}]
    //        """.stripMargin.parseJson
    //
    //      chromaFlightJson.convertTo[List[PassengerInfo]] should beEqualTo(List(
    //        ChromaSingleFlight("Flybe",
    //          "On Chocks",
    //          "2016-06-02T10:55:00Z",
    //          "2016-06-02T10:55:00Z",
    //          "2016-06-02T11:01:00Z",
    //          "2016-06-02T11:05:00Z",
    //          "46",
    //          "44R",
    //          78,
    //          51,
    //          0,
    //          "05L",
    //          "05",
    //          14710007,
    //          "MAN",
    //          "T3",
    //          "BEE1272",
    //          "BE1272",
    //          "AMS",
    //          "2016-06-02T09:55:00Z")))
    //    }
  }
  isolated
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

  "We can connect to the S3 bucket" >> {
    println("starting")
    val bucketName: String = "drt-deveu-west-1"
    val result = Bucket(bucketName)
    val toList: Stream[Either[String, S3ObjectSummary]] = result.ls("")
    val objects: Stream[Future[Option[(String, List[UnzippedFileContent])]]] = toList collect {
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



    import spray.json._
    import PassengerInfoParser._
    import FlightPassengerInfoProtocol._

    val res: Stream[Future[List[(String, Try[Iterable[(String, List[PassengerInfo], Int)]])]]] = objects.map { future =>
      val futureMapped = future collect {

        case Some(l) => l._2 map { file =>
          println("=" * 80)
          val zipFileName: String = l._1
          println(zipFileName)
          val zipEntryFileName: String = file.filename
          println(zipEntryFileName)
          val parsed: JsValue = file.content.parseJson
          val triedPassengerInfoResponse: Try[FlightPassengerInfoResponse] = Try(parsed.convertTo[FlightPassengerInfoResponse])
          (zipFileName + ":" + zipEntryFileName -> triedPassengerInfoResponse.map { passengerInfoResponse =>
            val passengerListByCountryCode = passengerInfoResponse.PassengerList.groupBy(_.DocumentIssuingCountryCode)
            passengerListByCountryCode.map(pair => (pair._1, pair._2, pair._2.length))
          })
        }
      }
      futureMapped
    }
    println(Await.ready(Future.sequence(res.take(5)), Duration.Inf).value.mkString("\n"))
    false
  }


  val passengerInfo =
    """

    """.stripMargin
}


