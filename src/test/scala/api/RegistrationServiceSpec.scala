package api

import akka.actor.{ActorRef, Props}
import core.AggregationActor
import core.AggregationActor.FlightInfo
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

class AggregationServiceSpec extends Specification with Directives with Specs2RouteTest
  {
  def actorRefFactory = system

    private val aggregationRef: ActorRef = system.actorOf(Props[AggregationActor])
    val serviceAgg = new AggregationReportingService(aggregationRef)

  "The routing infrastructure should support" >> {
    "the most simple and direct route" in {
      aggregationRef ! FlightInfo("BA123", DateTime(3000), None)
      Get("/flight/BA123") ~> serviceAgg.route ~> check {
        responseAs[String].parseJson ===
          """[{"type":"flightInfo","flightCode":"BA123", "scheduledDateTime": "1970-01-01T00:00:03",
            |"choxDown": null
            |}]""".stripMargin.parseJson
      }
    }
  }
}
