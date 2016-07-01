package core

import akka.actor.{Actor, ActorLogging}
import core.PassengerInfoRouterActor._
import core.PassengerQueueTypes.PaxTypeAndQueueCounts
import parsing.PassengerInfoParser.VoyagePassengerInfo
import spray.http.DateTime

object PassengerInfoRouterActor {

  case class ReportVoyagePaxSplit(carrierCode: String, voyageNumber: String, scheduledArrivalDateTime: DateTime)

  case class VoyagePaxSplits(destinationPort: String, carrierCode: String,
                             voyageNumber: String,
                             totalPaxCount: Int,
                             scheduledArrivalDateTime: DateTime,
                             paxSplits: PaxTypeAndQueueCounts)

  case class ReportFlightCode(flightCode: String)

  case class FlightNotFound(carrierCode: String, flightCode: String, scheduledArrivalDateTime: DateTime)
  object ProcessedFlightInfo
}

class PassengerInfoRouterActor extends Actor with PassengerQueueCalculator with ActorLogging {
  val id = getClass.toString
  var myFlights = List[VoyagePassengerInfo]()

  def receive = {
    case info: VoyagePassengerInfo =>
//      log.info("Got new passenger info: " + info)
      myFlights = info :: (myFlights filterNot (_.flightCode == info.flightCode))
      log.info(s"Count now: ${myFlights.length}")
      sender ! ProcessedFlightInfo
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
          val paxTypeAndQueueCount: PaxTypeAndQueueCounts = PassengerQueueCalculator.
            convertPassengerInfoToPaxQueueCounts(flight.PassengerList)
          sender ! VoyagePaxSplits(flight.ArrivalPortCode,
            carrierCode, voyageNumber, flight.PassengerList.length, scheduledArrivalDateTime,
            paxTypeAndQueueCount) :: Nil
        case None =>
          sender ! FlightNotFound(carrierCode, voyageNumber, scheduledArrivalDateTime)
      }
    case ReportFlightCode(flightCode) =>
      val matchingFlights: List[VoyagePassengerInfo] = myFlights.filter(_.flightCode == flightCode)
      log.info(s"Will reply ${matchingFlights}")

      sender ! matchingFlights
  }
}
