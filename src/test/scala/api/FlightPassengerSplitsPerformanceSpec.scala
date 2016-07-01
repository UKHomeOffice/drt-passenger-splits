package api

import akka.actor._
import akka.event.Logging
import akka.testkit.{TestActorRef, TestKitBase, TestKit, ImplicitSender}
import core.PassengerInfoRouterActor.ProcessedFlightInfo
import core.{PassengerTypeCalculator, PassengerInfoRouterActor}
import org.specs2.mutable.Specification
import org.specs2.specification.AfterAll
import parsing.PassengerInfoParser._
import spray.http.{DateTime, StatusCodes}
import spray.json._
import spray.routing.Directives
import spray.testkit.Specs2RouteTest
import org.scalacheck._
import scala.concurrent.duration._

import scala.collection.immutable.IndexedSeq

object PassengerInfoBatchComplete

case class PassengerInfoBatchActor(replyActor: ActorRef, passengerInfoRoutingRef: ActorRef, flights: Seq[VoyagePassengerInfo]) extends Actor with ActorLogging {
  var received = 0

  def receive = {
    case "Begin" =>
      flights foreach {
        (flight) => passengerInfoRoutingRef ! flight
      }
    case ProcessedFlightInfo =>
      if (received % 100 == 0) log.info(s"processed flight info ${received}")
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
  with ImplicitSender {
  def actorRefFactory = system

  isolated
  sequential

  val log = Logging(system, classOf[FlightPassengerSplitsPerformanceSpec])

  def airportGen = Gen.oneOf("LTN", "STN", "LHR")

  def carrierCodeGen = Gen.oneOf("EZ", "BA", "RY")

  def voyageNumberGen = Gen.chooseNum(1000, 9999)

  def eventType = Gen.oneOf("DC", "CI")

  def dateTimeGen = Gen.calendar

  def passengerInfoGen: Gen[PassengerInfoJson] = for {
    dt <- Gen.oneOf("P", "V")
    dicc <- Gen.oneOf(PassengerTypeCalculator.EEACountries.toSeq)
    eeaFlag = "EEA"
    age <- Gen.chooseNum(1, 99)
  } yield PassengerInfoJson(Some(dt), dicc, eeaFlag, Some(age.toString))

  // todo figure out scala check Gen.parameters
  def flightGen = for {
    et <- eventType
    port <- airportGen
    carrier <- carrierCodeGen
    vn <- voyageNumberGen
    month <- Gen.chooseNum(1, 12)
    day <- Gen.chooseNum(1, 20)
    minute <- Gen.chooseNum(0, 59)
    dateTime = DateTime(2016, month, day, 12, minute)
    dateStr = dateTime.toIsoLikeDateTimeString.split(" ")
    passengers <- Gen.listOf(passengerInfoGen)
  } yield VoyagePassengerInfo(et, port, vn.toString, carrier,
    dateStr(0),
    dateStr(1), passengers)

  def flights(max: Int) = (1 to max).map {
    (_) => Arbitrary(flightGen).arbitrary.sample.get
  }


  "Make 100 flights" in {
    //    println(flights(100).mkString("\n"))
    true
  }

  implicit def default(implicit system: ActorSystem) = RouteTestTimeout(new DurationInt(5).second)

  val aggregationRef: ActorRef = system.actorOf(Props[PassengerInfoRouterActor])
  val serviceAgg = new FlightPassengerSplitsReportingService(system, aggregationRef)

  val fs: IndexedSeq[VoyagePassengerInfo] = initialiseFlights(aggregationRef)

  "Given lots of flight events" >> {

    s"looking for the first event " in {
      val flightToFind = fs.head
      log.info(s"Looking for ${flightToFind}")
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
      success("yay")
    }
  }

  def initialiseFlights(aggregationRef: ActorRef): IndexedSeq[VoyagePassengerInfo] = {
    val fs = flights(10000)
    val batchActor = system.actorOf(Props(new PassengerInfoBatchActor(testActor, aggregationRef, fs.toList)))
    batchActor ! "Begin"
    expectMsg(20 seconds, PassengerInfoBatchComplete)
    log.info("Sending messages")
    log.info("Sent all messages and they're processed")
    fs
  }

  def afterAll() = system.terminate()
}


