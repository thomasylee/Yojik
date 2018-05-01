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

object XmlStreamActor {
  case class PassToClient(message: ByteString)
  case class ProcessMessage(message: ByteString)
  case class ProcessDecryptedMessage(message: ByteString)
  case object Stop

  val ValidStreamNamespace = "http://etherx.jabber.org/streams"
}

class XmlStreamActor extends Actor with ActorLogging {
  var xmlOutputStream = new PipedOutputStream
  var xmlInputStream = new PipedInputStream(xmlOutputStream)

  val tcpConnectionActor = context.parent

  def stop: Unit = {
    context.stop(self)
    try { xmlOutputStream.close } catch { case _: Throwable => {} }
    context.parent ! ConnectionActor.Disconnect
  }
  override def postStop: Unit = log.debug("XmlStreamActor stopped")

  def become(receive: Receive): Unit = context.become(receive)

  def receive: Receive = OpenStreamBehavior(
    log,
    this,
    context.actorOf(
      XmlParsingActor.props(xmlInputStream),
      "xml-parsing-actor-" + Random.alphanumeric.take(ConfigMap.randomCharsInActorNames).mkString))

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

    context.parent ! ConnectionActor.ReplyToSender(ByteString(
      openStreamIfNeeded + error.toString))

    stop
  }

  def handleStreamErrorWithTls(error: XmlStreamError, tlsActor: ActorRef): Unit = {
    error.message match {
      case Some(errorMessage) => log.warning(s"${ error.errorType }: $errorMessage")
      case None => log.warning(error.errorType)
    }

    tlsActor ! TlsActor.SendEncryptedToClient(ByteString(error.toString))

    stop
  }

  def validateOpenStreamRequest(request: XmlParsingActor.OpenStream): Option[XmlStreamError] = request.namespaceUri match {
    case Some(uri) if uri == XmlStreamActor.ValidStreamNamespace => None
    case _ => Some(new InvalidNamespaceError(request.prefix, request.namespaceUri))
  }

  def buildOpenStreamTag(request: XmlParsingActor.OpenStream): String =
    XmlResponse.openStream(
      prefix = request.prefix,
      contentNamespace = Some("jabber:client"),
      streamId = "abc",
      recipient = request.attributes.get("from"))

  def recreateXmlParser(xmlParser: ActorRef): ActorRef = {
    context.stop(xmlParser)
    try { xmlOutputStream.close } catch { case _: Throwable => {} }

    xmlOutputStream = new PipedOutputStream
    xmlInputStream = new PipedInputStream(xmlOutputStream)

    context.actorOf(
      XmlParsingActor.props(xmlInputStream),
      "xml-parsing-actor-" + Random.alphanumeric.take(
        ConfigMap.randomCharsInActorNames).mkString)
  }
}
