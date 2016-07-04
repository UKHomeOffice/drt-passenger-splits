package core

import akka.actor.{Props, ActorRef, Actor, ActorLogging}
import core.PassengerInfoRouterActor._
import core.PassengerQueueTypes.PaxTypeAndQueueCounts
import parsing.PassengerInfoParser.VoyagePassengerInfo
import spray.http.DateTime

object PassengerInfoRouterActor {

  case class ReportVoyagePaxSplit(destinationPort: String,
                                  carrierCode: String, voyageNumber: String, scheduledArrivalDateTime: DateTime)

  case class VoyagePaxSplits(destinationPort: String, carrierCode: String,
                             voyageNumber: String,
                             totalPaxCount: Int,
                             scheduledArrivalDateTime: DateTime,
                             paxSplits: PaxTypeAndQueueCounts)

  case class ReportFlightCode(flightCode: String)

  case class FlightNotFound(carrierCode: String, flightCode: String, scheduledArrivalDateTime: DateTime)

  object ProcessedFlightInfo

  object LogStatus

}

class PassengerInfoByPortRouter extends Actor with PassengerQueueCalculator with ActorLogging {
  var as = Map.empty[String, ActorRef]

  def getRCActor(id: String) = as get id getOrElse {
    val c = context actorOf Props[PassengerInfoRouterActor]
    as += id -> c
    context watch c
    log.info(s"created actor ${id}")
    c
  }

  def receive = {
    case info: VoyagePassengerInfo =>
      val child = getRCActor(s"pax-split-calculator-${info.ArrivalPortCode}-${info.CarrierCode}")
      child.tell(info, sender)
    case report: ReportVoyagePaxSplit =>
      val child = getRCActor(s"pax-split-calculator-${report.destinationPort}-${report.carrierCode}")
      child.tell(report, sender)
    case report: ReportFlightCode =>
      ???
    case LogStatus =>
      as.values.foreach(_ ! LogStatus)
  }
}

class PassengerInfoRouterActor extends Actor with PassengerQueueCalculator with ActorLogging {
  var myFlights = List[VoyagePassengerInfo]()
  var count = 0

  def receive = {
    case info: VoyagePassengerInfo =>
      myFlights = info :: myFlights.filterNot(doesFlightMatch(info, _))
      count += 1
      sender ! ProcessedFlightInfo
    case ReportVoyagePaxSplit(port, carrierCode, voyageNumber, scheduledArrivalDateTime) =>
      log.info(s"Report flight split for $port $carrierCode $voyageNumber $scheduledArrivalDateTime")
//      log.info(s"Current flights ${myFlights}")
      val matchingFlights: Option[VoyagePassengerInfo] = myFlights.find {
        (flight) => {
          doesFlightMatch(carrierCode, voyageNumber, scheduledArrivalDateTime, flight)
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
    case LogStatus =>
      log.info(s"Current Status ${myFlights}")
  }

  def doesFlightMatch(info: VoyagePassengerInfo, existingMessage: VoyagePassengerInfo): Boolean = {
    doesFlightMatch(info.CarrierCode,
      info.VoyageNumber, info.scheduleArrivalDateTime.get, existingMessage)
  }

  def doesFlightMatch(carrierCode: String, voyageNumber: String, scheduledArrivalDateTime: DateTime, flight: VoyagePassengerInfo): Boolean = {
    flight.VoyageNumber == voyageNumber && carrierCode == flight.CarrierCode && Some(scheduledArrivalDateTime) == flight.scheduleArrivalDateTime
  }
}
