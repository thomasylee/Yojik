package xyz.thomaslee.yojik.xml

import akka.actor.{ Actor, ActorLogging, Props }
import java.io.InputStream
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.io.BufferedSource
import scala.language.postfixOps
import scala.xml.pull.{ EvElemEnd, EvElemStart, EvText, XMLEventReader }

/**
 * Contains Akka messages pertaining to instances of
 * [[xyz.thomaslee.yojik.xml.XmlParsingActor]], and also acts as a factory for
 * creating [[akka.actor.Props]] to use when creating new instances of
 * [[xyz.thomaslee.yojik.xml.XmlParsingActor]].
 */
object XmlParsingActor {
  /** Indicates that the actor should start parsing XML from its InputStream. */
  case object Parse

  /** Indicates that the receiver should start SASL authentication. */
  case class AuthenticateWithSasl(mechanism: Option[String], namespaceUri: Option[String], value: Option[String])

  /** Indicates that the receiver should close the XML stream and disconnect. */
  case class CloseStream(streamPrefix: Option[String])

  /** Indicates that the receiver should validate and open the XML stream. */
  case class OpenStream(val prefix: Option[String], val namespaceUri: Option[String], val attributes: Map[String, String])

  /** Indicates that the receiver should handle a StartTLS request. */
  case class StartTls(namespaceUri: Option[String])

  /** Indicates that an XML tag was received and should be handled appropriately. */
  case class TagReceived(tag: XmlTag)

  /**
   * Returns [[akka.actor.Props]] to use to create a
   * [[xyz.thomaslee.yojik.xml.XmlParsingActor]].
   *
   * @param inputStream the [[java.io.InputStream]] from which XML is received
   * @return a new [[akka.actor.Props]] instance to use to create a
   *   [[xyz.thomaslee.yojik.xml.XmlParsingActor]]
   */
  def props(inputStream: InputStream): Props =
    Props(classOf[XmlParsingActor], inputStream)
}

/**
 * Parses XML received from a [[java.io.InputStream]] and sends its actor parent
 * messages in response to the received XML tags.
 *
 * @param inputStream the [[java.io.InputStream]] to listen to for incoming XML
 */
class XmlParsingActor(inputStream: InputStream) extends Actor with ActorLogging {
  /** The event-based XML parser that reads from the [[java.io.InputStream]]. */
  val xmlReader: XMLEventReader = new XMLEventReader(new BufferedSource(inputStream))

  var streamPrefix: Option[String] = None

  /**
   * Closes the [[scala.xml.pull.XmlEventReader]] after stopping to help save
   * system resources.
   */
  override def postStop: Unit = {
    log.debug("XmlParsingActor stopped")
    xmlReader.stop
    try { inputStream.close } catch { case _: Throwable => {} }
  }

  /**
   * Receives Akka messages with different behaviors, starting with parseXml.
   */
  def receive: Receive = parseXml(None, 0)

  /**
   * Parses XML from the [[scala.xml.pull.XmlEventReader]] when a
   * XmlParsingActor.Parse message is received.
   *
   * @param currentStanza the current XML tag, if there is one
   * @param depth the depth of nesting in the XML document
   */
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

  /**
   * Handles an opening XML tag.
   *
   * @param currentStanza the current XML tag, if there is one
   * @param depth the depth of nesting in the XML document
   * @param event the [[scala.xml.pull.EvElemStart]] event from the XML stream
   */
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

  /**
   * Sends a message to this actor's parent indicating that an XML stream has
   * been opened.
   *
   * @param xmlStreamTag the tag details to send to this actor's parent
   */
  def notifyXmlStreamStart(xmlStreamTag: XmlTag): Unit = {
    context.parent ! XmlParsingActor.OpenStream(
      xmlStreamTag.prefix,
      xmlStreamTag.namespaceUri,
      xmlStreamTag.attributes
    )
  }

  /**
   * Handles a closing XML tag.
   *
   * @param currentStanza the current XML tag, if there is one
   * @param depth the depth of nesting in the XML document
   * @param event the [[scala.xml.pull.EvElemEnd]] event from the XML stream
   */
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

  /**
   * Handles text within an XML tag.
   *
   * @param currentStanza the XML tag, if there is one
   * @param text the text content
   */
  def handleText(currentStanza: Option[XmlTag], text: String): Unit = currentStanza match {
    case Some(tag: XmlTag) => tag.addContent(new XmlString(text))
    case None => {}
  }
}
