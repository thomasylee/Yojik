package xyz.thomaslee.yojik.xml

import akka.actor.{ Actor, ActorLogging, Props }
import java.io.InputStream
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.io.BufferedSource
import scala.language.postfixOps
import scala.xml.pull.{ EvElemEnd, EvElemStart, EvText, XMLEventReader }

object XmlParsingActor {
  case object Parse

  case class AuthenticateWithSasl(mechanism: Option[String], namespaceUri: Option[String], value: Option[String])
  case class CloseStream(streamPrefix: Option[String])
  case class OpenStream(val prefix: Option[String], val namespaceUri: Option[String], val attributes: Map[String, String])
  case class StartTls(namespaceUri: Option[String])
  case class TagReceived(tag: XmlTag)

  def props(inputStream: InputStream): Props =
    Props(classOf[XmlParsingActor], inputStream)
}

class XmlParsingActor(inputStream: InputStream) extends Actor with ActorLogging {
  val xmlReader: XMLEventReader = new XMLEventReader(new BufferedSource(inputStream))

  var streamPrefix: Option[String] = None

  override def postStop: Unit = {
    log.debug("XmlParsingActor stopped")
    xmlReader.stop
    try { inputStream.close } catch { case _: Throwable => {} }
  }

  def receive: Receive = parseXml(None, 0)

  def parseXml(currentStanza: Option[XmlTag], depth: Int): Receive = {
    case XmlParsingActor.Parse =>
      if (xmlReader.available) {
        Some(xmlReader.next) collect {
          case EvText(text) => handleText(currentStanza, text)
          case event: EvElemStart => handleStartTag(currentStanza, depth, event)
          case event: EvElemEnd => handleEndTag(currentStanza, depth, event)
        }

        // If the stream has more events ready, go ahead and parse them.
        if (xmlReader.available) { self ! XmlParsingActor.Parse }
      }
      else {
        // Try again since parsing XML from the InputStream may take some time.
        context.system.scheduler.scheduleOnce(50 millis, self, XmlParsingActor.Parse)
      }
  }

  def handleStartTag(currentStanza: Option[XmlTag], depth: Int, event: EvElemStart): Unit = event match {
    case EvElemStart(prefix, name, attributes, namespace) => {
      val namespaceUri = Option(namespace) match {
        case None => None
        case Some(ns) => Option(ns.getURI(prefix))
      }

      val newTag = new XmlTag(name, Option(prefix), namespaceUri, attributes.asAttrMap)

      (currentStanza, depth) match {
        case (None, 0) => {
          streamPrefix = newTag.prefix
          notifyXmlStreamStart(newTag)
          context.become(parseXml(None, depth + 1))
        }
        case (None, 1) => context.become(parseXml(Some(newTag), depth + 1))
        case (Some(entity), _) => {
          entity.addContent(newTag)
          context.become(parseXml(currentStanza, depth + 1))
        }
      }
    }
  }

  def notifyXmlStreamStart(xmlStreamTag: XmlTag): Unit = {
    context.parent ! XmlParsingActor.OpenStream(
      xmlStreamTag.prefix,
      xmlStreamTag.namespaceUri,
      xmlStreamTag.attributes
    )
  }

  def handleEndTag(currentStanza: Option[XmlTag], depth: Int, event: EvElemEnd): Unit = (currentStanza, event) match {
    case (None, _) | (Some(_), EvElemEnd(_, "stream")) if depth <= 1 => {
      log.debug("XML stream closed")
      context.parent ! XmlParsingActor.CloseStream(streamPrefix)
      context.become(parseXml(None, 0))
    }
    // TODO: Handle all closing stanza tags equally, so starttls and auth
    // are no longer special cases.
    case (Some(stanza), EvElemEnd(_, label)) => (depth, label) match {
      case (2, "starttls") => {
        log.debug("<starttls/> received")
        context.parent ! XmlParsingActor.StartTls(stanza.namespaceUri)
        context.become(parseXml(currentStanza, depth - 1))
      }
      case (2, "auth") => {
        context.parent ! XmlParsingActor.AuthenticateWithSasl(
          stanza.attributes.get("mechanism"),
          stanza.namespaceUri,
          Some(stanza.getStrings.mkString))
        context.become(parseXml(currentStanza, depth - 1))
      }
      case (2, _) => {
        context.parent ! XmlParsingActor.TagReceived(stanza)
        context.become(parseXml(None, depth - 1))
      }
      case _ => context.become(parseXml(currentStanza, depth - 1))
    }
  }

  def handleText(currentStanza: Option[XmlTag], text: String): Unit = currentStanza match {
    case Some(tag: XmlTag) => tag.addContent(new XmlString(text))
    case None => {}
  }
}
