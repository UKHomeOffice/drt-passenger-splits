package api

import akka.actor.{ActorRef, Props}
import akka.event.Logging
import core.{PassengerTypeCalculator, PassengerInfoRouterActor}
import org.specs2.mutable.Specification
import org.specs2.specification.AfterAll
import parsing.PassengerInfoParser._
import spray.http.{DateTime, StatusCodes}
import spray.json._
import spray.routing.Directives
import spray.testkit.Specs2RouteTest
import org.scalacheck._

import scala.collection.immutable.IndexedSeq

class FlightPassengerSplitsPerformanceSpec extends Specification with AfterAll with Directives with Specs2RouteTest {
  def actorRefFactory = system

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

  def flights(max: Int): IndexedSeq[Any] = (1 to max).map {
    (_) => Arbitrary(flightGen).arbitrary.sample.get
  }


  "Make 100 flights" in {
    //    println(flights(100).mkString("\n"))
    true
  }

  "Given lots of flight events" >> {
    val aggregationRef: ActorRef = system.actorOf(Props[PassengerInfoRouterActor])
    val serviceAgg = new FlightPassengerSplitsReportingService(system, aggregationRef)
    val fs = flights(1000)
    fs foreach {
      (flight) => aggregationRef ! flight
    }
    Thread.sleep(3000)
    //    aggregationRef ! VoyagePassengerInfo(EventCodes.DoorsClosed,
    //      "LHR", "123", "BA", "2015-05-01", "14:55", PassengerInfoJson(Some("P"), "GBR", "EEA", None) :: Nil)
    val earliestFlightGenerated = fs.head
    s"looking for the first event ${earliestFlightGenerated}" in {
      earliestFlightGenerated match {

        case VoyagePassengerInfo(_, port, voyageNumber, carrier, scheduleDate, scheduledTime, passengers) =>
          val nearlyIsoArrivalDt = s"${scheduleDate}T${scheduledTime}"
          log.info("About to request")
          "a request for the paxSplits for a specific flight should respond in < 500msec" in {
            Get(s"/flight-pax-splits/dest-${port}/terminal-N/${carrier}${voyageNumber}/scheduled-arrival-time-${nearlyIsoArrivalDt}") ~>
              serviceAgg.route ~> check {
              log.info("response was" + responseAs[String])
              assert(response.status === StatusCodes.OK)
              val json: JsValue = responseAs[String].parseJson

              val expected =
                """
                  |[{
                  | "destinationPort": "STN",
                  | "voyageNumber": "934",
                  | "carrierCode": "RY",
                  | "scheduledArrivalDateTime": "2015-02-01T13:48:00",
                  | "totalPaxCount": 1,
                  | "paxSplits": [
                  |     {"passengerType": "eea-machine-readable", "queueType": "desk", "paxCount": 1},
                  |     {"passengerType": "eea-machine-readable", "queueType": "egate", "paxCount": 0}
                  | ]
                  |}]
                """.stripMargin.
                  parseJson
              println(json.prettyPrint)
              println(expected.prettyPrint)
              json should beEqualTo (expected)
            }
          }
          false
      }
    }
  }

  def afterAll() = system.terminate()
}


