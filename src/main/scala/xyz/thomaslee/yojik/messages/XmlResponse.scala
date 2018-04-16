package xyz.thomaslee.yojik.messages

import scala.xml.{ Attribute, Elem, Null, XML }

object XmlResponse {
  /**
   * Returns the opening <stream/> tag in response to a XML stream request with
   * the given attributes.
   *
   * @param prefix the <stream/> tag prefix, or None if no prefix is used
   * @param contentNamespace either "jabber:client" or "jabber:server"
   * @param streamId the unique identifier for the XML stream
   * @param recipient the stream requester as identified in the original "from" field
   * @return a String representation of the opening <stream/> tag
   */
  def openStream(prefix: Option[String], contentNamespace: Option[String], streamId: String, recipient: Option[String]) =
    s"""<${ if (prefix.isDefined) { prefix.get + ":" } else "" }stream
       |    ${ if (contentNamespace.isDefined) s"xmlns='${ contentNamespace.get }'" else "" }
       |    xmlns:stream='http://etherx.jabber.org/streams'
       |    id='$streamId'
       |    from='localhost'
       |    ${ if (recipient.isDefined) s"to='${ recipient.get }'" else "" }
       |    version='1.0'>""".stripMargin

  /**
   * Returns the closing <stream/> tag to close an XML stream using the given
   * tag prefix.
   *
   * @param prefix the <stream/> tag prefix, or None if no prefix is used
   * @return a String representation of the closing <stream/> tag
   */
  def closeStream(prefix: Option[String]) =
    s"""</${ if (prefix.isDefined) { prefix.get + ":" } else "" }stream>"""
}
