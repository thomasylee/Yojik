package xyz.thomaslee.yojik.messages

import akka.actor.{ Actor, ActorLogging, Props }
import java.io.InputStream
import javax.xml.stream.{ XMLInputFactory, XMLStreamConstants, XMLStreamException, XMLStreamReader }

object XmlParsingActor {
  case object Parse

  case class AuthenticateWithSasl(mechanism: Option[String], namespaceUri: Option[String], value: Option[String])
  case class CloseStream(streamPrefix: Option[String])
  case class OpenStream(val prefix: Option[String], val namespaceUri: String, val attributes: Map[String, String])
  case class StartTls(namespaceUri: String)

  def props(inputStream: InputStream) =
    Props(classOf[XmlParsingActor], inputStream)
}

class XmlParsingActor(inputStream: InputStream) extends Actor with ActorLogging {
  val xmlReader: XMLStreamReader =
    XMLInputFactory.newInstance.createXMLStreamReader(inputStream)

  var streamPrefix: Option[String] = None

  var depth = 0

  var lastText: Option[String] = None

  var authAttributes: Option[Map[String, Any]] = None

  override def postStop: Unit = {
    log.debug("XmlParsingActor stopped")
    xmlReader.close
  }

  def receive: Receive = {
    case XmlParsingActor.Parse => {
      try {
        Some(xmlReader.next) collect {
          case XMLStreamConstants.START_ELEMENT => {
            depth += 1
            if (depth == 1 && xmlReader.getName.getLocalPart == "stream") {
              // Prefixes can be null, "", or a non-empty String.
              streamPrefix =
                if (xmlReader.getPrefix == null || xmlReader.getPrefix == "") {
                  None
                }
                else {
                  Some(xmlReader.getPrefix)
                }

              // Need attributes to determine "to" field.
              context.parent ! XmlParsingActor.OpenStream(
                streamPrefix,
                xmlReader.getNamespaceURI,
                (0 until xmlReader.getAttributeCount).map(i =>
                  (xmlReader.getAttributeName(i).getLocalPart, xmlReader.getAttributeValue(i))
                ).toMap
              )
            }
            else if (depth == 2 && xmlReader.getName.getLocalPart == "auth") {
              authAttributes = Some((0 until xmlReader.getAttributeCount).map(i =>
                (xmlReader.getAttributeName(i).getLocalPart, xmlReader.getAttributeValue(i))
              ).toMap)
            }
            else if (depth == 0) {
              context.parent ! new ServiceUnavailableError(None, Some(
                s"<${ xmlReader.getName.getLocalPart }/> must instead be <stream/>"))
            }
          }
          case XMLStreamConstants.END_ELEMENT => {
            depth -= 1
            if (depth == 0) {
              log.debug("XML stream closed")
              context.parent ! XmlParsingActor.CloseStream(streamPrefix)
            }
            else if (depth == 1 && xmlReader.getName.getLocalPart == "starttls") {
              log.debug("<starttls/> received")
              context.parent ! XmlParsingActor.StartTls(xmlReader.getNamespaceURI)
            }
            else if (depth == 1 && xmlReader.getName.getLocalPart == "auth") {
              val namespace = xmlReader.getNamespaceURI
              val value = lastText
              context.parent ! XmlParsingActor.AuthenticateWithSasl(
                authAttributes match {
                  case None => None
                  case Some(attributes) =>
                    if (attributes("mechanism") == null) {
                      None
                    }
                    else {
                      Some(attributes("mechanism").toString)
                    }
                },
                if (namespace == null) None else Some(namespace),
                lastText)
            }
          }
          case XMLStreamConstants.CHARACTERS => lastText = Some(xmlReader.getText())
          case XMLStreamConstants.CDATA => lastText = Some(xmlReader.getText())
        }

        if (xmlReader.hasNext) self ! XmlParsingActor.Parse
      } catch {
        case error: XMLStreamException if !error.toString.contains("Pipe closed") =>
          context.parent ! new BadFormatError(None, Some(error.toString))
      }
    }
  }
}
