package core

import core.PassengerSplitsCalculator.PaxSplits
import org.specs2.matcher.Matchers
import org.specs2.mutable.Specification
import org.specs2.specification.Tables
import parsing.PassengerInfoParser.PassengerInfo


class FlightPassengerSplitCalculatorSpec extends Specification with Matchers {

  import PassengerSplitsCalculator._

  "FlightPassengerSplitCalculator should" >> {
    "given an API passenger info set " in {

      "where everyone is from GB and the DocumentType is Passport (P)" in {
        val passengerInfo = PassengerInfo(passport, "GB", None) :: Nil
        "then egate usage should be 100%" in {
          flightPaxSplits(passengerInfo) should beEqualTo(PaxSplits(egate = 1.0))
        }
      }
      "where half are from  GB and half are from NZ " in {
        val passengerInfo = PassengerInfo(passport, "GB", None) :: PassengerInfo(passport, "NZ") :: Nil
        "then egate usage should be 50% and nonEEA = 50%" in {
          flightPaxSplits(passengerInfo) should beEqualTo(PaxSplits(egate = 1, nonEea = 1))
        }
      }
      "where half are from  GB and half are from AU " in {
        val passengerInfo = PassengerInfo(passport, "GB", None) :: PassengerInfo(passport, "AU") :: Nil
        "then egate usage should be 50% and nonEEA = 50%" in {
          flightPaxSplits(passengerInfo) should beEqualTo(PaxSplits(egate = 1, nonEea = 1))
        }
      }
      "where half are from  GB and half are from DE and everyone has electronic passports" in {
        val passengerInfo = PassengerInfo(passport, "GB") :: PassengerInfo(passport, "DE") :: Nil
        "then egate usage should be 100%" in {
          flightPaxSplits(passengerInfo) should beEqualTo(PaxSplits(egate = 2))
        }
      }
      "where half are from  GB and half are from DE and the DE have Identity Cards" in {
        val passengerInfo = PassengerInfo(passport, "GB") :: PassengerInfo(identityCard, "DE") :: Nil
        "then egate usage should be 50% and 50% EEA queues" in {
          flightPaxSplits(passengerInfo) should beEqualTo(PaxSplits(egate = 1, eea = 1))
        }
      }
    }
  }
}
