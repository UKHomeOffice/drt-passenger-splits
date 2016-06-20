package core

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import org.specs2.matcher.Matchers
import parsing.PassengerInfoParser
import parsing.PassengerInfoParser.{PassengerInfo, FlightPassengerInfoResponse}
import org.specs2.mutable.{Specification, SpecificationLike}
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

class FlightPassengerSplitCalculatorSpec extends Specification with Matchers {
  val passport: Some[String] = Some("P")

  case class FlightPaxSplits(egate: Double = 0.0, eea: Double = 0.0, nonEea: Double = 0.0, fastTrack: Double = 0.0) {
    val gateTypeTotal = eea + nonEea + fastTrack + egate

    def normalised = this.copy(egate = this.egate / gateTypeTotal,
      eea = this.eea / gateTypeTotal,
      nonEea = this.nonEea / gateTypeTotal,
      fastTrack = this.fastTrack / gateTypeTotal
    )
  }

  def flightPaxSplits(passengerInfos: List[PassengerInfo]): FlightPaxSplits = {
    val passportCounts = passengerInfos.foldLeft(FlightPaxSplits()) {
      (counts, pi) => {
        pi match {
          case pi if pi.DocumentIssuingCountryCode == "GB" => counts.copy(egate = counts.egate + 1)
          case pi if pi.DocumentIssuingCountryCode == "DE" => counts.copy(eea = counts.eea + 1)
          case pi if pi.DocumentIssuingCountryCode == "NZ" => counts.copy(nonEea = counts.nonEea + 1)
        }
      }
    }
    passportCounts.normalised
  }

  "FlightPassengerSplitCalculator should" >> {
    "given an API passenger info set " in {

      "where everyone is from GB" in {
        val passengerInfo = PassengerInfo(passport, "GB", None) :: Nil
        "then egate usage should be 100%" in {
          val distribution = flightPaxSplits(passengerInfo)
          distribution should beEqualTo(FlightPaxSplits(egate = 1.0))
        }
      }
      "where half are from  GB and half are from NZ " in {
        val passengerInfo = PassengerInfo(passport, "GB", None) :: PassengerInfo(passport, "NZ") :: Nil
        "then egate usage should be 50% and nonEEA = 50%" in {
          val distribution = flightPaxSplits(passengerInfo)
          distribution should beEqualTo(FlightPaxSplits(egate = 0.5, nonEea = 0.5))
        }
      }
      "where half are from  GB and half are from DE " in {
        val passengerInfo = PassengerInfo(passport, "GB") :: PassengerInfo(passport, "DE") :: Nil
        "then egate usage should be 50% and eea = 50%" in {
          val distribution = flightPaxSplits(passengerInfo)
          distribution should beEqualTo(FlightPaxSplits(egate = 0.5, eea = 0.5))
        }
      }
    }
  }
}

