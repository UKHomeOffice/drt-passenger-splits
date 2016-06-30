package api

import akka.actor.{ActorSystem, ActorRef}
import akka.event.Logging
import akka.pattern.AskableActorRef
import akka.util.Timeout
import com.amazonaws.services.dynamodbv2.xspec.N
import core.PassengerInfoRouterActor._
import core.PassengerQueueTypes.PaxTypeAndQueueCount
import core.PassengerSplitsCalculator.PaxSplits
import core.RegistrationActor.{Register, NotRegistered, Registered}
import core.User
import parsing.PassengerInfoParser.VoyagePassengerInfo
import spray.http.{DateTime, StatusCodes, StatusCode}
import spray.json._
import spray.routing.Directives
import spray.http._
import spray.routing.Directives
import scala.concurrent.{Future, ExecutionContext}
import core.{User, RegistrationActor}
import akka.util.Timeout
import RegistrationActor._
import spray.http._
import core.User
import core.RegistrationActor.Register
import scala.Some
import scala.util.matching.Regex.Match
import scala.util.{Try, Success, Failure}

trait FlightPassengerSplitsReportingServiceJsonFormats {
  object ReportingJsonProtocol extends DefaultJsonProtocol {

    implicit object DateTimeJsonFormat extends JsonFormat[DateTime] {
      def read(json: JsValue) = ???

      def write(c: DateTime) = JsString(c.toIsoDateTimeString)
    }

//    implicit object FlightInfoJsonFormat extends JsonWriter[VoyagePaxSplits] { def write(obj: VoyagePaxSplits) = JsString("hellobob") }

    implicit object FlightPaxSplitsInfoJsonFormat extends JsonWriter[PaxSplits] {
      def write(obj: PaxSplits) = JsString("hellobobsplit")
    }
    implicit val paxTypeAndQueueFormat = jsonFormat3(PaxTypeAndQueueCount)
    implicit val voyagePaxSplitsFormat: RootJsonFormat[VoyagePaxSplits] = jsonFormat6(VoyagePaxSplits)

  }
}

object FlightPassengerSplitsReportingServiceJsonFormats extends FlightPassengerSplitsReportingServiceJsonFormats

class FlightPassengerSplitsReportingService(system: ActorSystem, aggregation: ActorRef)(implicit executionContext: ExecutionContext)
  extends Directives with DefaultJsonFormats with FlightPassengerSplitsReportingServiceJsonFormats {
  val log = Logging(system, classOf[FlightPassengerSplitsReportingService])

  case class ImageUploaded(size: Int)

  //  import reflect.ClassTag
  import parsing.PassengerInfoParser
  import spray.json._
  import akka.pattern.ask
  import FlightPassengerSplitsReportingService._

  import scala.concurrent.duration._

  implicit val timeout = Timeout(2.seconds)

  implicit val userFormat = jsonFormat4(User)
  implicit val registerFormat = jsonFormat1(Register)
  implicit val registeredFormat = jsonObjectFormat[Registered.type]
  implicit val notRegisteredFormat = jsonObjectFormat[NotRegistered.type]
  implicit val imageUploadedFormat = jsonFormat1(ImageUploaded)

  implicit object EitherErrorSelector extends ErrorSelector[NotRegistered.type] {
    def apply(v: NotRegistered.type): StatusCode = StatusCodes.BadRequest
  }


  import PassengerInfoParser.FlightPassengerInfoProtocol._
  import ReportingJsonProtocol._

  val flightCodeRe = """\w{2,3}\d+""".r
  val route =
    path("flight" / Segment) {
      (flightCode) =>
        get {
          complete((aggregation ? ReportFlightCode(flightCode)).mapTo[List[VoyagePassengerInfo]]
            .map {
              _.map(_.toJson).mkString("[", ",", "]")
            }
          )
        }
    } ~
      path("flight-pax-splits" / "dest-" ~ "STN".r / "terminal-" ~ "\\w+".r /
        flightCodeRe / "scheduled-arrival-time-" ~ """\d{8}T\d{4}""".r) {
        (destPort, terminalName, flightCode, arrivalTime) =>
          get {
            println(s"Got request! $destPort, $terminalName, $flightCode")
            val time: Option[DateTime] = parseUrlDateTime(arrivalTime)
            time match {
              case Some(t) =>
                onComplete(calculateSplits(aggregation)(destPort, terminalName, flightCode, t)) {
                  case Success(value: List[VoyagePaxSplits]) =>
                    log.info(s"Got some value ${value}")
                    complete(value.toJson.prettyPrint)
//                    complete(
//                      """
//                        |[{
//                        | "destinationPort": "STN",
//                        | "flightNumber": "934",
//                        | "carrier": "RY",
//                        | "scheduledArrival": "2015-02-01T13:48:00",
//                        | "totalPax": 1,
//                        | "paxSplit": [
//                        |     {"paxType": "eea-machine-readable", "queueType": "eea-desk", "numberOfPax": 1}
//                        | ]
//                        |}]
//                      """.stripMargin)
                  case Success(any) => failWith(new Exception("Unexpected result" + any))
                  case Failure(ex) => complete("boo!")
                }
              case None =>
                failWith(new Exception(s"Bad nearly ISO datetime ${arrivalTime}"))
            }
          }
      }
}


object FlightPassengerSplitsReportingService {
  def parseUrlDateTime(notQuiteIsoDatetime: String) = {
    val dateTimeRe = """(\d\d\d\d)(\d\d)(\d\d)T(\d\d)(\d\d)""".r
    val matches: Option[Match] = dateTimeRe.findFirstMatchIn(notQuiteIsoDatetime)
    matches match {
      case Some(reMatch) =>
        val isoDt = s"${reMatch.group(1)}-${reMatch.group(2)}-${reMatch.group(3)}T${reMatch.group(4)}:${reMatch.group(5)}:00"
        DateTime.fromIsoDateTimeString(isoDt)
      case None => None
    }
  }

  def calculateSplits(aggregator: AskableActorRef)
                     (destPort: String, terminalName: String, flightCode: String, arrivalTime: DateTime)(implicit timeout: Timeout, ec: ExecutionContext) = {
    val ccAndFnOpt = getCarrierCodeAndFlightNumber(flightCode)
    ccAndFnOpt match {
      case Some((cc, fn)) => aggregator ? ReportVoyagePaxSplit(cc, fn, arrivalTime)
      case None => Future.failed(new Exception(s"couldn't get carrier and voyage number from $flightCode"))
    }
  }

  val flightCodeRe = """(\w{2})(\d{1,5})""".r("carrierCode", "voyageNumber")

  def getCarrierCodeAndFlightNumber(flightCode: String) = {
    flightCodeRe.findFirstMatchIn(flightCode) match {
      case Some(matches) => Some((matches.group("carrierCode"), matches.group("voyageNumber")))
      case None => None
    }
  }
}
