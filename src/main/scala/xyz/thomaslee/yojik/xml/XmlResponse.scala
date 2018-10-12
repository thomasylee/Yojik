package xyz.thomaslee.yojik.xml

/**
 * Provides methods for generating XML responses.
 *
 * @todo Replace this object with [[xyz.thomaslee.yojik.xml.XmlTag]] instances.
 */
object XmlResponse {
  /**
   * Returns the given XML tag prefix in a format that can be inserted directly
   * in front of the tag name.
   *
   * @param prefix the <stream/> tag prefix, or None if no prefix is used
   * @return a String that contains the prefix followed by a colon if there is
   *   a prefix, or else an empty String
   */
  def insertPrefix(prefix: Option[String]): String = prefix match {
    case Some(pre) => pre + ":"
    case None => ""
  }

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
  def openStream(prefix: Option[String], contentNamespace: Option[String], streamId: String, recipient: Option[String]): String =
    s"""<${ insertPrefix(prefix) }stream
       |  ${ contentNamespace match { case Some(ns) => s"xmlns='$ns'" case None => "" }}
       |  xmlns:stream='http://etherx.jabber.org/streams'
       |  id='$streamId'
       |  from='localhost'
       |  ${ recipient match { case Some(r) => s"to='$r'" case None => "" }}
       |  version='1.0'>""".stripMargin

  /**
   * Returns the closing <stream/> tag to close an XML stream using the given
   * tag prefix.
   *
   * @param prefix the <stream/> tag prefix, or None if no prefix is used
   * @return a String representation of the closing <stream/> tag
   */
  def closeStream(prefix: Option[String]): String =
    s"""</${ insertPrefix(prefix) }stream>"""

  /**
   * Returns the XML tag advertising the StartTLS feature.
   *
   * @param prefix the <stream/> tag prefix, or None if no prefix is used
   * @return a String representation of the StartTLS feature
   */
  def startTlsStreamFeature(prefix: Option[String]): String =
    s"""<${ insertPrefix(prefix) }features>
       |  <starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'>
       |    <required/>
       |  </starttls>
       |</${ insertPrefix(prefix) }features>""".stripMargin

  /**
   * The XML tag indicating that the client should proceed with TLS negotiations.
   */
  val proceedWithTls = "<proceed xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>"

  /**
   * Returns the XML tag advertising the SASL authentication feature.
   *
   * @param prefix the <stream/> tag prefix, or None if no prefix is used
   * @param includeExternal true if the EXTERNAL mechanism should be advertised
   * @return a String representation of the SASL authentication feature
   */
  def saslStreamFeature(prefix: Option[String], includeExternal: Boolean = false): String =
    s"""<${ insertPrefix(prefix) }features>
       |  <mechanisms xmlns='urn:ietf:params:xml:ns:xmpp-sasl'>
       |    ${ if (includeExternal) "<mechanism>EXTERNAL</mechanism>" else "" }
       |    <!-- Not yet implemented: <mechanism>SCRAM-SHA-1-PLUS</mechanism> -->
       |    <!-- Not yet implemented: <mechanism>SCRAM-SHA-1</mechanism> -->
       |    <mechanism>PLAIN</mechanism>
       |  </mechanisms>
       |</${ insertPrefix(prefix) }features>""".stripMargin

  /** The XML tag indicating that SASL authentication succeeded. */
  val saslSuccess = "<success xmlns='urn:ietf:params:xml:ns:xmpp-sasl'/>"

  /**
   * Returns the XML tag advertising the resource binding feature.
   *
   * @param prefix the <stream/> tag prefix, or None if no prefix is used
   * @return a String representation of the resource binding feature
   */
  def resourceBindFeature(prefix: Option[String]): String =
    s"""<${ insertPrefix(prefix) }features>
       |  <bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/>
       |</${ insertPrefix(prefix) }features>""".stripMargin
}
