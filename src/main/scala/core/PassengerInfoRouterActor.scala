package core

import akka.actor.{ActorLogging, Actor}
import core.PassengerInfoRouterActor.{FlightNotFound, ReportFlightCode, ReportVoyagePaxSplit, VoyagePaxSplits}
import core.PassengerQueueTypes.PaxTypeAndQueueCount
import parsing.PassengerInfoParser.VoyagePassengerInfo
import spray.http.DateTime

object PassengerInfoRouterActor {

  case class ReportVoyagePaxSplit(carrierCode: String, voyageNumber: String, scheduledArrivalDateTime: DateTime)

  case class VoyagePaxSplits(carrierCode: String, voyageNumber: String, paxSplits: PaxTypeAndQueueCount)

  case class ReportFlightCode(flightCode: String)

  case class FlightNotFound(carrierCode: String, flightCode: String, scheduledArrivalDateTime: DateTime)

}

class PassengerInfoRouterActor extends Actor with PassengerQueueCalculator with ActorLogging {
  val id = getClass.toString
  var myFlights = List[VoyagePassengerInfo]()

  def receive = {
    case info: VoyagePassengerInfo =>
      log.info("Got new passenger info" + info)
      myFlights = info :: (myFlights filterNot (_.flightCode == info.flightCode))
    case ReportVoyagePaxSplit(carrierCode, voyageNumber, scheduledArrivalDateTime) =>
      log.info(id + s"Report flight split for $carrierCode $voyageNumber\n Current state is ${myFlights}")
      val matchingFlights: Option[VoyagePassengerInfo] = myFlights.find {
        (flight) => {
          log.info(s"Testing $flight ${flight.scheduleArrivalDateTime} === ($carrierCode, ${voyageNumber}, ${scheduledArrivalDateTime})")
          log.info(s"Dates are scheduledArrivalDateTime == flight.scheduleArrivalDateTime.get ${Some(scheduledArrivalDateTime) == flight.scheduleArrivalDateTime}")
          flight.VoyageNumber == voyageNumber && carrierCode == flight.CarrierCode && Some(scheduledArrivalDateTime) == flight.scheduleArrivalDateTime
        }
      }
      log.info(s"Matching flight is ${matchingFlights}")
      matchingFlights match {
        case Some(flight) =>
          val paxTypeAndQueueCount: PaxTypeAndQueueCount = PassengerQueueCalculator.convertPassengerInfoToPaxQueueCounts(flight.PassengerList)
          sender ! VoyagePaxSplits(carrierCode, voyageNumber, paxTypeAndQueueCount) :: Nil
        case None =>
          sender ! FlightNotFound(carrierCode, voyageNumber, scheduledArrivalDateTime)
      }
    case ReportFlightCode(flightCode) =>
      val matchingFlights: List[VoyagePassengerInfo] = myFlights.filter(_.flightCode == flightCode)
      log.info(s"Will reply ${matchingFlights}")

      sender ! matchingFlights
  }
}
