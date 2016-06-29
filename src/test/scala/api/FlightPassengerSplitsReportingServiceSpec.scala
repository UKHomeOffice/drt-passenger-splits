package api

import org.specs2.mutable.Specification
import akka.actor.{ActorRef, Props}
import akka.event.Logging
import core.{PassengerInfoRouterActor}
import org.specs2.specification.AfterAll
import parsing.PassengerInfoParser._
import spray.testkit.Specs2RouteTest
import spray.routing.Directives
import org.specs2.mutable.Specification
import spray.http.{StatusCodes, DateTime, HttpResponse}

import spray.json._
import DefaultJsonProtocol._


class FlightPassengerSplitsReportingServiceSpec extends Specification with AfterAll with Directives with Specs2RouteTest {
  def actorRefFactory = system
  val log = Logging(system, classOf[FlightPassengerSplitsReportingServiceSpec])
  "The routing infrastructure should support" >> {
    val aggregationRef: ActorRef = system.actorOf(Props[PassengerInfoRouterActor])
    val serviceAgg = new FlightPassengerSplitsReportingService(system, aggregationRef)
    aggregationRef ! VoyagePassengerInfo(EventCodes.DoorsClosed,
      "LHR", "123", "BA", "2015-05-01", "14:55", PassengerInfoJson(Some("P"), "GBR", "EEA", None) :: Nil)

    "the most simple and direct route" in {
      Get("/flight/BA123") ~> serviceAgg.route ~> check {
        responseAs[String].parseJson ===
          """[{"ScheduledDateOfArrival":"2015-05-01",
            "EventCode": "DC",
             "PassengerList":[{"DocumentType":"P","DocumentIssuingCountryCode":"GBR","NationalityCountryEEAFlag":"EEA"}],
             "ScheduledTimeOfArrival": "14:55",
              "CarrierCode":"BA","VoyageNumber":"123","ArrivalPortCode":"LHR"}]
          """.stripMargin.parseJson
      }
    }
    "a route to get flight-pax-splits for a specific flight" in {
      aggregationRef ! VoyagePassengerInfo(EventCodes.DoorsClosed,
        "STN", "934", "RY", "2015-02-01", "13:48:00", PassengerInfoJson(Some("P"), "DEU", "EEA", None) :: Nil)
      Get("/flight-pax-splits/dest-STN/terminal-N/RY934/scheduled-arrival-time-20150201T1348") ~>
        serviceAgg.route ~> check {
        log.info("response was" + responseAs[String])
        assert(response.status === StatusCodes.OK)
        responseAs[String].parseJson ===
          """
            |[{
            | "destinationPort": "STN",
            | "flightNumber": "934",
            | "carrier": "RY",
            | "scheduledArrival": "2015-02-01T13:48:00",
            | "totalPax": 1,
            | "paxSplit": [
            |     {"paxType": "eea-machine-readable", "queueType": "eea-desk", "numberOfPax": 1}
            | ]
            |}]
          """.stripMargin.parseJson
      }
    }
  }

  def afterAll() = system.terminate()
}

class FlightPassengerSplitsReportingServiceUnitTests extends Specification {
  "We can parse a date from a not quite ISO datetime" in {
    Some(DateTime(2016, 5, 1, 13, 44)) should beEqualTo(FlightPassengerSplitsReportingService.parseUrlDateTime("20160501T1344"))
  }
  "Invalid syntax datetimes will be a None" in {
    None should beEqualTo(FlightPassengerSplitsReportingService.parseUrlDateTime("2016-0501T1--344"))
  }
  "Correct syntax YYYYMMDDTHHMM but bad values will be None" in {
    None should beEqualTo(FlightPassengerSplitsReportingService.parseUrlDateTime("20161509T0103"))
  }
}
