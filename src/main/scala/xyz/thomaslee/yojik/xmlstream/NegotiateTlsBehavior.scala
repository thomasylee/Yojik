package xyz.thomaslee.yojik.xmlstream

import akka.actor.ActorRef
import akka.actor.Actor.Receive
import akka.event.LoggingAdapter
import akka.util.ByteString

import xyz.thomaslee.yojik.tls.TlsActor
import xyz.thomaslee.yojik.xml.{ XmlParsingActor, XmlResponse, XmlStreamError }

/** Handles requests to negotiate a TLS session. */
object NegotiateTlsBehavior {
  /**
   * Handles the messages that attempt to negotiate a TLS session.
   *
   * @param log the [[akka.event.LoggingAdapter]] to use for logging
   * @param self the [[xyz.thomaslee.yojik.xmlstream.XmlStreamManaging]] actor
   *   instance that is handling unauthenticated XML requests and responses
   * @param xmlParser an ActorRef to the [[xyz.thomaslee.yojik.xml.XmlParsingActor]]
   *   responsible for parsing XML
   * @param prefix the stream prefix for the opening tag of the XML stream
   * @param tlsActor an ActorRef to the [[xyz.thomaslee.yojik.tls.TlsActor]]
   *   responsible for handling the TLS session
   */
  def apply(log: LoggingAdapter, self: XmlStreamManaging, xmlParser: ActorRef, prefix: Option[String], tlsActor: ActorRef): Receive = {
    case request: XmlParsingActor.OpenStream =>
      self.validateOpenStreamRequest(request) match {
        case Some(error: XmlStreamError) =>
          self.handleStreamErrorWithTls(error, tlsActor)
        case None => {
          tlsActor ! TlsActor.SendEncryptedToClient(ByteString(
            self.buildOpenStreamTag(request) + "\n" +
            XmlResponse.saslStreamFeature(request.prefix)))

          self.context.become(SaslAuthenticateBehavior(
            log,
            self,
            xmlParser,
            request.prefix,
            tlsActor))
        }
      }
    case message => HandleTlsMessage(log, self, xmlParser, prefix, tlsActor)(message)
  }
}
