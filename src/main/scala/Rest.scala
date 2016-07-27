import akka.actor.ActorRef
import restapi.paxSplits.PaxSplitsRestApi
import core.{BootedCore, CoreActors}
import s3.SimpleS3Poller
import web.Web

object Rest extends App with BootedCore with CoreActors with PaxSplitsRestApi with Web with SimpleS3Poller


