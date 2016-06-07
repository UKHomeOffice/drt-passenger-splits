package core

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import org.specs2.mutable.SpecificationLike
import spray.http.DateTime

class AggregatorActorSpec extends
  TestKit(ActorSystem()) with SpecificationLike with ImplicitSender with CoreActors with Core {
  test =>
  import AggregationActor._
  isolated

  "Aggregator should take messages about flightInfo" >> {
    "Accept a single flight info" in {
      aggregator ! FlightInfo("BA123", DateTime(3000), None)
      aggregator ! ReportFlightCode("BA123")
      expectMsg(FlightInfo("BA123", DateTime(3000), None) :: Nil)
      success
    }

    "Accept a couple of flights info" in {
      aggregator ! FlightInfo("BA123", DateTime(3000), None)
      aggregator ! FlightInfo("GE456", DateTime(9000), None)
      aggregator ! ReportFlightCode("BA123")
      expectMsg(FlightInfo("BA123", DateTime(3000), None) :: Nil)
      success
    }

    "Only take the most recently received message for a flight" in {
      aggregator ! FlightInfo("BA123", DateTime(3000), None)
      aggregator ! FlightInfo("BA123", DateTime(1000), None)
      aggregator ! ReportFlightCode("BA123")
      expectMsg(FlightInfo("BA123", DateTime(1000), None) :: Nil)
      success
    }

  }

}



