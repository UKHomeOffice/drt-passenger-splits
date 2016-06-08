package core

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.{ConfigFactory, Config}
import core.ChromaParser.{ChromaSingleFlight, ChromaToken}
import http.ProdSendAndReceive
import org.specs2.mutable.SpecificationLike
import spray.client.pipelining._
import spray.http.HttpHeaders.{Authorization, Accept}
import spray.http._
import spray.json.DefaultJsonProtocol
import spray.httpx.SprayJsonSupport._
import scala.concurrent.{ExecutionContext, Await, Future}

trait ChromaConfig {
  lazy val config = ConfigFactory.load()

  val chromaTokenRequestCredentials = FormData(Seq(
    "username" -> config.getString("chromausername"),
    "password" -> config.getString("chromapassword"),
    "grant_type" -> "password"
  ))
  val tokenUrl: String = "https://ukbf-api.magairports.com/chroma/token"

  val url: String = "https://ukbf-api.magairports.com/chroma/live/man"
}

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


  // to get the username and password you need an application.conf
  // that file shouldn't be checked in because of 12-factor appiness


  "A Chroma client" >> {
    "be able to get a token" in {
      val sut = new ChromaParser with ProdSendAndReceive {
        implicit val system: ActorSystem = test.system
        private val pipeline = tokenPipeline
        val response: Future[ChromaToken] = pipeline(Post(tokenUrl, chromaTokenRequestCredentials))

        response onSuccess {
          case s => println(s)
        }

        def await = Await.result(response, 10 seconds) must not equalTo (ChromaToken("", "", 0))
      }
      sut.await
    }
    "given a token " in {
      val sut = new ChromaParser with ProdSendAndReceive {
        implicit val system: ActorSystem = test.system
//        private val pipeline = tokenPipeline
      }
      Await.result(sut.currentFlights, 10 seconds) must not beEmpty
    }
  }
}
