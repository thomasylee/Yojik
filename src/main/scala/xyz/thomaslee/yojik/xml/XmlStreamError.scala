package xyz.thomaslee.yojik.xml

/**
 * The parent class for all XML stream error subclasses.
 *
 * @todo Replace [[xyz.thomaslee.yojik.xml.XmlStreamError]] and its subclasses
 *   with [[xyz.thomaslee.yojik.xml.XmlTag]] instances.
 */
case class XmlStreamError(val prefix: Option[String], val errorType: String, val message: Option[String]) {
  /**
   * Returns an XML String representation of this stream error.
   *
   * @return a String representation of this error in XML format
   */
  override def toString: String = {
    val visiblePrefix = prefix match {
      case Some(pre) => pre + ":"
      case None => ""
    }
    s"""  <${ visiblePrefix }error>
       |    <$errorType xmlns='urn:ietf:params:xml:ns:xmpp-streams'/>
       |  </${ visiblePrefix }error>
       |</${ visiblePrefix }stream>""".stripMargin
  }
}

/**
 * Represents <bad-format/> XMPP errors.
 */
class BadFormatError(prefix: Option[String], message: Option[String])
  extends XmlStreamError(prefix, "bad-format", message)

/**
 * Represents <service-unavailable/> XMPP errors.
 */
class ServiceUnavailableError(prefix: Option[String], message: Option[String])
  extends XmlStreamError(prefix, "service-unavailable", message)

/**
 * InvalidXmlError represents <invalid-xml/> XMPP errors.
 */
class InvalidXmlError(prefix: Option[String], message: Option[String])
  extends XmlStreamError(prefix, "invalid-xml", message)

/**
 * InvalidNamespaceError represents <invalid-namespace/> XMPP errors.
 */
class InvalidNamespaceError(prefix: Option[String], message: Option[String])
  extends XmlStreamError(prefix, "invalid-namespace", message)

/** Represents errors when initiating StartTLS. */
class StartTlsError {
  override def toString: String = "<failure xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>"
}

/**
 * Represents a failure with inner tags or text content.
 *
 * @param condition the content to include in the <failure/> tag.
 */
class FailureWithDefinedCondition(condition: String) {
  override def toString: String =
    s"""<failure xmlns='urn:ietf:params:xml:ns:xmpp-sasl'>
       |  <${ condition }/>
       |</failure>""".stripMargin
}

/** Represents a <bad-request/> XMPP error. */
class BadRequestError(iqId: String) extends XmlStreamError(None, "bad-request", None) {
  override def toString: String =
    s"""<iq id='${ iqId }' type='error'>
       |<error type='modify'>
       |<bad-request xmlns='urn:ietf:params:xml:ns:xmpp-stanzas'/>
       |</error>
       |</iq>""".stripMargin
}
