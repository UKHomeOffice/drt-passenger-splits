package api

import akka.actor.{ActorRef, ActorSystem}
import akka.event.Logging
import akka.pattern.AskableActorRef
import akka.util.Timeout
import core.PassengerInfoRouterActor._
import core.PassengerQueueTypes.PaxTypeAndQueueCount
import core.PassengerSplitsCalculator.PaxSplits
import parsing.PassengerInfoParser.VoyagePassengerInfo
import spray.http._
import spray.json._
import spray.routing.Directives
import spray.http._
import spray.routing.Directives
import scala.concurrent.{Future, ExecutionContext}
import akka.util.Timeout
import spray.http._
import core.User
import scala.Some
import scala.util.matching.Regex.Match
import scala.util.{Failure, Success}

trait FlightPassengerSplitsReportingServiceJsonFormats {

  object ReportingJsonProtocol extends DefaultJsonProtocol {

    implicit object DateTimeJsonFormat extends JsonFormat[DateTime] {
      def read(json: JsValue) = ???

      def write(c: DateTime) = JsString(c.toIsoDateTimeString)
    }

    //    implicit object FlightInfoJsonFormat extends JsonWriter[VoyagePaxSplits] { def write(obj: VoyagePaxSplits) = JsString("hellobob") }

    implicit val paxTypeAndQueueFormat = jsonFormat3(PaxTypeAndQueueCount)
    implicit val voyagePaxSplitsFormat: RootJsonFormat[VoyagePaxSplits] = jsonFormat6(VoyagePaxSplits)

  }

}

object FlightPassengerSplitsReportingServiceJsonFormats extends FlightPassengerSplitsReportingServiceJsonFormats

class FlightPassengerSplitsReportingService(system: ActorSystem, flightInfoPaxSplitActor: ActorRef)(implicit executionContext: ExecutionContext)
  extends Directives with DefaultJsonFormats with FlightPassengerSplitsReportingServiceJsonFormats {
  val log = Logging(system, classOf[FlightPassengerSplitsReportingService])

  case class ImageUploaded(size: Int)

  //  import reflect.ClassTag
  import parsing.PassengerInfoParser._
  import FlightPassengerInfoProtocol._
  import FlightPassengerSplitsReportingService._
  import akka.pattern.ask
  import spray.json._

  import scala.concurrent.duration._

  implicit val timeout = Timeout(2.seconds)


  import ReportingJsonProtocol._

  val flightCodeRe = """\w{2,3}\d+""".r
  val portRe = """\w{2,3}""".r
  val route =
    path("flight" / flightCodeRe) {
      (flightCode) =>
        get {
          onComplete(flightInfoPaxSplitActor ? ReportFlightCode(flightCode)) {
            case Success(s) => complete(s.asInstanceOf[List[VoyagePassengerInfo]].toJson.prettyPrint)
          }
        }
    } ~
      path("flight-pax-splits" / "dest-" ~ portRe / "terminal-" ~ "\\w+".r /
        flightCodeRe / "scheduled-arrival-time-" ~ """\d{8}T\d{4}""".r) {
        (destPort, terminalName, flightCode, arrivalTime) =>
          get {
            log.info(s"GET flight-pax-splits $destPort, $terminalName, $flightCode, $arrivalTime")
            val time: Option[DateTime] = parseUrlDateTime(arrivalTime)
            time match {
              case Some(t) =>
                onComplete(calculateSplits(flightInfoPaxSplitActor)(destPort, terminalName, flightCode, t)) {
                  case Success(value: List[VoyagePaxSplits]) =>
                    complete(value.toJson.prettyPrint)
                  case Success(flightNotFound: FlightNotFound) =>
                    complete(StatusCodes.NotFound)
                  case Success(any) => failWith(new Exception("Unexpected result: " + any))
                  case Failure(ex) =>
                    log.error(ex, s"Failed to complete for ${destPort} ${terminalName} ${flightCode} ${t}")
                    failWith(ex)
                }
              case None =>
                failWith(new Exception(s"Bad nearly ISO datetime ${arrivalTime}"))
            }
          }
      } ~
      path("flight-pax-splits" / "dest-" ~ portRe /) {
        (port) =>
          parameters('from.?, 'to.?) {
            (from: Option[String], to) =>
              get {
                log.info(s"GET flight-pax-splits to $port between $from, $to")
                val timeFrom: Option[DateTime] = parseUrlDateTime(from.getOrElse(""))
                val timeTo: Option[DateTime] = parseUrlDateTime(to.getOrElse(""))
                (timeFrom, timeTo) match {
                  case (Some(tfrom), Some(tto)) =>
                    onComplete(calculateSplitsFromTimeRange(flightInfoPaxSplitActor)(port, tfrom, tto)) {
                      case Success(value) =>
                        val asList = value.asInstanceOf[List[VoyagePaxSplits]]
                        complete(asList.toJson.prettyPrint)
                      case Failure(ex) =>
                        log.error(ex, s"Failed to complete flight-pax-splits between times")
                        failWith(ex)
                    }
                  case error =>
                    failWith(new Exception(s"Bad nearly ISO datetime ${error}"))
                }
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
    getCarrierCodeAndFlightNumber(flightCode) match {
      case Some((cc, fn)) => aggregator ? ReportVoyagePaxSplit(destPort, cc, fn, arrivalTime)
      case None => Future.failed(new Exception(s"couldn't get carrier and voyage number from $flightCode"))
    }
  }

  def calculateSplitsFromTimeRange(aggregator: AskableActorRef)
                                  (destPort: String, arrivalTimeFrom: DateTime, arrivalTimeTo: DateTime)
                                  (implicit timeout: Timeout, ec: ExecutionContext) = {
    aggregator ? ReportVoyagePaxSplitBetween(destPort, arrivalTimeFrom, arrivalTimeTo)
  }

  val flightCodeRe = """(\w{2})(\d{1,5})""".r("carrierCode", "voyageNumber")

  def getCarrierCodeAndFlightNumber(flightCode: String) = {
    flightCodeRe.findFirstMatchIn(flightCode) match {
      case Some(matches) => Some((matches.group("carrierCode"), matches.group("voyageNumber")))
      case None => None
    }
  }
}
