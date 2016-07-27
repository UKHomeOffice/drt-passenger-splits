package spray.testkit

import akka.actor.{Actor, ActorLogging}

class DeadLetterListener extends Actor with ActorLogging {
  def receive = {
    case dl => {
      log.warning(s"DeadLetter: ${dl}")
    }
  }
}
