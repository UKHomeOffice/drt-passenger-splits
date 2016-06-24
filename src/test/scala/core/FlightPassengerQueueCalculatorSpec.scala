package core

import org.specs2.mutable.Specification
import org.specs2.matcher.Matchers
import org.specs2.specification.Tables

class FlightPassengerQueueCalculatorSpec extends Specification with Matchers with Tables {
  "Information about a passenger and their document type tells us what passenger type they are" >> {
    passengerType
  }
  "Information about a passenger type is used to inform what queue we think they'll go to." >> {

    "Given an EEA machine readable passenger we think they'll go to desks in these ratios" in {
      "PassengerType" | "EGate%" | "Desk%" |>
        eeaMachineReadable ! 0.8 ! 0.2 | {
        (passengerType, egateRatio, queueRatio) => {
          val deskRatio = passengerDeskRatio(passengerType)
          deskRatio must_== (egateRatio, queueRatio)
        }
      }
    }
  }
  type EgateToDeskRatios = (Double, Double)

  def passengerDeskRatio(passengerType: String): EgateToDeskRatios = {
    (0.8, 0.2)
  }

  def passengerType = {
    import core.PassengerTypeCalculator._
    import CountryCodes._
    "NationalityCountryEEAFlag" | "DocumentIssuingCountryCode" | "DocumentType" | "PassengerType" |>
      "EEA" ! Germany ! "P" ! eeaMachineReadable |
      "" ! "NZL" ! "P" ! nationalNonVisa |
      "" ! "NZL" ! "V" ! nationalVisa |
      "" ! "AUS" ! "V" ! nationalVisa |
      EEA ! Greece ! "P" ! eeaNonMachineReadable |
      EEA ! Italy ! "P" ! eeaNonMachineReadable |
      EEA ! Portugal ! "P" ! eeaNonMachineReadable |
      EEA ! Slovakia ! "P" ! eeaNonMachineReadable | {
      (countryFlag, documentCountry, documentType, passengerType) =>
        paxType(countryFlag, documentCountry, documentType) must_== passengerType
    }
  }

  val eeaNonMachineReadable = "eea-non-machine-readable"
  val nationalVisa = "national-visa"
  val eeaMachineReadable = "eea-machine-readable"
  val nationalNonVisa = "national-non-visa"


}
