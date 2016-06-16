package core

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import parsing.PassengerInfoParser
import PassengerInfoParser.FlightPassengerInfoResponse
import org.specs2.mutable.SpecificationLike
import spray.http.DateTime

class FlightPassengerInfoRouterSpec extends
  TestKit(ActorSystem()) with SpecificationLike with ImplicitSender with CoreActors with Core {
  test =>
  import FlightPassengerInfoRouter._
  isolated

  "Router should accept PassengerFlightInfo messages" >> {
    "Accept a single flight info" in {
      aggregator ! FlightPassengerInfoResponse("LGW", "12345", "EZ", "2015-06-01", Nil)
      aggregator ! ReportFlightSplit("LGW", "12345", DateTime(3000))
      expectMsg(FlightSplit("LGW", "12345", Map("E-GATE" -> 0.20)) :: Nil)
      success
    }

  }

}



