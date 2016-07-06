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

  case object ProcessedFlightInfo

  case object LogStatus

}

trait SimpleRouterActor[C <: Actor] {
  self: Actor with ActorLogging =>
  var childActorMap = Map.empty[String, ActorRef]

  def childProps: Props

  def getRCActor(id: String) = childActorMap get id getOrElse {
    val c = context actorOf childProps
    childActorMap += id -> c
    context watch c
    log.info(s"created actor ${id}")
    c
  }
}

class PassengerInfoByPortRouter extends
  Actor with PassengerQueueCalculator with ActorLogging
  with SimpleRouterActor[PassengerInfoRouterActor] {

  def childProps = Props[PassengerInfoRouterActor]

  def receive: PartialFunction[Any, Unit] = {
    case info: VoyagePassengerInfo =>
      val child = getRCActor(childName(info.ArrivalPortCode, info.CarrierCode))
      child.tell(info, sender)
    case report: ReportVoyagePaxSplit =>
      val child = getRCActor(childName(report.destinationPort, report.carrierCode))
      child.tell(report, sender)
    case report: ReportFlightCode =>
      childActorMap.values.foreach(_ ! report)
    case LogStatus =>
      childActorMap.values.foreach(_ ! LogStatus)
  }

  def childName(arrivalPortCode: String, carrierCode: String): String = {
    s"pax-split-calculator-${arrivalPortCode}"
  }
}

class PassengerInfoRouterActor extends Actor with ActorLogging
  with SimpleRouterActor[SingleFlightActor] {

  def childProps = Props(classOf[SingleFlightActor])

  def receive = {
    case info: VoyagePassengerInfo =>
      val child = getRCActor(childName(info.ArrivalPortCode, info.CarrierCode, info.VoyageNumber, info.scheduleArrivalDateTime.get))
      child.tell(info, sender)
    case report: ReportVoyagePaxSplit =>
      val name: String = childName(report.destinationPort, report.carrierCode, report.voyageNumber,
        report.scheduledArrivalDateTime)
      log.info(s"Asking $name for paxSplits")
      val child = getRCActor(name)
      child.tell(report, sender)
    case report: ReportFlightCode =>
      (childActorMap.values).foreach { case child => child.tell(report, sender) }
    case LogStatus =>
      childActorMap.values.foreach(_ ! LogStatus)
  }

  def childName(port: String, carrierCode: String, voyageNumber: String, scheduledArrivalDt: DateTime) = {
    s"flight-pax-calculator-$port-$carrierCode-$voyageNumber-$scheduledArrivalDt"
  }
}

class SingleFlightActor
  extends Actor with PassengerQueueCalculator with ActorLogging {
  var latestMessage: Option[VoyagePassengerInfo] = None

  def receive = {
    case info: VoyagePassengerInfo =>
      latestMessage = Option(info)
      sender ! ProcessedFlightInfo
    case ReportVoyagePaxSplit(port, carrierCode, voyageNumber, scheduledArrivalDateTime) =>
      log.info(s"Report flight split for $port $carrierCode $voyageNumber $scheduledArrivalDateTime")
      log.info(s"Current flights ${latestMessage}")
      val matchingFlights: Option[VoyagePassengerInfo] = latestMessage.find {
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
      val matchingFlights: Option[VoyagePassengerInfo] = latestMessage.filter(_.flightCode == flightCode)
      log.info(s"Will reply ${matchingFlights}")
      for (mf <- matchingFlights)
        sender ! List(mf)
    case LogStatus =>
      log.info(s"Current Status ${latestMessage}")
  }

  def doesFlightMatch(info: VoyagePassengerInfo, existingMessage: VoyagePassengerInfo): Boolean = {
    doesFlightMatch(info.CarrierCode,
      info.VoyageNumber, info.scheduleArrivalDateTime.get, existingMessage)
  }

  def doesFlightMatch(carrierCode: String, voyageNumber: String, scheduledArrivalDateTime: DateTime, flight: VoyagePassengerInfo): Boolean = {
    flight.VoyageNumber == voyageNumber && carrierCode == flight.CarrierCode && Some(scheduledArrivalDateTime) == flight.scheduleArrivalDateTime
  }
}
