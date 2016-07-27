package spray.testkit

import akka.actor.{DeadLetter, Props}

trait DeadLetterFixture {
  self: RouteTest =>
  val deadLetterActorRef = system.actorOf(Props[DeadLetterListener])
  system.eventStream.subscribe(deadLetterActorRef, classOf[DeadLetter])
}
