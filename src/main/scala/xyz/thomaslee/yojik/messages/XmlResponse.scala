package xyz.thomaslee.yojik.messages

import scala.xml.{ Attribute, Elem, Null, XML }

object XmlResponse {
  val XmlVersion = "<?xml version='1.0'?>"

  def OpenStream(xmlStreamRequest: String, id: String): String = {
    val requestXml = XML.loadString(xmlStreamRequest)

    val fromAttr: String = requestXml.attribute("from") match {
      case Some(fromVal) => fromVal.toString
      case None => null.asInstanceOf[String]
    }

    val responseXml = requestXml.asInstanceOf[Elem] %
      Attribute(null, "to", fromAttr,
      Attribute(null, "from", "localhost",
      Attribute(null, "id", id, Null)))

    responseXml.attributes.remove("to");

    val responseStr = responseXml.toString

    if (responseStr.endsWith("/>"))
      responseStr.substring(0, responseStr.length - 2) + ">"
    else
      if (requestXml.prefix == null)
        responseXml.toString.replaceAll("</stream>", "")
      else
        responseXml.toString.replaceAll("</" + requestXml.prefix + ":stream>", "")
  }.trim

  val CloseStream = "</stream:stream>"

  val BadFormat = """
    |  <stream:error>
    |    <bad-format xmlns='urn:ietf:params:xml:ns:xmpp-streams'/>
    |  </stream:error>""".stripMargin.split("\n").filter(!_.isEmpty).mkString
}
