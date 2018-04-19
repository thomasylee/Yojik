package xyz.thomaslee.yojik.messages

import akka.actor.{ Actor, ActorLogging, PoisonPill, Props }
import java.io.{ PipedInputStream, PipedOutputStream }
import org.xml.sax.SAXParseException
import scala.util.Random
import scala.xml.XML

import xyz.thomaslee.yojik.ConnectionActor

object MessageActor {
  case class ProcessMessage(message: String)
  case object Stop

  val StreamNamespaceRegex = """<([^:]+:)?stream """.r

  val ValidStreamNamespace = "http://etherx.jabber.org/streams"
  val ValidStartTlsNamespace = "urn:ietf:params:xml:ns:xmpp-tls"
}

class MessageActor extends Actor with ActorLogging {
  val xmlOutputStream = new PipedOutputStream

  val xmlInputStream = new PipedInputStream(xmlOutputStream)

  lazy val xmlParsingActor = context.actorOf(
    XmlParsingActor.props(xmlInputStream),
    "xml-parsing-actor-" + Random.alphanumeric.take(10).mkString)

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
      xmlOutputStream.write(message.getBytes)
    }
    case error: XmlStreamError => handleStreamError(error, true)
    case request: XmlParsingActor.OpenStream =>
      validateOpenStreamRequest(request) match {
        case Some(error: XmlStreamError) => handleStreamError(error, true)
        case None => {
          context.parent ! ConnectionActor.ReplyToSender(
            buildOpenStreamTag(request) + "\n" +
            XmlResponse.startTlsStreamFeature(request.prefix))
          context.become(startTls(request.prefix))
        }
      }
    case XmlParsingActor.CloseStream(streamPrefix) => {
      context.parent ! ConnectionActor.ReplyToSender(
        XmlResponse.closeStream(streamPrefix))

      stop
    }
    case MessageActor.Stop => stop
  }

  def startTls(prefix: Option[String]): Receive = {
    case MessageActor.ProcessMessage(message) => {
      xmlParsingActor ! XmlParsingActor.Parse
      xmlOutputStream.write(message.getBytes)
    }
    case error: XmlStreamError => handleStreamError(error, true)
    case tls: XmlParsingActor.StartTls => handleStartTls(tls) match {
      case Some(error: StartTlsError) => {
        log.warning(s"StartTls failure: ${error.message}")
        context.parent ! ConnectionActor.ReplyToSender(
          error.toString + "\n" + XmlResponse.closeStream(prefix))
        stop
      }
      case None => {
        context.parent ! ConnectionActor.ReplyToSender(XmlResponse.proceedWithTls)
        context.become(negotiateTls(prefix))
      }
    }
    case XmlParsingActor.CloseStream(streamPrefix) => {
      context.parent ! ConnectionActor.ReplyToSender(
        XmlResponse.closeStream(streamPrefix))

      stop
    }
    case MessageActor.Stop => stop
  }

  def negotiateTls(prefix: Option[String]): Receive = {
    case MessageActor.Stop => stop
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

    context.parent ! ConnectionActor.ReplyToSender(openStreamIfNeeded + error.toString)

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
