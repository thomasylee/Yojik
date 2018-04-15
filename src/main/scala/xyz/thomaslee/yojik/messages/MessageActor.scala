package xyz.thomaslee.yojik.messages

import akka.actor.{ Actor, ActorLogging, Props }
import org.xml.sax.SAXParseException
import scala.util.Random
import scala.xml.XML

import xyz.thomaslee.yojik.ConnectionManager

object MessageActor {
  case class ProcessMessage(message: String)

  val StreamNamespaceRegex = """<([^:]+:)?stream """.r
}

class MessageActor extends Actor with ActorLogging {
  var streamNamespace = ""

  lazy val streamId = "c2s-" + Random.alphanumeric.take(10).mkString

  def receive = openXmlStream

  def sendBadFormatResponse(request: String) =
    sender ! ConnectionManager.ReplyToSender(
      XmlResponse.XmlVersion + "\n" +
      XmlResponse.OpenStream(request, streamId) + "\n" +
      XmlResponse.BadFormat + "\n" +
      XmlResponse.CloseStream)

  def sendStreamOpenedResponse(request: String) =
    sender ! ConnectionManager.ReplyToSender(
      XmlResponse.XmlVersion + "\n" +
      XmlResponse.OpenStream(request, streamId))

  def extractStreamNamespace(xml: String) =
    MessageActor.StreamNamespaceRegex.findFirstMatchIn(xml.trim) match {
      case Some(regexMatch) =>
        if (regexMatch.group(1) == null) ""
        else regexMatch.group(1)
      case None => ""
    }

  val openXmlStream: Receive = {
    case MessageActor.ProcessMessage(message) => {
      streamNamespace = extractStreamNamespace(message)

      var xmlStream: Option[String] = None

      try {
        xmlStream = Some(XML.loadString(message + "</" + streamNamespace + "stream>").toString)
      } catch {
        case _: Throwable => sendBadFormatResponse(message)
      }

      if (xmlStream.isDefined)
        sendStreamOpenedResponse(xmlStream.get)
    }
  }
}
