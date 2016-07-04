package core

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import core.PassengerQueueTypes.PaxTypeAndQueueCount
import core.PassengerSplitsCalculator.PaxSplits
import org.specs2.matcher.Matchers
import org.specs2.specification.AfterAll
import parsing.PassengerInfoParser
import parsing.PassengerInfoParser.{PassengerInfoJson, EventCodes, PassengerInfo, VoyagePassengerInfo}
import org.specs2.mutable.{Specification, SpecificationLike}
import spray.http.DateTime

class FlightPassengerInfoRouterActorSpec extends
  TestKit(ActorSystem()) with AfterAll with SpecificationLike with ImplicitSender with CoreActors with Core {
  test =>

  import PassengerInfoRouterActor._

  isolated

  "Router should accept PassengerFlightInfo messages" >> {
    "Parsing datetime make sure you include the seconds" in {
      DateTime.fromIsoDateTimeString("2015-06-01T13:55:00") should beEqualTo(Some(DateTime(2015, 6, 1, 13, 55, 0)))
      DateTime.fromIsoDateTimeString("2015-06-01T13:55") should beEqualTo(None)
    }
    "Given a single flight, with just one GBR passenger" in {
      aggregator ! VoyagePassengerInfo(EventCodes.DoorsClosed, "LGW", "12345", "EZ", "2017-04-02", "15:33:00",
        PassengerInfoJson(Some("P"), "GBR", "EEA", None) :: Nil)
      "When we ask for a report of voyage pax splits then we should see pax splits of the 1 passenger in eeaDesk queue" in {
        aggregator ! ReportVoyagePaxSplit("LGW", "EZ", "12345", DateTime(2017, 4, 2, 15, 33))
        val expectedPaxSplits = Seq(
          PaxTypeAndQueueCount(PassengerQueueTypes.PaxTypes.EEAMACHINEREADABLE, PassengerQueueTypes.Desks.egate, 0),
          PaxTypeAndQueueCount(PassengerQueueTypes.PaxTypes.EEAMACHINEREADABLE, PassengerQueueTypes.Desks.eeaDesk, 1)
        )
        expectMsg(VoyagePaxSplits("LGW", "EZ", "12345", 1, DateTime(2017,4,2,15,33), expectedPaxSplits) :: Nil)
        success
      }
    }

    "Given a single flight STN EZ789 flight, with just one GBR and one nationals passenger" in {
      "When we ask for a report of voyage pax splits" in {
        aggregator ! VoyagePassengerInfo(EventCodes.DoorsClosed, "STN", "789", "EZ", "2015-06-01", "13:55:00",
          PassengerInfoJson(Some("P"), "GBR", "EEA", None) ::
            PassengerInfoJson(Some("P"), "NZL", "", None) ::
            Nil)

        val scheduleArrivalTime: DateTime = DateTime(2015, 6, 1, 13, 55)
        aggregator ! ReportVoyagePaxSplit("STN", "EZ", "789", scheduleArrivalTime)

        val expectedPaxSplits = Seq(
          PaxTypeAndQueueCount(PassengerQueueTypes.PaxTypes.EEAMACHINEREADABLE, PassengerQueueTypes.Desks.egate, 0),
          PaxTypeAndQueueCount(PassengerQueueTypes.PaxTypes.EEAMACHINEREADABLE, PassengerQueueTypes.Desks.eeaDesk, 1),
          PaxTypeAndQueueCount(PassengerQueueTypes.PaxTypes.NATIONALNONVISA, PassengerQueueTypes.Desks.nationalsDesk, 1)
        )
        expectMsg(VoyagePaxSplits("STN", "EZ", "789", 2, scheduleArrivalTime, expectedPaxSplits) :: Nil)
        success
      }
    }
    "Given a single flight STN BA978 flight, with 100 passengers, and a default egate usage of 60%" in {
      "When we ask for a report of voyage pax splits" in {
        aggregator ! VoyagePassengerInfo(EventCodes.DoorsClosed, "STN", "978", "BA", "2015-07-12", "10:22:00",
          List.tabulate(80)(passengerNumber => PassengerInfoJson(Some("P"), "GBR", "EEA", Some((passengerNumber % 60 + 16).toString))) :::
            List.tabulate(20)(_ => PassengerInfoJson(Some("P"), "NZL", "", None)))

        val scheduleArrivalDateTime: DateTime = DateTime(2015, 7, 12, 10, 22)
        aggregator ! ReportVoyagePaxSplit("STN", "BA", "978", scheduleArrivalDateTime)
        val expectedPaxSplits: PassengerQueueTypes.PaxTypeAndQueueCounts = Seq(
          PaxTypeAndQueueCount(PassengerQueueTypes.PaxTypes.EEAMACHINEREADABLE, PassengerQueueTypes.Desks.egate, 48),
          PaxTypeAndQueueCount(PassengerQueueTypes.PaxTypes.EEAMACHINEREADABLE, PassengerQueueTypes.Desks.eeaDesk, 32),
          PaxTypeAndQueueCount(PassengerQueueTypes.PaxTypes.NATIONALNONVISA, PassengerQueueTypes.Desks.nationalsDesk, 20)
        )
        expectMsg(VoyagePaxSplits("STN", "BA", "978", 100, scheduleArrivalDateTime, expectedPaxSplits) :: Nil)
        success
      }
    }

    "Given no flights" in {
      "When we ask for a report of voyage pax splits of a flight we don't know about then we get FlightNotFound " in {
        aggregator ! ReportVoyagePaxSplit("NON", "DNE", "999", DateTime(2015, 6, 1, 13, 55))
        expectMsg(FlightNotFound("DNE", "999", DateTime(2015, 6, 1, 13, 55)))
        success
      }
    }
  }

  def afterAll() = system.terminate()
}




