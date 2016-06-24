package api

import akka.actor.{ActorRef, Props}
import core.{PassengerInfoRouter, PassengerInfoRouter$}
import parsing.PassengerInfoParser._
import spray.testkit.Specs2RouteTest
import spray.routing.Directives
import org.specs2.mutable.Specification
import spray.http.{DateTime, HttpResponse}

import spray.json._
import DefaultJsonProtocol._

class RegistrationServiceSpec extends Specification with Directives with Specs2RouteTest {

  "The routing infrastructure should support" >> {
    "the most simple and direct route" in {
      Get() ~> complete(HttpResponse()) ~> (_.response) === HttpResponse()
    }
  }
}

class AggregationServiceSpec extends Specification with Directives with Specs2RouteTest {
  def actorRefFactory = system

  private val aggregationRef: ActorRef = system.actorOf(Props[PassengerInfoRouter])
  val serviceAgg = new AggregationReportingService(aggregationRef)

  "The routing infrastructure should support" >> {
    "the most simple and direct route" in {
      aggregationRef ! VoyagePassengerInfo(EventCodes.DoorsClosed, "LHR", "123", "BA", "2015-05-01", Nil)
      Get("/flight/BA123") ~> serviceAgg.route ~> check {
        responseAs[String].parseJson ===
          """[{"ScheduledDateOfArrival":"2015-05-01",
            "EventCode": "DC",
             "PassengerList":[],
              "CarrierCode":"BA","VoyageNumber":"123","ArrivalPortCode":"LHR"}]
            """.stripMargin.parseJson
      }
    }
  }
}
