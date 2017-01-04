import akka.actor.ActorRef
import api.Api
import core.{BootedCore, CoreActors}
import s3.{SimpleAtmosPoller, SimpleS3Poller}
import web.Web

object Rest extends App with BootedCore with CoreActors with Api with Web with SimpleAtmosPoller    {
  val res = this.streamAllThisToPrintln
  val toActor = this.streamAllThis
}


