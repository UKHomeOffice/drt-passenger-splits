package api

import akka.actor.ActorRef
import scala.concurrent.ExecutionContext
import spray.routing.{Route, Directives}
import core.MessengerActor

class UpcheckService(implicit executionContext: ExecutionContext)
  extends Directives with DefaultJsonFormats {
   val route: Route =
     path("upcheck") {
       get {
         complete(
           <h1>Hello world</h1>
         )
       }
     }
}