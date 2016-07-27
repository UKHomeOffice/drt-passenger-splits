package restapi.workload

import akka.actor._
import akka.event.Logging
import core.{PassengerQueueTypes, PassengerInfoByPortRouter}
import core.PassengerInfoRouterActor.VoyagePaxSplits
import core.PassengerQueueTypes.{Desks, PaxTypes, PaxTypeAndQueueCount}
import core.PassengerSplitsCalculator.PaxSplits
import org.specs2.mutable.Specification
import org.specs2.specification.AfterAll
import parsing.PassengerInfoParser.{EventCodes, PassengerInfoJson, VoyagePassengerInfo}
import restapi.paxSplits.{FlightPassengerSplitsReportingService, FlightPassengerSplitsReportingServiceSpec, PassengerInfoBatchActor}
import spray.http.DateTime
import spray.routing.Directives
import spray.testkit.{DeadLetterFixture, Specs2RouteTest}


class WorkloadBetweenTimesServiceSpec extends Specification
  with AfterAll with Directives with Specs2RouteTest
  with DeadLetterFixture {
  def actorRefFactory = system

  isolated

  val aggregationRef: ActorRef = system.actorOf(Props[PassengerInfoByPortRouter])
  val serviceAgg = new FlightPassengerSplitsReportingService(system, aggregationRef)

  val log = Logging(system, classOf[FlightPassengerSplitsReportingServiceSpec])
  "Given a seq of VoyagePaxSplits. The workload service can answer questions about workload between times" >> {
    val paxSplits =
      VoyagePaxSplits(
        "STN", "934", "RY", 250, DateTime(2015, 2, 1, 14, 55),
        PaxTypeAndQueueCount(PaxTypes.EEAMACHINEREADABLE, Desks.eeaDesk, 100) ::
        PaxTypeAndQueueCount(PaxTypes.EEANONMACHINEREADABLE, Desks.eeaDesk, 50) ::
        Nil
      ) :: Nil

    Get("/workload/dest-STN/?from=20150201T1300&to=20150202T0000") ~>
      serviceAgg.route ~> check {
      false
    }
  }

  def afterAll() = system.terminate()
}
