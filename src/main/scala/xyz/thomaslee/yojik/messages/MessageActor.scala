package xyz.thomaslee.yojik.messages

import akka.actor.{ Actor, ActorLogging, PoisonPill, Props }
import java.io.{ PipedInputStream, PipedOutputStream }
import org.xml.sax.SAXParseException
import scala.util.Random
import scala.xml.XML

import xyz.thomaslee.yojik.ConnectionManager

object MessageActor {
  case class ProcessMessage(message: String)

  val StreamNamespaceRegex = """<([^:]+:)?stream """.r

  val ValidStreamNamespace = "http://etherx.jabber.org/streams"
}

class MessageActor extends Actor with ActorLogging {
  var streamNamespace = ""

  val xmlOutputStream = new PipedOutputStream

  val xmlInputStream = new PipedInputStream(xmlOutputStream)

  lazy val xmlParsingActor = context.actorOf(XmlParsingActor.props(xmlInputStream))

  override def preStart = xmlParsingActor ! XmlParsingActor.StartParsing

  def receive = openXmlStream

  def handleStreamError(error: XmlStreamError) = error match {
    case XmlStreamError(_, errorType, message) => {
      message match {
        case Some(errorMessage) => log.warning(s"$errorType: $message")
        case None => log.warning(errorType)
      }
      context.parent ! ConnectionManager.ReplyToSender(
        buildOpenStreamTag(XmlParsingActor.OpenStream(
          error.prefix, MessageActor.ValidStreamNamespace, Map())) + "\n" +
        error.toString)
      context.parent ! ConnectionManager.Disconnect
    }
  }

  val openXmlStream: Receive = {
    case MessageActor.ProcessMessage(message) =>
      xmlOutputStream.write(message.getBytes)
    case error: XmlStreamError => handleStreamError(error)
    case request: XmlParsingActor.OpenStream =>
      validateOpenStreamRequest(request) match {
        case Some(error: XmlStreamError) => handleStreamError(error)
        case None => context.parent ! ConnectionManager.ReplyToSender(
          buildOpenStreamTag(request))
      }
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
}
