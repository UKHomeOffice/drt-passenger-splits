package core

import akka.actor.ActorSystem
import core.ChromaParser.{ChromaSingleFlight, ChromaToken, ChromaParserProtocol}
import http.WithSendAndReceive
import org.slf4j.LoggerFactory
import org.specs2.mutable.SpecificationLike
import spray.client.pipelining._
import spray.http.HttpHeaders.{Accept, Authorization}
import spray.http.{HttpResponse, HttpRequest, MediaTypes, OAuth2BearerToken}
import spray.httpx.SprayJsonSupport._
import spray.json.DefaultJsonProtocol

import scala.concurrent.{ExecutionContext, Future}

object ChromaParser {

  case class ChromaToken(access_token: String, token_type: String, expires_in: Int)

  case class ChromaSingleFlight(Operator: String,
                                Status: String,
                                EstDT: String,
                                ActDT: String,
                                EstChoxDT: String,
                                ActChoxDT: String,
                                Gate: String,
                                Stand: String,
                                MaxPax: Int,
                                ActPax: Int,
                                TranPax: Int,
                                RunwayID: String,
                                BaggageReclaimId: String,
                                FlightID: Int,
                                AirportID: String,
                                Terminal: String,
                                ICAO: String,
                                IATA: String,
                                Origin: String,
                                SchDT: String)

  object ChromaParserProtocol extends DefaultJsonProtocol {
    implicit val chromaTokenFormat = jsonFormat3(ChromaToken)
    implicit val chromaSingleFlightFormat = jsonFormat20(ChromaSingleFlight)
  }

}
trait ChromaParser extends ChromaConfig with WithSendAndReceive {
//  self: WithSendAndReceive =>
  implicit val system: ActorSystem

  import system.dispatcher

  import ChromaParserProtocol._

  def log = LoggerFactory.getLogger(getClass)

  def tokenPipeline: HttpRequest => Future[ChromaToken] = (
    addHeader(Accept(MediaTypes.`application/json`))
      ~> sendAndReceive
      ~> unmarshal[ChromaToken]
    )

  def livePipeline(token: String): HttpRequest => Future[List[ChromaSingleFlight]] = {
    println(s"Sending request for $token")
    val logRequest: HttpRequest => HttpRequest = { r => log.debug(r.toString); r }
    val logResponse: HttpResponse => HttpResponse = {
      resp =>
        log.info("Response Object: "  + resp); resp
    }
    val logUnMarshalled: List[ChromaSingleFlight] => List[ChromaSingleFlight] = { resp => log.info("Unmarshalled Response Object: " + resp); resp }

    (
      addHeaders(Accept(MediaTypes.`application/json`), Authorization(OAuth2BearerToken(token)))
        ~> logRequest
        ~> sendReceive
        ~> logResponse
        ~> unmarshal[List[ChromaSingleFlight]]
        ~> logUnMarshalled
      )
  }

  def currentFlights: Future[Seq[ChromaSingleFlight]] = {
    val eventualToken: Future[ChromaToken] = tokenPipeline(Post(tokenUrl, chromaTokenRequestCredentials))
    def eventualLiveFlights(accessToken: String) = livePipeline(accessToken)(Get(url))

    for {
      t <- eventualToken
      cr <- eventualLiveFlights(t.access_token)
    } yield {
      cr
    }
  }

}

class ChromaParserSpec extends
  SpecificationLike {

  import ChromaParserProtocol._

  //  sequential
  "Parse chroma response" >> {
    "Given a chroma response list with a single Flight we can parse it" in {
      import spray.json._
      val chromaFlightJson: JsValue =
        """
          |[{
          |"Operator":"Flybe"
          	 |,"Status":"On Chocks"
          	 |,"EstDT":"2016-06-02T10:55:00Z"
          	 |,"ActDT":"2016-06-02T10:55:00Z"
          	 |,"EstChoxDT":"2016-06-02T11:01:00Z"
          	 |,"ActChoxDT":"2016-06-02T11:05:00Z"
          	 |,"Gate":"46"
          	 |,"Stand":"44R"
          	 |,"MaxPax":78
          	 |,"ActPax":51
          	 |,"TranPax":0
          	 |,"RunwayID":"05L"
          	 |,"BaggageReclaimId":"05"
          	 |,"FlightID":14710007
          	 |,"AirportID":"MAN"
          	 |,"Terminal":"T3"
          	 |,"ICAO":"BEE1272"
          	 |,"IATA":"BE1272"
          	 |,"Origin":"AMS"
          	 |,"SchDT":"2016-06-02T09:55:00Z"}]
        """.stripMargin.parseJson

      chromaFlightJson.convertTo[List[ChromaSingleFlight]] should beEqualTo(List(
        ChromaSingleFlight("Flybe",
          "On Chocks",
          "2016-06-02T10:55:00Z",
          "2016-06-02T10:55:00Z",
          "2016-06-02T11:01:00Z",
          "2016-06-02T11:05:00Z",
          "46",
          "44R",
          78,
          51,
          0,
          "05L",
          "05",
          14710007,
          "MAN",
          "T3",
          "BEE1272",
          "BE1272",
          "AMS",
          "2016-06-02T09:55:00Z")))
    }
  }
  isolated
}
