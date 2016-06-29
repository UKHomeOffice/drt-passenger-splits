package api

import akka.actor.{ActorRef, Props}
import akka.event.Logging
import core.{PassengerInfoRouterActor}
import org.specs2.specification.AfterAll
import parsing.PassengerInfoParser._
import spray.testkit.Specs2RouteTest
import spray.routing.Directives
import org.specs2.mutable.Specification
import spray.http.{DateTime, HttpResponse}

import spray.json._
import DefaultJsonProtocol._

class RegistrationServiceSpec extends Specification with Directives with Specs2RouteTest {

  "The routing infrastructure should support" >> {
    "the most simple and direct route" in {
      Get() ~> complete(HttpResponse()) ~> (_.response) === HttpResponse()
    }
  }
}





