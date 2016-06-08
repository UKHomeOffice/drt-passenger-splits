package http

import akka.actor.ActorSystem
import spray.http.{HttpResponse, HttpRequest}
import spray.client.pipelining._
import scala.concurrent.Future

trait WithSendAndReceive {
  def sendAndReceive:  (HttpRequest) => Future[HttpResponse]
}

trait ProdSendAndReceive extends WithSendAndReceive {
  implicit val system: ActorSystem
  import system.dispatcher
  override def sendAndReceive = sendReceive
}

