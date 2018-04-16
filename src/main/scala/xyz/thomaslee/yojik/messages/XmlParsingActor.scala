package xyz.thomaslee.yojik.messages

import akka.actor.{ Actor, ActorLogging, Props }
import java.io.InputStream
import javax.xml.stream.{ XMLInputFactory, XMLStreamConstants, XMLStreamException, XMLStreamReader }

object XmlParsingActor {
  case object StartParsing

  case class OpenStream(val prefix: Option[String], val namespaceUri: String, val attributes: Map[String, String])

  def props(inputStream: InputStream) =
    Props(classOf[XmlParsingActor], inputStream)
}

class XmlParsingActor(inputStream: InputStream) extends Actor with ActorLogging {
  lazy val xmlReader: XMLStreamReader  =
    XMLInputFactory.newInstance.createXMLStreamReader(inputStream)

  def receive: Receive = {
    case XmlParsingActor.StartParsing => {
      def loop: Unit = {
        try {
          Some(xmlReader.next) collect {
            case XMLStreamConstants.START_ELEMENT =>
              if (xmlReader.getName.getLocalPart == "stream")
                // Need attributes to determine "to" field.
                sender ! XmlParsingActor.OpenStream(
                  // Prefixes can be null, "", or a non-empty String.
                  if (xmlReader.getPrefix == null || xmlReader.getPrefix == "") None else Some(xmlReader.getPrefix),
                  xmlReader.getNamespaceURI,
                  (0 until xmlReader.getAttributeCount).map(i =>
                    (xmlReader.getAttributeName(i).getLocalPart, xmlReader.getAttributeValue(i))
                  ).toMap
                )
              else
                sender ! new ServiceUnavailableError(None, Some(
                  s"<${ xmlReader.getName.getLocalPart }/> must instead be <stream/>"))
          }
          if (xmlReader.hasNext) loop
        } catch {
          case error: XMLStreamException => {
            sender ! new BadFormatError(None, Some(error.toString))
          }
        }
      }
      if (xmlReader.hasNext) loop
    }
  }
}
