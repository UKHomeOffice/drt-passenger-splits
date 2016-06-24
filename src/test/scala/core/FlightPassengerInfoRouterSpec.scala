package core

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import core.PassengerSplitsCalculator.PaxSplits
import org.specs2.matcher.Matchers
import parsing.PassengerInfoParser
import parsing.PassengerInfoParser.{EventCodes, PassengerInfo, VoyagePassengerInfo}
import org.specs2.mutable.{Specification, SpecificationLike}
import spray.http.DateTime

class FlightPassengerInfoRouterSpec extends
  TestKit(ActorSystem()) with SpecificationLike with ImplicitSender with CoreActors with Core {
  test =>

  import PassengerInfoRouter._

  isolated

  "Router should accept PassengerFlightInfo messages" >> {
    "Accept a single flight info" in {
      aggregator ! VoyagePassengerInfo(EventCodes.DoorsClosed, "LGW", "12345", "EZ", "2015-06-01", Nil)
      aggregator ! ReportVoyagePaxSplit("LGW", "12345", DateTime(3000))
      expectMsg(VoyagePaxSplits("LGW", "12345", PaxSplits(egate = 0.20)) :: Nil)
      success
    }

  }

}




