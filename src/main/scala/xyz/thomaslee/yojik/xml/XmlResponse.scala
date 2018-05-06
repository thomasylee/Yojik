package xyz.thomaslee.yojik.xml

object XmlResponse {
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
       |    ${ contentNamespace match { case Some(ns) => s"xmlns='$ns'" case None => "" }}
       |    xmlns:stream='http://etherx.jabber.org/streams'
       |    id='$streamId'
       |    from='localhost'
       |    ${ recipient match { case Some(r) => s"to='$r'" case None => "" }}
       |    version='1.0'>""".stripMargin

  /**
   * Returns the closing <stream/> tag to close an XML stream using the given
   * tag prefix.
   *
   * @param prefix the <stream/> tag prefix, or None if no prefix is used
   * @return a String representation of the closing <stream/> tag
   */
  def closeStream(prefix: Option[String]): String =
    s"""</${ insertPrefix(prefix) }stream>"""

  def startTlsStreamFeature(prefix: Option[String]): String =
    s"""<${ insertPrefix(prefix) }features>
       |  <starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'>
       |    <required/>
       |  </starttls>
       |</${ insertPrefix(prefix) }features>""".stripMargin

  val proceedWithTls = "<proceed xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>"

  def saslStreamFeature(prefix: Option[String], includeExternal: Boolean = false): String =
    s"""<${ insertPrefix(prefix) }features>
       |  <mechanisms xmlns='urn:ietf:params:xml:ns:xmpp-sasl'>
       |    ${ if (includeExternal) "<mechanism>EXTERNAL</mechanism>" else "" }
       |    <!-- Not yet implemented: <mechanism>SCRAM-SHA-1-PLUS</mechanism> -->
       |    <!-- Not yet implemented: <mechanism>SCRAM-SHA-1</mechanism> -->
       |    <mechanism>PLAIN</mechanism>
       |  </mechanisms>
       |</${ insertPrefix(prefix) }features>""".stripMargin

  val saslSuccess = "<success xmlns='urn:ietf:params:xml:ns:xmpp-sasl'/>"

  def resourceBindFeature(prefix: Option[String]): String =
    s"""<${ insertPrefix(prefix) }features>
       |  <bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/>
       |</${ insertPrefix(prefix) }features>""".stripMargin
}
