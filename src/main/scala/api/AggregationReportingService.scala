package api

import akka.actor.ActorRef
import akka.util.Timeout
import core.PassengerInfoRouter.{ReportFlightCode, VoyagePaxSplits}
import core.PassengerSplitsCalculator.PaxSplits
import core.RegistrationActor.{Register, NotRegistered, Registered}
import core.User
import parsing.PassengerInfoParser.VoyagePassengerInfo
import spray.http.{DateTime, StatusCodes, StatusCode}
import spray.routing.Directives
import spray.http._
import core.PassengerInfoRouter.{VoyagePaxSplits, ReportFlightCode}
import spray.routing.Directives
import scala.concurrent.ExecutionContext
import akka.actor.ActorRef
import core.{User, RegistrationActor}
import akka.util.Timeout
import RegistrationActor._
import spray.http._
import core.User
import core.RegistrationActor.Register
import scala.Some
import scala.util.{Try, Success, Failure}

import scala.concurrent.ExecutionContext

class AggregationReportingService(aggregation: ActorRef)(implicit executionContext: ExecutionContext)
  extends Directives with DefaultJsonFormats {

  case class ImageUploaded(size: Int)

  //  import reflect.ClassTag
  import parsing.PassengerInfoParser
  import spray.json._
  import akka.pattern.ask

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


  object MyJsonProtocol extends DefaultJsonProtocol {

    implicit object DateTimeJsonFormat extends JsonWriter[DateTime] {
      def write(c: DateTime) = JsString(c.toIsoDateTimeString)
    }

    implicit object FlightInfoJsonFormat extends JsonWriter[VoyagePaxSplits] {
      def write(obj: VoyagePaxSplits) = JsString("hellobob")
    }

    implicit object FlightPaxSplitsInfoJsonFormat extends JsonWriter[PaxSplits] {
      def write(obj: PaxSplits) = JsString("hellobobsplit")
    }

  }
  import PassengerInfoParser.FlightPassengerInfoProtocol._
  import MyJsonProtocol._

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
    }
}
