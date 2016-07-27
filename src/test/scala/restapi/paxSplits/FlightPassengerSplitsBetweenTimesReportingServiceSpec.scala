package restapi.paxSplits

import akka.actor._
import akka.event.Logging
import core.{PassengerInfoByPortRouter, PassengerInfoRouterActor}
import org.specs2.mutable.Specification
import org.specs2.specification.AfterAll
import parsing.PassengerInfoParser.{PassengerInfoJson, EventCodes, VoyagePassengerInfo}
import spray.http.StatusCodes
import spray.json.JsValue
import spray.routing.Directives
import spray.testkit.{DeadLetterListener, DeadLetterFixture, Specs2RouteTest}
import spray.json._
import DefaultJsonProtocol._


class FlightPassengerSplitsBetweenTimesReportingServiceSpec extends Specification
  with AfterAll with Directives with Specs2RouteTest {
  def actorRefFactory = system
  isolated

  val myListenerActorRef = system.actorOf(Props[DeadLetterListener])
  system.eventStream.subscribe(myListenerActorRef, classOf[DeadLetter])

  val log = Logging(system, classOf[FlightPassengerSplitsReportingServiceSpec])
  "The routing infrastructure should support" >> {
    val aggregationRef: ActorRef = system.actorOf(Props[PassengerInfoByPortRouter])
    val serviceAgg = new FlightPassengerSplitsReportingService(system, aggregationRef)

    "Given four flights," >> {

      val vpis = VoyagePassengerInfo(EventCodes.DoorsClosed,
        "LHR", "123", "BA", "2015-02-01", "22:11:00", PassengerInfoJson(Some("P"), "GBR", "EEA", None) :: Nil) ::
        VoyagePassengerInfo(EventCodes.DoorsClosed,
          "STN", "934", "RY", "2015-02-01", "13:48:00", PassengerInfoJson(Some("P"), "DEU", "EEA", None) :: Nil) ::
        VoyagePassengerInfo(EventCodes.DoorsClosed,
          "STN", "387", "EZ", "2015-02-01", "14:55:00", PassengerInfoJson(Some("P"), "GBR", "EEA", None) :: Nil) ::
        VoyagePassengerInfo(EventCodes.DoorsClosed,
          "STN", "999", "EZ", "2015-02-02", "14:55:00", PassengerInfoJson(Some("P"), "GBR", "EEA", None) :: Nil) ::
        Nil
      val batchSender = system.actorOf(PassengerInfoBatchActor.props(Actor.noSender, aggregationRef, vpis, "batchsend"))
      batchSender ! "Begin"
      " a route to get flight-pax-splits for a destination port between fromTime and toTime which covers just two of the flights" +
        "should return a list of paxSplits for those two flights" in {
        Get("/flight-pax-splits/dest-STN/?from=20150201T1300&to=20150202T0000") ~>
          serviceAgg.route ~> check {
          log.info("response was" + responseAs[String])
          assert(response.status === StatusCodes.OK)
          val json: JsValue = responseAs[String].parseJson

          val expected =
            """
              |[
              |{"paxSplits":[
              | {"passengerType":"eea-machine-readable","queueType":"desk","paxCount":1},
              | {"passengerType":"eea-machine-readable","queueType":"egate","paxCount":0}
              |],
              |   "carrierCode":"EZ","destinationPort":"STN","totalPaxCount":1,
              |   "scheduledArrivalDateTime":"2015-02-01T14:55:00","voyageNumber":"387"
              |},
              |{"paxSplits":[
              | {"passengerType":"eea-machine-readable","queueType":"desk","paxCount":1},
              | {"passengerType":"eea-machine-readable","queueType":"egate","paxCount":0}
              |],
              | "carrierCode":"RY","destinationPort":"STN","totalPaxCount":1,
              | "scheduledArrivalDateTime":"2015-02-01T13:48:00","voyageNumber":"934"
              |}
              |]""".stripMargin.parseJson.convertTo[List[Map[String, JsValue]]]
          println(json.prettyPrint)
          println(expected)
          json.convertTo[List[Map[String, JsValue]]] should containAllOf(expected)
        }
      }
      " a route to get flight-pax-splits for just the LHR flight" +
        "should return a list of paxSplits for that single LHR flight" in {
        Get("/flight-pax-splits/dest-LHR/?from=20150201T1300&to=20150202T0000") ~>
          serviceAgg.route ~> check {
          log.info("response was" + responseAs[String])
          assert(response.status === StatusCodes.OK)
          val json: JsValue = responseAs[String].parseJson

          val expected =
            """
              |[
              |{"paxSplits":[
              | {"passengerType":"eea-machine-readable","queueType":"desk","paxCount":1},
              | {"passengerType":"eea-machine-readable","queueType":"egate","paxCount":0}
              |],
              |   "carrierCode":"BA","destinationPort":"LHR","totalPaxCount":1,
              |   "scheduledArrivalDateTime":"2015-02-01T22:11:00","voyageNumber":"123"
              |}
              |]""".stripMargin.parseJson
          println(json.prettyPrint)
          println(expected.prettyPrint)
          json should beEqualTo(expected)
        }
      }
    }
  }

  def afterAll() = system.terminate()
}
