package xyz.thomaslee.yojik.messages

import akka.actor.ActorRef
import akka.actor.Actor.Receive
import org.xml.sax.SAXParseException
import scala.xml.XML

import xyz.thomaslee.yojik.ConnectionManager

class XmlStreamOpener {
  val StreamNamespaceRegex = """<([^:]+:)?stream """.r

  var streamNamespace: Option[String] = None

  def sendSuccessResponse(connectionActor: ActorRef) =
    connectionActor ! ConnectionManager.ReplyToSender(
      XmlResponse.XmlVersion + "\n" +
      "Other things")

  def sendBadFormatResponse(connectionActor: ActorRef) =
    connectionActor ! ConnectionManager.ReplyToSender(
      XmlResponse.XmlVersion + "\n" +
      XmlResponse.BadFormat + "\n" +
      XmlResponse.CloseStream)

  def extractStreamNamespace(xml: String): String =
    StreamNamespaceRegex.findFirstMatchIn(xml.trim) match {
      case Some(regexMatch) =>
        if (regexMatch.group(1) == null) ""
        else regexMatch.group(1)
      case None => ""
    }

  def openXmlStream: ActorRef => Receive = (connectionActor: ActorRef) => {
    case MessageActor.ProcessMessage(message) => {
      try {
        streamNamespace = Some(extractStreamNamespace(message))
      } catch {
        case _: SAXParseException => sendBadFormatResponse(connectionActor)
      }

      if (streamNamespace.isDefined) {
        var xmlStream: Option[String] = None

        try {
          xmlStream = Some(XML.loadString(message).toString)
        } catch {
          case _: SAXParseException =>
            try {
              xmlStream = Some(XML.loadString(message + "</" + streamNamespace + ":stream>").toString)
            } catch {
              case _: Throwable => sendBadFormatResponse(connectionActor)
            }
          case _: Throwable => sendBadFormatResponse(connectionActor)
        }

        if (xmlStream.isDefined)
          sendSuccessResponse(connectionActor)
      }
    }
  }
}
