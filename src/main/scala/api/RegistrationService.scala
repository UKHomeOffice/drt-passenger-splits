package api

import core.AggregationActor.{FlightInfo, ReportFlightCode}
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

class RegistrationService(registration: ActorRef)(implicit executionContext: ExecutionContext)
  extends Directives with DefaultJsonFormats {

  case class ImageUploaded(size: Int)

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

  val route =
    path("register") {
      post {
        handleWith { ru: Register => (registration ? ru).mapTo[Either[NotRegistered.type, Registered.type]] }
      }
    }
//  ~
//      path("register" / "image") {
//        post {
//          handleWith { data: MultipartFormData =>
//            data.fields.filter(_.name == Some("files[]")) match {
//              case Some(imageEntity) =>
//                val size = imageEntity.entity.buffer.length
//                println(s"Uploaded $size")
//                ImageUploaded(size)
//              case None =>
//                println("No files")
//                ImageUploaded(0)
//            }
//          }
//        }
//      }
}

class AggregationReportingService(aggregation: ActorRef)(implicit executionContext: ExecutionContext)
  extends Directives with DefaultJsonFormats {

  case class ImageUploaded(size: Int)
//  import reflect.ClassTag
  import akka.pattern.ask
  import spray.json._
  import DefaultJsonProtocol._
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


  object MyJsonDateTimeProtocol extends DefaultJsonProtocol {
    implicit object DateTimeJsonFormat extends JsonWriter[DateTime] {
      def write(c: DateTime) = JsString(c.toIsoDateTimeString)

//      def read(value: JsValue) = {
//        value.asJsObject.getFields("name", "red", "green", "blue") match {
//          case Seq(JsString(name), JsNumber(red), JsNumber(green), JsNumber(blue)) =>
//            new Color(name, red.toInt, green.toInt, blue.toInt)
//          case _ => throw new DeserializationException("Color expected")
//        }
//      }
    }
    implicit object FlightInfoJsonFormat extends JsonWriter[FlightInfo] {
      def write(obj: FlightInfo) = JsString("hellobob")
    }
//    implicit val flightInfoFormat = jsonFormat3(FlightInfo)

  }

  import MyJsonDateTimeProtocol.FlightInfoJsonFormat

  val route =
    path("flight" / Segment) {
      (flightCode) =>
        get {
          complete((aggregation ? ReportFlightCode(flightCode)).mapTo[List[FlightInfo]]
              .map { _.map(_.toJson).mkString("[",",", "]") }
          )
        }
    }
}