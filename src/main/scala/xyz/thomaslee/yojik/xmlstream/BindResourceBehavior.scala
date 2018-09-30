package xyz.thomaslee.yojik.xmlstream

import akka.actor.ActorRef
import akka.actor.Actor.Receive
import akka.event.LoggingAdapter

import xyz.thomaslee.yojik.xml.{ StanzaHandlingActor, XmlParsingActor, XmlTag }

/** Handles requests to bind a resource to the XML stream. */
object BindResourceBehavior {
  /**
   * Handles the messages that attempt to bind a resource to the XML stream.
   *
   * @param log the [[akka.event.LoggingAdapter]] to use for logging
   * @param self the [[xyz.thomaslee.yojik.xmlstream.XmlStreamActor]] instance
   *   that is handling unauthenticated XML requests and responses
   * @param xmlParser an ActorRef to the [[xyz.thomaslee.yojik.xml.XmlParsingActor]]
   *   responsible for parsing XML
   * @param prefix the stream prefix for the opening tag of the XML stream
   * @param tlsActor an ActorRef to the [[xyz.thomaslee.yojik.tls.TlsActor]]
   *   responsible for handling the TLS session
   * @param stanzaHandler an ActorRef to the
   *   [[xyz.thomaslee.yojik.xml.StanzaHandlingActor]] responsible for handling
   *   XML stanzas
   * @param user the authenticated username
   */
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
