package xyz.thomaslee.yojik.xml

import akka.actor.{ Actor, ActorLogging, Props }
import java.io.InputStream
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.io.BufferedSource
import scala.language.postfixOps
import scala.xml.MetaData
import scala.xml.pull.{ EvElemEnd, EvElemStart, EvText, XMLEventReader }

object XmlParsingActor {
  case object Parse

  case class AuthenticateWithSasl(mechanism: Option[String], namespaceUri: Option[String], value: Option[String])
  case class CloseStream(streamPrefix: Option[String])
  case class OpenStream(val prefix: Option[String], val namespaceUri: Option[String], val attributes: Map[String, String])
  case class StartTls(namespaceUri: Option[String])

  def props(inputStream: InputStream): Props =
    Props(classOf[XmlParsingActor], inputStream)
}

class XmlParsingActor(inputStream: InputStream) extends Actor with ActorLogging {
  val xmlReader: XMLEventReader = new XMLEventReader(new BufferedSource(inputStream))

  var streamPrefix: Option[String] = None

  var lastText: Option[String] = None

  var tlsNamespaceUri: Option[String] = None

  var authNamespaceUri: Option[String] = None

  var authAttributes: Option[MetaData] = None

  override def postStop: Unit = {
    log.debug("XmlParsingActor stopped")
    xmlReader.stop
    try { inputStream.close } catch { case _: Throwable => {} }
  }

  def receive: Receive = parseXml(0)

  def parseXml(depth: Int): Receive = {
    case XmlParsingActor.Parse =>
      if (xmlReader.available) {
        Some(xmlReader.next) collect {
          case EvText(text) => lastText = Some(text)
          case event: EvElemStart => handleStartElement(depth, event)
          case event: EvElemEnd => handleEndElement(depth, event)
        }

        // If the stream has more events ready, go ahead and parse them.
        if (xmlReader.available) { self ! XmlParsingActor.Parse }
      }
      else {
        // Try again since parsing XML from the InputStream may take some time.
        context.system.scheduler.scheduleOnce(50 millis, self, XmlParsingActor.Parse)
      }
  }

  def handleStartElement(depth: Int, event: EvElemStart): Unit = event match {
    case EvElemStart(prefix, label, attributes, namespace) => {
      context.become(parseXml(depth + 1))

      val namespaceUri = Option(namespace) match {
        case None => None
        case Some(ns) => Option(ns.getURI(prefix))
      }

      Option(depth, label) collect {
        case (0, "stream") => {
          streamPrefix = Option(prefix)

          // Need attributes to determine "to" and "from" fields.
          context.parent ! XmlParsingActor.OpenStream(
            streamPrefix,
            namespaceUri,
            attributes.asAttrMap
          )
        }
        case (1, "starttls") => tlsNamespaceUri = namespaceUri
        case (1, "auth") => {
          authNamespaceUri = namespaceUri
          authAttributes = Option(attributes)
        }
        case (0, _) =>
          context.parent ! new ServiceUnavailableError(None, Some(
            s"<$label/> must instead be <stream/>"))
      }
    }
  }

  def handleEndElement(depth: Int, event: EvElemEnd): Unit = event match {
    case EvElemEnd(_, label) => {
      context.become(parseXml(depth - 1))

      Option(depth, label) collect {
        case (1, _) | (2, "stream") => {
          log.debug("XML stream closed")
          context.parent ! XmlParsingActor.CloseStream(streamPrefix)
        }
        case (2, "starttls") => {
          log.debug("<starttls/> received")
          context.parent ! XmlParsingActor.StartTls(tlsNamespaceUri)
        }
        case (2, "auth") => {
          context.parent ! XmlParsingActor.AuthenticateWithSasl(
            authAttributes match {
              case None => None
              case Some(attributes) => Option(attributes("mechanism")) match {
                case None => None
                case Some(mech) => Some(mech.toString)
              }
            },
            authNamespaceUri,
            lastText)
        }
      }
    }
  }
}
