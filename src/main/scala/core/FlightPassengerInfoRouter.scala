package core

import akka.actor.Actor
import core.FlightPassengerInfoRouter.{FlightSplit, ReportFlightSplit, ReportFlightCode}
import parsing.PassengerInfoParser.FlightPassengerInfoResponse
import spray.http.DateTime

import scala.collection.mutable

object FlightPassengerInfoRouter {
  case class ReportFlightSplit(carrierCode: String, flightNumber: String, time: DateTime)
  case class FlightSplit(carrierCode: String, flightNumber: String, paxSplits: Map[String, Double])
  case class ReportFlightCode(flightCode: String)

}

import spray.json._
import DefaultJsonProtocol._

class FlightPassengerInfoRouter extends Actor {
  var myFlights = List[FlightPassengerInfoResponse]()

  def receive = {
    case info: FlightPassengerInfoResponse =>
      println("Got new passenger info", info)
      myFlights = info :: (myFlights filterNot (_.flightCode == info.flightCode))
    case ReportFlightSplit(_, _, _) =>
      println("Report flight split!")
      sender ! FlightSplit("LGW", "12345", Map("E-GATE" -> 0.20)) :: Nil
    case ReportFlightCode(flightCode) =>
      val matchingFlights: List[FlightPassengerInfoResponse] = myFlights.filter(_.flightCode == flightCode)
      println(s"Will reply ${matchingFlights}")

      sender ! matchingFlights
  }
}
