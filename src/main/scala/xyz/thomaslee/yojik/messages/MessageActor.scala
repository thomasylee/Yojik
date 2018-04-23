package xyz.thomaslee.yojik.messages

import akka.actor.{ Actor, ActorLogging, ActorRef, PoisonPill, Props }
import akka.util.ByteString
import java.io.{ PipedInputStream, PipedOutputStream }
import org.xml.sax.SAXParseException
import scala.util.Random
import scala.xml.XML

import xyz.thomaslee.yojik.ConnectionActor
import xyz.thomaslee.yojik.tls.TlsActor

object MessageActor {
  case class PassToClient(message: ByteString)
  case class ProcessMessage(message: ByteString)
  case class ProcessDecryptedMessage(message: ByteString)
  case object Stop

  val ValidStreamNamespace = "http://etherx.jabber.org/streams"
  val ValidStartTlsNamespace = "urn:ietf:params:xml:ns:xmpp-tls"
}

class MessageActor extends Actor with ActorLogging {
  var xmlOutputStream = new PipedOutputStream

  var xmlInputStream = new PipedInputStream(xmlOutputStream)

  lazy val xmlParsingActor = {
    val xmlParserId = "xml-parsing-actor-" + Random.alphanumeric.take(10).mkString
    println("First: " + xmlParserId)
    context.actorOf(
      XmlParsingActor.props(xmlInputStream),
      "xml-parsing-actor-" + Random.alphanumeric.take(10).mkString)
  }

  def stop = {
    context.stop(self)
    try { xmlOutputStream.close } catch { case _: Throwable => {} }
    try { xmlInputStream.close } catch { case _: Throwable => {} }
    context.parent ! ConnectionActor.Disconnect
  }
  override def postStop = println("MessageActor stopped")

  def receive = openXmlStream

  val openXmlStream: Receive = {
    case MessageActor.ProcessMessage(message) => {
      xmlParsingActor ! XmlParsingActor.Parse
      xmlOutputStream.write(message.toArray[Byte])
    }
    case error: XmlStreamError => handleStreamError(error, true)
    case request: XmlParsingActor.OpenStream =>
      validateOpenStreamRequest(request) match {
        case Some(error: XmlStreamError) => handleStreamError(error, true)
        case None => {
          context.parent ! ConnectionActor.ReplyToSender(ByteString(
            buildOpenStreamTag(request) + "\n" +
            XmlResponse.startTlsStreamFeature(request.prefix)))
          context.become(startTls(request.prefix))
        }
      }
    case XmlParsingActor.CloseStream(streamPrefix) => {
      context.parent ! ConnectionActor.ReplyToSender(ByteString(
        XmlResponse.closeStream(streamPrefix)))

      stop
    }
    case MessageActor.Stop => stop
  }

  def startTls(prefix: Option[String]): Receive = {
    case MessageActor.ProcessMessage(message) => {
      println("Received: " + message.utf8String)
      xmlParsingActor ! XmlParsingActor.Parse
      xmlOutputStream.write(message.toArray[Byte])
    }
    case error: XmlStreamError => handleStreamError(error, true)
    case tls: XmlParsingActor.StartTls => handleStartTls(tls) match {
      case Some(error: StartTlsError) => {
        log.warning(s"StartTls failure: ${error.message}")
        context.parent ! ConnectionActor.ReplyToSender(ByteString(
          error.toString + "\n" + XmlResponse.closeStream(prefix)))
        stop
      }
      case None => {
        context.parent ! ConnectionActor.ReplyToSender(ByteString(
          XmlResponse.proceedWithTls))

        context.stop(xmlParsingActor)
        try { xmlOutputStream.close } catch { case _: Throwable => {} }
        try { xmlInputStream.close } catch { case _: Throwable => {} }

        xmlOutputStream = new PipedOutputStream
        xmlInputStream = new PipedInputStream(xmlOutputStream)

        val xmlParserId = "xml-parsing-actor-" + Random.alphanumeric.take(10).mkString
        println("Second: " + xmlParserId)
        context.become(negotiateTls(
          prefix,
          context.actorOf(
            Props(classOf[TlsActor]),
            "tls-actor-" + Random.alphanumeric.take(10).mkString),
          context.actorOf(
            XmlParsingActor.props(xmlInputStream),
            xmlParserId)))
      }
    }
    case XmlParsingActor.CloseStream(streamPrefix) => {
      context.parent ! ConnectionActor.ReplyToSender(ByteString(
        XmlResponse.closeStream(streamPrefix)))

      stop
    }
    case MessageActor.Stop => stop
  }

  def negotiateTls(prefix: Option[String], tlsActor: ActorRef, xmlParser: ActorRef): Receive = {
    case MessageActor.ProcessMessage(message) => {
      tlsActor ! TlsActor.ProcessMessage(message)
    }
    case MessageActor.ProcessDecryptedMessage(message) => {
      println("Decrypted: " + message.utf8String)
      xmlOutputStream.write(message.toArray[Byte])
      xmlParser ! XmlParsingActor.Parse
    }
    case MessageActor.PassToClient(message) =>
      context.parent ! ConnectionActor.ReplyToSender(message)
    case MessageActor.Stop => {
      tlsActor ! TlsActor.Stop
      stop
    }
    case error: XmlStreamError => {} // handleStreamError(error, true)
    case request: XmlParsingActor.OpenStream =>
      validateOpenStreamRequest(request) match {
        case Some(error: XmlStreamError) => handleStreamError(error, true)
        case None => {
          // TODO: Send SASL stream feature to the client.
          tlsActor ! TlsActor.SendEncryptedToClient(ByteString(
            buildOpenStreamTag(request)))
        }
      }
    case XmlParsingActor.CloseStream(streamPrefix) => {
      // TODO: Figure out why the </stream:stream> tag isn't getting detected.
      tlsActor ! TlsActor.SendEncryptedToClient(ByteString(
        XmlResponse.closeStream(streamPrefix)))

      // stop
    }
  }

  def handleStreamError(error: XmlStreamError, includeOpenStream: Boolean = false) = {
    error.message match {
      case Some(errorMessage) => log.warning(s"${ error.errorType }: $errorMessage")
      case None => log.warning(error.errorType)
    }

    val openStreamIfNeeded =
      if (includeOpenStream)
        buildOpenStreamTag(XmlParsingActor.OpenStream(
          error.prefix, MessageActor.ValidStreamNamespace, Map())) + "\n"
      else ""

    context.parent ! ConnectionActor.ReplyToSender(ByteString(
      openStreamIfNeeded + error.toString))

    stop
  }

  def validateOpenStreamRequest(request: XmlParsingActor.OpenStream): Option[XmlStreamError] =
    if (request.namespaceUri != MessageActor.ValidStreamNamespace)
      Some(new InvalidNamespaceError(request.prefix, Some(request.namespaceUri)))
    else
      None

  def buildOpenStreamTag(request: XmlParsingActor.OpenStream) =
    XmlResponse.openStream(
      prefix = request.prefix,
      contentNamespace = Some("jabber:client"),
      streamId = "abc",
      recipient = request.attributes.get("from"))

  def handleStartTls(request: XmlParsingActor.StartTls): Option[StartTlsError] =
    if (request.namespaceUri != MessageActor.ValidStartTlsNamespace)
      Some(new StartTlsError(request.namespaceUri))
    else
      None
}
