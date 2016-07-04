package api

import akka.actor._
import akka.event.Logging
import akka.testkit.{TestActorRef, TestKitBase, TestKit, ImplicitSender}
import core.PassengerInfoRouterActor.ProcessedFlightInfo
import core.{PassengerInfoByPortRouter, PassengerTypeCalculator, PassengerInfoRouterActor}
import org.specs2.mutable.Specification
import org.specs2.specification.AfterAll
import parsing.PassengerInfoParser._
import spray.http.{DateTime, StatusCodes}
import spray.json._
import spray.routing.Directives
import spray.testkit.Specs2RouteTest
import org.scalacheck._
import scala.annotation.tailrec
import scala.concurrent.duration._

import scala.collection.immutable.IndexedSeq

object PassengerInfoBatchComplete

import System.nanoTime

trait SimpleProfiler {
  def profile[R](code: => R, t: Long = nanoTime) = (code, nanoTime - t)
}

case class PassengerInfoBatchActor(replyActor: ActorRef, passengerInfoRoutingRef: ActorRef, flights: Seq[VoyagePassengerInfo]) extends Actor with ActorLogging {
  var received = 0

  def receive = {
    case "Begin" =>
      val dates = flights.map(_.ScheduledDateOfArrival).toSet
      log.info(s"Dates to process ${dates}")
      flights foreach {
        (flight) => passengerInfoRoutingRef ! flight
      }
    case ProcessedFlightInfo =>
      if (received % 1000 == 0) log.info(s"processed flight info ${received}")
      received += 1
      if (received == flights.length) {
        log.info(s"Batch complete")
        replyActor ! PassengerInfoBatchComplete
        self ! PoisonPill
      }
  }
}

class FlightPassengerSplitsPerformanceSpec extends Specification with AfterAll with Directives
  with Specs2RouteTest
  with TestKitBase
  with ImplicitSender
  with SimpleProfiler {
  def actorRefFactory = system

  sequential

  val log = Logging(system, classOf[FlightPassengerSplitsPerformanceSpec])

  def airportGen = Gen.oneOf("LTN", "STN", "LHR", "GTW", "EDI")

  def carrierCodeGen = Gen.oneOf("EZ", "BA", "RY", "NZ")

  def voyageNumberGen = Gen.chooseNum(1000, 9999)

  def eventType = Gen.oneOf("DC", "CI")

  def passengerInfoGen: Gen[PassengerInfoJson] = for {
    dt <- Gen.oneOf("P", "V")
    dicc <- Gen.oneOf(PassengerTypeCalculator.EEACountries.toSeq)
    eeaFlag = "EEA"
    age <- Gen.chooseNum(1, 99)
  } yield PassengerInfoJson(Some(dt), dicc, eeaFlag, Some(age.toString))

  // todo figure out scala check Gen.parameters
  def flightGen(dateTime: DateTime): Gen[VoyagePassengerInfo] = for {
    et <- eventType
    port <- airportGen
    carrier <- carrierCodeGen
    vn <- voyageNumberGen
    minute <- Gen.chooseNum(0, 59)
    hour <- Gen.chooseNum(0, 23)
    randomizedDateTime = dateTime.copy(hour = hour, minute = minute)
    dateStr = randomizedDateTime.toIsoLikeDateTimeString.split(" ")
    passengers <- Gen.listOf(passengerInfoGen)
  } yield VoyagePassengerInfo(et, port, vn.toString, carrier,
    dateStr(0),
    dateStr(1), passengers)

  case class FlightId(port: String, carrier: String, voyageNumber: String, scheduledArrival: DateTime)

  def flightIdGen(dateTime: DateTime): Gen[FlightId] = for {
    port <- airportGen
    carrier <- carrierCodeGen
    vn <- voyageNumberGen
    minute <- Gen.chooseNum(0, 59)
    hour <- Gen.chooseNum(0, 23)
    randomizedDateTime = dateTime.copy(hour = hour, minute = minute)
  } yield FlightId(port, vn.toString, carrier, randomizedDateTime)


  //  def flights(max: Int) = (1 to max).map {
  //    (n) => {
  //      val dateTime = DateTime(2016, 4, 1, 12, 23)
  //      val v: IndexedSeq[VoyagePassengerInfo] = Arbitrary(flightGen(dateTime)).arbitrary.sample.get
  //      v
  //    }
  //  }

  def flightStream(startDateTime: DateTime): Stream[VoyagePassengerInfo] = {
    Arbitrary(flightGen(startDateTime)).arbitrary.sample.get #::
      flightStream(startDateTime)
  }

  def flightList(startDateTime: DateTime, numFlights: Int): Seq[VoyagePassengerInfo] = {
    (0 to numFlights).flatMap(n => eventsFromFlight(Arbitrary(flightGen(startDateTime)).arbitrary.sample.get))
  }

  def eventsFromFlight(vpi: VoyagePassengerInfo): List[VoyagePassengerInfo] = {
    vpi match {
      case v if v.PassengerList == Nil => Nil
      case v =>
        val copy = v.copy(PassengerList = v.PassengerList.tail)
        copy :: eventsFromFlight(copy)
    }
  }

  implicit def default(implicit system: ActorSystem) = RouteTestTimeout(new DurationInt(5).second)

  val aggregationRef: ActorRef = system.actorOf(Props[PassengerInfoByPortRouter])
  val serviceAgg = new FlightPassengerSplitsReportingService(system, aggregationRef)


  "Given lots of flight events" >> {
    val totalEvents: Int = 1000000
    val flightsToFind: List[VoyagePassengerInfo] = initialiseFlightsWithStream(aggregationRef, totalEvents)

    s"looking for the first event " in {
      val flightToFind = flightsToFind.take(1).toList.head
      log.info(s"Looking for ${flightToFind}")
      findFlightAndCheckResult(flightToFind)
      success("yay")
    }
    s"looking for multiple events ${flightsToFind.length}" in {

      flightsToFind foreach {
        flightToFind =>
          val (result, time) = profile {
            findFlightAndCheckResult(flightToFind)
          }
          log.info(s"Find of ${flightToFind.summary} took ${time / 1000000}")
          result
      }
      success("yay")
    }
  }

  def findFlightAndCheckResult(flightToFind: VoyagePassengerInfo): Unit = {
    flightToFind match {
      case VoyagePassengerInfo(_, port, voyageNumber, carrier, scheduleDate, scheduledTime, passengers) =>
        val nearlyIsoArrivalDt = s"${scheduleDate.replace("-", "")}T${scheduledTime.replace(":", "").take(4)}"
        val routeToRequest: String = s"/flight-pax-splits/dest-${port}/terminal-N/${carrier}${voyageNumber}/scheduled-arrival-time-${nearlyIsoArrivalDt}"
        log.info(s"About to request ${routeToRequest}")
        Get(routeToRequest) ~>
          serviceAgg.route ~> check {
          log.info("response was: " + responseAs[String])
          assert(response.status === StatusCodes.OK)
          val json: JsValue = responseAs[String].parseJson
          json match {
            case JsArray(elements) =>
              val head1 = elements.head.asJsObject
              head1.getFields("destinationPort", "carrierCode", "voyageNumber", "scheduledArrivalDateTime", "totalPaxCount") match {
                case Seq(JsString(port), JsString(carrier), JsString(voyageNumber), JsString(isoArrivalDt), JsNumber(totalPaxCount)) =>
                  success("we got what we came for")
              }
              log.info(s"head is ${head1}")
            case _ => failTest("response was not an array")
          }
        }
      case default =>
        log.error("Why are we here?")
        failTest(s"Why are we here? ${default}")
    }
  }

  def dateStream(sd: DateTime): Stream[DateTime] = sd #:: dateStream(sd + 1000 * 60 * 60 * 24)

  def initialiseFlightsWithStream(aggRef: ActorRef, totalEvents: Int): List[VoyagePassengerInfo] = {
    println("Initialise flights")
    val (result, time) = profile {
      val dts = dateStream(DateTime(2016, 4, 1))
      val flightsPerDay: Int = 1400
      val numberOfDays: Int = 30

      val fs: Stream[VoyagePassengerInfo] = dts.take(numberOfDays).flatMap(flightList(_, flightsPerDay))
//        .take(totalEvents)
      fs.grouped(50000).map {
        (group: Stream[VoyagePassengerInfo]) =>
          val groupList: List[VoyagePassengerInfo] = group.toList
          val batchActor = system.actorOf(Props(new PassengerInfoBatchActor(testActor, aggregationRef, groupList)))
          log.info("Sending messages")
          batchActor ! "Begin"
          expectMsg(500 seconds, PassengerInfoBatchComplete)

          log.info("Sent all messages and they're processed")
          groupList.take(2)
      }.flatten.toList
    }
    log.info(s"Initialise took ${time / 1000000}")
    result

  }

  //  def initialiseFlights(aggregationRef: ActorRef): IndexedSeq[VoyagePassengerInfo] = {
  //    println("Initialise flights")
  //    val (result, time) = profile {
  //      val fs = flights(400000)
  //      val batchActor = system.actorOf(Props(new PassengerInfoBatchActor(testActor, aggregationRef, fs.toList)))
  //      log.info("Sending messages")
  //      batchActor ! "Begin"
  //      expectMsg(500 seconds, PassengerInfoBatchComplete)
  //
  //      log.info("Sent all messages and they're processed")
  //      fs
  //    }
  //    log.info(s"Initialise took ${time / 1000000}")
  //    result
  //  }

  def afterAll() = system.terminate()
}


