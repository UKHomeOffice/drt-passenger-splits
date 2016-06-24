package core

import akka.actor.Actor
import core.PassengerInfoRouter.{VoyagePaxSplits, ReportVoyagePaxSplit, ReportFlightCode}
import core.PassengerSplitsCalculator.PaxSplits
import parsing.PassengerInfoParser.VoyagePassengerInfo
import spray.http.DateTime

import scala.collection.mutable

object PassengerInfoRouter {
  case class ReportVoyagePaxSplit(carrierCode: String, voyageNumber: String, time: DateTime)
  case class VoyagePaxSplits(carrierCode: String, voyageNumber: String, paxSplits: PaxSplits)
  case class ReportFlightCode(flightCode: String)

}

import spray.json._
import DefaultJsonProtocol._

class PassengerInfoRouter extends Actor {
  val id = getClass.toString
  var myFlights = List[VoyagePassengerInfo]()

  def receive = {
    case info: VoyagePassengerInfo =>
      println("Got new passenger info"+ info)
      myFlights = info :: (myFlights filterNot (_.flightCode == info.flightCode))
    case ReportVoyagePaxSplit(carrierCode, flightCode, _) =>
      println(id, "Report flight split!")
      val matchingFlights = myFlights.filter((flight)=> flight.flightCode == flightCode && carrierCode == flight.CarrierCode)

      sender ! VoyagePaxSplits("LGW", "12345", PaxSplits(egate=0.20)) :: Nil
    case ReportFlightCode(flightCode) =>
      val matchingFlights: List[VoyagePassengerInfo] = myFlights.filter(_.flightCode == flightCode)
      println(s"Will reply ${matchingFlights}")

      sender ! matchingFlights
  }
}
