package xyz.thomaslee.yojik.xmlstream

import akka.actor.{ Actor, ActorLogging, ActorRef }
import akka.util.ByteString
import java.io.{ PipedInputStream, PipedOutputStream }
import scala.util.Random

import xyz.thomaslee.yojik.ConnectionActor
import xyz.thomaslee.yojik.config.ConfigMap
import xyz.thomaslee.yojik.tls.TlsActor
import xyz.thomaslee.yojik.xml.{
  InvalidNamespaceError, XmlParsingActor, XmlResponse, XmlStreamError
}

/**
 * Contains messages pertaining to actors with the XmlStreamManaging trait.
 */
object XmlStreamManaging {
  /** Indicates that the message should be processed by the actor. */
  case class ProcessMessage(message: ByteString)

  /** Indicates that the message should be passed on to the client. */
  case class PassToClient(message: ByteString)

  /**
   * Indicates that the message is a decrypted message from a TLS-encrypted
   * connection and should be processed by the actor.
   */
  case class ProcessDecryptedMessage(message: ByteString)

  /**
   * Indicates that the actor should stop, disconnect, and close the XML parser.
   */
  case object Stop

  /** Indicates that a different actor is now managing the XML stream. */
  case class XmlStreamManagerChanged(xmlStreamManager: ActorRef)
}

/**
 * Provides variables and methods for actors that will be proxying between
 * connections and XML parsers, and processing stanzas.
 */
trait XmlStreamManaging extends ActorLogging with Actor {
  var xmlOutputStream = new PipedOutputStream
  var xmlInputStream = new PipedInputStream(xmlOutputStream)

  /**
   * Returns an [[akka.actor.ActorRef]] to the actor responsible for the connection.
   *
   * @return an [[akka.actor.ActorRef]] to the actor responsible for the connection
   */
  def connectionActor: ActorRef

  /**
   * Stops this actor and sends its connection actor a ConnectionActor.Disconnect
   * message.
   */
  def stop: Unit = {
    context.stop(self)
    try { xmlOutputStream.close } catch { case _: Throwable => {} }
    connectionActor ! ConnectionActor.Disconnect
  }
  override def postStop: Unit = log.debug(getClass.getName + " stopped")

  /**
   * Responds to stream errors by closing the XML stream in the appropriate manner
   * and stops this actor.
   *
   * @param error the [[xyz.thomaslee.yojik.xml.XmlStreamError]] to handle
   * @param includeOpenStream whether the XML response requires an opening tag
   */
  def handleStreamError(error: XmlStreamError, includeOpenStream: Boolean = false): Unit = {
    error.message match {
      case Some(errorMessage) => log.warning(s"${ error.errorType }: $errorMessage")
      case None => log.warning(error.errorType)
    }

    val openStreamIfNeeded =
      if (includeOpenStream) {
        buildOpenStreamTag(XmlParsingActor.OpenStream(
          error.prefix, Some(XmlStreamActor.ValidStreamNamespace), Map())) + "\n"
      }
      else {
        ""
      }

    connectionActor ! ConnectionActor.ReplyToSender(ByteString(
      openStreamIfNeeded + error.toString))

    stop
  }

  /**
   * Responds to stream errors during a TLS session by sending the encrypted
   * XML response to the client and stopping this actor.
   *
   * @param error the [[xyz.thomaslee.yojik.xml.XmlStreamError]] to handle
   * @param tlsActor an ActorRef to a [[xyz.thomaslee.yojik.tls.TlsActor]] to
   *   encrypt the XML response and send it back to the client
   */
  def handleStreamErrorWithTls(error: XmlStreamError, tlsActor: ActorRef): Unit = {
    error.message match {
      case Some(errorMessage) => log.warning(s"${ error.errorType }: $errorMessage")
      case None => log.warning(error.errorType)
    }

    tlsActor ! TlsActor.SendEncryptedToClient(ByteString(error.toString))

    stop
  }

  /**
   * Returns an Option to an [[xyz.thomaslee.yojik.xml.XmlStreamError]] to
   * handle if the request to open an XML stream is invalid, or None if the
   * request is valid.
   *
   * @param request the request to open an XML stream
   * @return an Option to an error if the request is invalid, or else None
   */
  def validateOpenStreamRequest(request: XmlParsingActor.OpenStream): Option[XmlStreamError] =
    request.namespaceUri match {
      case Some(uri) if uri == XmlStreamActor.ValidStreamNamespace => None
      case _ => Some(new InvalidNamespaceError(request.prefix, request.namespaceUri))
    }

  /**
   * Returns an opening XML stream tag as defined by RFC-6120.
   *
   * @param request the request to open an XML stream
   * @return the opening XML stream tag
   */
  def buildOpenStreamTag(request: XmlParsingActor.OpenStream): String =
    XmlResponse.openStream(
      prefix = request.prefix,
      contentNamespace = Some("jabber:client"),
      streamId = "abc",
      recipient = request.attributes.get("from"))

  /**
   * Recreates the XML stream parser to reset the XML stream state. For example,
   * after the stream negotiates TLS, the XML stream must be restarted.
   *
   * @param xmlParser the ActorRef to the [[xyz.thomaslee.yojik.xml.XmlParsingActor]]
   *   that needs to be stopped and replaced with a new instance
   * @return an ActorRef to a new [[xyz.thomaslee.yojik.xml.XmlParsingActor]]
   */
  def recreateXmlParser(xmlParser: ActorRef): ActorRef = {
    context.stop(xmlParser)
    try { xmlOutputStream.close } catch { case _: Throwable => {} }

    xmlOutputStream = new PipedOutputStream
    xmlInputStream = new PipedInputStream(xmlOutputStream)

    context.system.actorOf(
      XmlParsingActor.props(self, xmlInputStream),
      "xml-parsing-actor-" + Random.alphanumeric.take(
        ConfigMap.randomCharsInActorNames).mkString)
  }
}
