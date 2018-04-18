package xyz.thomaslee.yojik.messages

import akka.actor.{ Actor, ActorLogging, Props }
import java.io.InputStream
import javax.xml.stream.{ XMLInputFactory, XMLStreamConstants, XMLStreamException, XMLStreamReader }

object XmlParsingActor {
  case object Parse

  case class CloseStream(streamPrefix: Option[String])
  case class OpenStream(val prefix: Option[String], val namespaceUri: String, val attributes: Map[String, String])

  def props(inputStream: InputStream) =
    Props(classOf[XmlParsingActor], inputStream)
}

class XmlParsingActor(inputStream: InputStream) extends Actor with ActorLogging {
  lazy val xmlReader: XMLStreamReader  =
    XMLInputFactory.newInstance.createXMLStreamReader(inputStream)

  var streamPrefix: Option[String] = None

  var depth = 0

  override def postStop = {
    println("XmlParsingActor stopped")
    xmlReader.close
  }

  def receive: Receive = {
    case XmlParsingActor.Parse => {
      try {
        Some(xmlReader.next) collect {
          case XMLStreamConstants.START_ELEMENT => {
            depth += 1
            if (xmlReader.getName.getLocalPart == "stream") {
              // Prefixes can be null, "", or a non-empty String.
              streamPrefix =
                if (xmlReader.getPrefix == null || xmlReader.getPrefix == "") None
                else Some(xmlReader.getPrefix)

              // Need attributes to determine "to" field.
              context.parent ! XmlParsingActor.OpenStream(
                streamPrefix,
                xmlReader.getNamespaceURI,
                (0 until xmlReader.getAttributeCount).map(i =>
                  (xmlReader.getAttributeName(i).getLocalPart, xmlReader.getAttributeValue(i))
                ).toMap
              )
            }
            else {
              context.parent ! new ServiceUnavailableError(None, Some(
                s"<${ xmlReader.getName.getLocalPart }/> must instead be <stream/>"))
            }
          }
          case XMLStreamConstants.END_ELEMENT => {
            depth -= 1
            if (depth == 0) {
              println("Stream closed!")
              context.parent ! XmlParsingActor.CloseStream(streamPrefix)
            }
          }
        }

        if (xmlReader.hasNext) self ! XmlParsingActor.Parse
      } catch {
        case error: XMLStreamException => {
          context.parent ! new BadFormatError(None, Some(error.toString))
        }
      }
    }
  }
}
