package xyz.thomaslee.yojik

import akka.actor.{ Actor, ActorLogging, ActorRef, DeadLetter }
import akka.util.ByteString

import xyz.thomaslee.yojik.messages.MessageActor

object DeadLetterActor {
  val MessageActorPrefix = "message-actor-"
  val TcpConnectionPrefix = "tcp-connection-actor-"
  val TcpServerName = "tcp-server"
  val XmlParsingActorPrefix = "xml-parsing-actor-"
}

class DeadLetterActor extends Actor with ActorLogging {
  def receive: Receive = {
    case DeadLetter(msg: ByteString, from, to) => {
      val fromName = from.path.name
      val toName = to.path.name
      if (!fromName.startsWith(DeadLetterActor.XmlParsingActorPrefix) &&
          !toName.startsWith(DeadLetterActor.XmlParsingActorPrefix) &&
          msg != ConnectionActor.Disconnect &&
          msg != MessageActor.Stop) {
        log.warning("Message failed to send from " + fromName + " to " +
          toName + ": " + msg.utf8String)
      }
    }
  }
}
