package xyz.thomaslee.yojik.xmlstream

import akka.actor.ActorRef
import akka.actor.Actor.Receive
import akka.event.LoggingAdapter

object BindResourceBehavior {
  def apply(log: LoggingAdapter, self: XmlStreamActor, xmlParser: ActorRef, prefix: Option[String], tlsActor: ActorRef, user: String): Receive = {
    case message => {
      log.debug("Logged in as " + user)
      HandleTlsMessage(log, self, xmlParser, prefix, tlsActor)(message)
    }
  }
}
