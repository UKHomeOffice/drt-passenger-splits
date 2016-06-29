package core

import core.PassengerTypeCalculator.CountryCodes
import org.specs2.mutable.Specification
import org.specs2.matcher.Matchers
import org.specs2.specification.Tables
import PassengerQueueCalculator._
import PassengerQueueTypes.Desks._
import parsing.PassengerInfoParser.PassengerInfoJson

class FlightPassengerQueueCalculatorSpec extends Specification with Matchers with Tables {
  "Information about a passenger and their document type tells us what passenger type they are" >> {
    s2"""$passengerType"""
  }

  "Information about a passenger type is used to inform what queue we think they'll go to." >> {
    "Given a list of passenger types, count by passenger type" in {
      val passengerTypes = EeaMachineReadable ::
        NationalNonVisa ::
        NationalNonVisa ::
        NationalVisa ::
        EeaNonMachineReadable ::
        Nil
      val passengerTypeCounts = countPassengerTypes(passengerTypes)
      val expectedpassengerTypeCounts = Map(
        EeaMachineReadable -> 1,
        EeaNonMachineReadable -> 1,
        NationalNonVisa -> 2,
        NationalVisa -> 1
      )
      expectedpassengerTypeCounts should beEqualTo(passengerTypeCounts)
    }

    "Given counts of passenger types, " +
      "And a 'machineRead to desk percentage' of 60% " +
      "Then we can generate counts of passenger types in queues" in {
      val passengerTypeCounts = Map(
        EeaMachineReadable -> 20,
        EeaNonMachineReadable -> 10,
        NationalNonVisa -> 10,
        NationalVisa -> 5
      )
      val calculatedDeskCounts = calculateQueuePaxCounts(passengerTypeCounts)
      calculatedDeskCounts should beEqualTo(Map(
        (EeaMachineReadable, egate) -> 12,
        (EeaMachineReadable, eeaDesk) -> 8,
        (EeaNonMachineReadable, eeaDesk) -> 10,
        (NationalNonVisa, nationalsDesk) -> 10,
        (NationalVisa, nationalsDesk) -> 5
      ))
    }
    "Given different counts of passenger types, " +
      "And a 'machineRead to desk percentage' of 80% " +
      "Then we can generate counts of passenger types in queues" in {
      val passengerTypeCounts = Map(
        EeaMachineReadable -> 100,
        EeaNonMachineReadable -> 15,
        NationalNonVisa -> 50,
        NationalVisa -> 10
      )
      val expectedDeskPaxCounts = Map(
        (EeaMachineReadable, egate) -> 60,
        (EeaMachineReadable, eeaDesk) -> 40,
        (EeaNonMachineReadable, eeaDesk) -> 15,
        (NationalNonVisa, nationalsDesk) -> 50,
        (NationalVisa, nationalsDesk) -> 10
      )
      val calculatedDeskCounts = calculateQueuePaxCounts(passengerTypeCounts)
      calculatedDeskCounts should beEqualTo(expectedDeskPaxCounts)
    }
    "Given just some nationals on visa and non visa" +
      "Then we can generate counts of types of passengers in queues" in {
      val passengerTypeCounts = Map(
        NationalNonVisa -> 50,
        NationalVisa -> 10
      )
      val expectedDeskPaxCounts = Map(
        (NationalVisa, nationalsDesk) -> 10,
        (NationalNonVisa, nationalsDesk) -> 50
      )
      val calculatedDeskCounts = calculateQueuePaxCounts(passengerTypeCounts)
      calculatedDeskCounts should ===(expectedDeskPaxCounts)
    }

    "Given some passenger info parsed from the AdvancePassengerInfo" in {
      import CountryCodes._
      "Then we can calculate passenger types and queues from that" in {
        val passengerInfos = PassengerInfoJson(Passport, UK, "EEA", None) :: Nil
        PassengerQueueCalculator.convertPassengerInfoToPaxQueueCounts(passengerInfos) should beEqualTo(Map(
          (EeaMachineReadable, eeaDesk) -> 1,
          (EeaMachineReadable, egate) -> 0
        ))
      }
    }
  }


  def passengerType = {
    import core.PassengerTypeCalculator._
    import CountryCodes._
    s2"""${
      "NationalityCountryEEAFlag" | "DocumentIssuingCountryCode" | "DocumentType" | "PassengerType" |>
        "EEA" ! Germany ! "P" ! EeaMachineReadable |
        ""    ! "NZL" ! "P" ! NationalNonVisa |
        ""    ! "NZL" ! "V" ! NationalVisa |
        ""    ! "AUS" ! "V" ! NationalVisa |
        EEA   ! Greece ! "P" ! EeaNonMachineReadable |
        EEA   ! Italy ! "P" ! EeaNonMachineReadable |
        EEA   ! Portugal ! "P" ! EeaNonMachineReadable |
        EEA   ! Slovakia ! "P" ! EeaNonMachineReadable | {
        (countryFlag, documentCountry, documentType, passengerType) =>
          paxType(countryFlag, documentCountry, Option(documentType)) must_== passengerType
      }
    }"""
  }

  val Passport = Some("P")

  val EeaNonMachineReadable = "eea-non-machine-readable"
  val NationalVisa = "national-visa"
  val EeaMachineReadable = "eea-machine-readable"
  val NationalNonVisa = "national-non-visa"


}
