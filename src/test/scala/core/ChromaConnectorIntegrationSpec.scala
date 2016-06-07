package core

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.{ConfigFactory, Config}
import core.ChromaParser.{ChromaSingleFlight, ChromaToken}
import org.specs2.mutable.SpecificationLike
import spray.client.pipelining._
import spray.http.HttpHeaders.{Authorization, Accept}
import spray.http._
import spray.json.DefaultJsonProtocol
import spray.httpx.SprayJsonSupport._
import scala.concurrent.{ExecutionContext, Await, Future}

class ChromaConnectorIntegrationSpec extends TestKit(ActorSystem())
  with SpecificationLike
  with CoreActors
  with Core
  with ImplicitSender {
  test =>

  import scala.concurrent.duration._

  //  import ChromaParser.ChromaParserProtocol._
  //  import scala.concurrent.ExecutionContext.Implicits.global
  import system.dispatcher

  object ChromaParserProtocol extends DefaultJsonProtocol {
    implicit val chromaTokenFormat = jsonFormat3(ChromaToken)
    implicit val chromaSingleFlightFormat = jsonFormat20(ChromaSingleFlight)
  }

  import ChromaParserProtocol._

  val tokenPipeline: HttpRequest => Future[ChromaToken] = (
    addHeader(Accept(MediaTypes.`application/json`))
      ~> sendReceive
      ~> unmarshal[ChromaToken]
    )

  def livePipeline(token: String): HttpRequest => Future[List[ChromaSingleFlight]] = {
    println(s"Sending request for $token")
    (
      addHeaders(
        Accept(MediaTypes.`application/json`),
        Authorization(OAuth2BearerToken(token)))
        ~> sendReceive
        ~> unmarshal[List[ChromaSingleFlight]]
      )
  }

  // to get the username and password you need an application.conf
  // that file shouldn't be checked in because of 12-factor appiness
  lazy val config = ConfigFactory.load()

  val chromaTokenRequestCredentials = FormData(Seq(
    "username" -> config.getString("chromausername"),
    "password" -> config.getString("chromapassword"),
    "grant_type" -> "password"
  ))
  private val tokenUrl: String = "https://ukbf-api.magairports.com/chroma/token"

  private val url: String = "https://ukbf-api.magairports.com/chroma/live/man"

  "A Chroma client" >> {
    "be able to get a token" in {
      val response: Future[ChromaToken] = tokenPipeline(Post(tokenUrl, chromaTokenRequestCredentials))

      response onSuccess {
        case s => println(s)
      }
      Await.result(response, 10 seconds) must not equalTo (ChromaToken("", "", 0))
    }
    "given a token " in {
      val tp = tokenPipeline
      val eventualToken: Future[ChromaToken] = tp(Post(tokenUrl, chromaTokenRequestCredentials))
      def eventualLiveFlights(accessToken: String) = livePipeline(accessToken)(Get(url))

      val ss: Future[Seq[ChromaSingleFlight]] = for {
        t <- eventualToken
        cr <- eventualLiveFlights(t.access_token)
      } yield {
        println(cr)
        cr
      }
      Await.result(ss, 10 seconds) must not beEmpty
    }
  }
}
