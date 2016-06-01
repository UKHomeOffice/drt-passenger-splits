package core

import akka.actor.Actor
import core.AggregationActor.{ReportFlightCode, FlightInfo}
import spray.http.DateTime

import scala.collection.mutable

object AggregationActor {

  case class FlightInfo(flightCode: String,
                        scheduledDate: DateTime,
                        choxDown: Option[DateTime]) {
    private def choxDownOptAsJson = choxDown match {
      case Some(s) => "\"" + s + "\""
      case None => "null"
    }

    def toJson: String =
      s"""{"type": "flightInfo",
                          "flightCode": "$flightCode",
    "scheduledDateTime": "${scheduledDate.toIsoDateTimeString}", "choxDown": ${choxDownOptAsJson}}"""
  }

  case class ReportFlightCode(flightCode: String)

}

import spray.json._
import DefaultJsonProtocol._

class AggregationActor extends Actor {
  var myFlights = List[FlightInfo]()

  def receive = {
    case info: FlightInfo =>
      myFlights = info :: (myFlights filterNot (_.flightCode == info.flightCode))
    case ReportFlightCode(flightCode) =>
      val matchingFlights: List[FlightInfo] = myFlights.filter(_.flightCode == flightCode)
      println(s"Will reply ${matchingFlights}")

      sender ! matchingFlights
  }
}
