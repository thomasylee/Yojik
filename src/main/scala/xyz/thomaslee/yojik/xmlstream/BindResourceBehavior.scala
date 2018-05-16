package xyz.thomaslee.yojik.xmlstream

import akka.actor.ActorRef
import akka.actor.Actor.Receive
import akka.event.LoggingAdapter

import xyz.thomaslee.yojik.xml.{ StanzaHandlingActor, XmlParsingActor, XmlTag }

object BindResourceBehavior {
  def apply(log: LoggingAdapter,
            self: XmlStreamActor,
            xmlParser: ActorRef,
            prefix: Option[String],
            tlsActor: ActorRef,
            stanzaHandler: ActorRef,
            user: String): Receive = {
    case XmlParsingActor.TagReceived(tag: XmlTag) =>
      stanzaHandler ! StanzaHandlingActor.HandleStanza(tag)
    case message => {
      log.debug("Logged in as " + user)
      HandleTlsMessage(log, self, xmlParser, prefix, tlsActor)(message)
    }
  }
}
