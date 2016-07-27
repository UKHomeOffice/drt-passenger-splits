package restapi.upcheck

import restapi.paxSplits.DefaultJsonFormats
import spray.routing.{Directives, Route}

import scala.concurrent.ExecutionContext

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