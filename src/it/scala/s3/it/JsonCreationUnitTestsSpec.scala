package s3.it

import org.specs2.mutable.Specification
import parsing.PassengerInfoParser

//trait ChromaParser extends ChromaConfig with WithSendAndReceive {
////  self: WithSendAndReceive =>
//  implicit val system: ActorSystem
//
//  import system.dispatcher
//
//  import ChromaParserProtocol._
//
//  def log = LoggerFactory.getLogger(getClass)
//
//  def tokenPipeline: HttpRequest => Future[ChromaToken] = (
//    addHeader(Accept(MediaTypes.`application/json`))
//      ~> sendAndReceive
//      ~> unmarshal[ChromaToken]
//    )
//
//  def livePipeline(token: String): HttpRequest => Future[List[ChromaSingleFlight]] = {
//    println(s"Sending request for $token")
//    val logRequest: HttpRequest => HttpRequest = { r => log.debug(r.toString); r }
//    val logResponse: HttpResponse => HttpResponse = {
//      resp =>
//        log.info("Response Object: "  + resp); resp
//    }
//    val logUnMarshalled: List[ChromaSingleFlight] => List[ChromaSingleFlight] = { resp => log.info("Unmarshalled Response Object: " + resp); resp }
//
//    (
//      addHeaders(Accept(MediaTypes.`application/json`), Authorization(OAuth2BearerToken(token)))
//        ~> logRequest
//        ~> sendReceive
//        ~> logResponse
//        ~> unmarshal[List[ChromaSingleFlight]]
//        ~> logUnMarshalled
//      )
//  }
//
//  def currentFlights: Future[Seq[ChromaSingleFlight]] = {
//    val eventualToken: Future[ChromaToken] = tokenPipeline(Post(tokenUrl, chromaTokenRequestCredentials))
//    def eventualLiveFlights(accessToken: String) = livePipeline(accessToken)(Get(url))
//
//    for {
//      t <- eventualToken
//      cr <- eventualLiveFlights(t.access_token)
//    } yield {
//      cr
//    }
//  }
//
//}
class JsonCreationUnitTestsSpec extends Specification {

  import PassengerInfoParser._
  import FlightPassengerInfoProtocol._
  import PassengerInfoParser._
  import spray.json._

  val sampleJson =
    """{
      |  "EventCode": "DC",
      |  "DeparturePortCode": "BRE",
      |  "VoyageNumberTrailingLetter": "",
      |  "ArrivalPortCode": "STN",
      |  "DeparturePortCountryCode": "DEU",
      |  "VoyageNumber": "3631",
      |  "VoyageKey": "517c62be54d6822e33424d0fd7057449",
      |  "ScheduledDateOfDeparture": "2016-03-02",
      |  "ScheduledDateOfArrival": "2016-03-02",
      |  "CarrierType": "AIR",
      |  "CarrierCode": "FR",
      |  "ScheduledTimeOfArrival": "06:00:00",
      |  "PassengerList": [
      |    {
      |      "DocumentIssuingCountryCode": "MAR",
      |      "PersonType": "P",
      |      "DocumentLevel": "Primary",
      |      "Age": "21",
      |      "DisembarkationPortCode": "STN",
      |      "InTransitFlag": "N",
      |      "DisembarkationPortCountryCode": "GBR",
      |      "NationalityCountryEEAFlag": "",
      |      "DocumentType": "P",
      |      "PoavKey": "000d6ab0f4929d8a92a99b83b0c35cfc",
      |      "NationalityCountryCode": "MAR"
      |    }]
      |}""".stripMargin


  "Can parse something" in {
    val parsedAndConverted = sampleJson.parseJson.convertTo[VoyagePassengerInfo]
    parsedAndConverted should beEqualTo(
      VoyagePassengerInfo(EventCodes.DoorsClosed, "STN", "3631", "FR", "2016-03-02", "06:00:00",
        PassengerInfoJson(Some("P"), "MAR", "", Some("21")) :: Nil)
    )
  }
  "Can produce json from a VoyagePassengerInfo" in {
    val vpi = VoyagePassengerInfo(EventCodes.DoorsClosed, "STN", "3631", "FR", "2016-03-02", "06:00:00",
      PassengerInfoJson(Some("P"), "MAR", "", Some("21")) :: Nil)

    vpi.toJson should beEqualTo(
      """{"ScheduledDateOfArrival":"2016-03-02",
        |"EventCode":"DC","PassengerList":[
        | {"DocumentType":"P","DocumentIssuingCountryCode":"MAR","NationalityCountryEEAFlag":"","Age":"21"}],
        | "ScheduledTimeOfArrival":"06:00:00","CarrierCode":"FR","VoyageNumber":"3631","ArrivalPortCode":"STN"
        | }""".stripMargin.parseJson)
  }
  //  "Can produce json from a VoyagePassengerSplits" in {
  //
  //  }
  getClass.getClassLoader.getResourceAsStream("s3content")
}
