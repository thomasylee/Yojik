package xyz.thomaslee.yojik.xmlstream

import akka.actor.{ ActorRef, Props }
import akka.actor.Actor.Receive
import akka.event.LoggingAdapter
import akka.util.ByteString
import scala.util.Random

import xyz.thomaslee.yojik.config.ConfigMap
import xyz.thomaslee.yojik.tls.TlsActor
import xyz.thomaslee.yojik.xml.{
  StanzaHandlingActor, XmlParsingActor, XmlResponse, XmlStreamError
}

/** Handles requests to open a new XML stream for resource binding. */
object OpenStreamForBindResourceBehavior {
  /**
   * Handles the messages that opens a new XML stream for resource binding.
   *
   * @param log the [[akka.event.LoggingAdapter]] to use for logging
   * @param self the [[xyz.thomaslee.yojik.xmlstream.XmlStreamActor]] instance
   *   that is handling unauthenticated XML requests and responses
   * @param xmlParser an ActorRef to the [[xyz.thomaslee.yojik.xml.XmlParsingActor]]
   *   responsible for parsing XML
   * @param prefix the stream prefix for the opening tag of the XML stream
   * @param tlsActor an ActorRef to the [[xyz.thomaslee.yojik.tls.TlsActor]]
   *   responsible for handling the TLS session
   * @param user the authenticated username
   */
  def apply(log: LoggingAdapter, self: XmlStreamActor, xmlParser: ActorRef, prefix: Option[String], tlsActor: ActorRef, user: String): Receive = {
    case request: XmlParsingActor.OpenStream =>
      self.validateOpenStreamRequest(request) match {
        case Some(error: XmlStreamError) =>
          self.handleStreamErrorWithTls(error, tlsActor)
        case None => {
          tlsActor ! TlsActor.SendEncryptedToClient(ByteString(
            self.buildOpenStreamTag(request) +
            XmlResponse.resourceBindFeature(request.prefix)))

          self.context.become(BindResourceBehavior(
            log,
            self,
            xmlParser,
            request.prefix,
            tlsActor,
            self.context.actorOf(
              Props[StanzaHandlingActor],
              "stanza-handling-actor-" + Random.alphanumeric.take(
                ConfigMap.randomCharsInActorNames).mkString),
            user))
        }
      }
    case message => HandleTlsMessage(log, self, xmlParser, prefix, tlsActor)(message)
  }
}
