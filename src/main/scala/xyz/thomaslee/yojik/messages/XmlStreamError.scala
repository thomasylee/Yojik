package xyz.thomaslee.yojik.messages

/**
 * XmlStreamError is the parent class of all XMPP stream errors.
 */
case class XmlStreamError(prefix: Option[String], errorType: String, message: Option[String]) {
  override def toString = {
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
 * BadFormatError represents <bad-format/> XMPP errors.
 */
class BadFormatError(prefix: Option[String], message: Option[String])
  extends XmlStreamError(prefix, "bad-format", message)

/**
 * ServiceUnavailableError represents <service-unavailable/> XMPP errors.
 */
class ServiceUnavailableError(prefix: Option[String], message: Option[String])
  extends XmlStreamError(prefix, "service-unavailable", message)

/**
 * InvalidNamespaceError represents <invalid-namespace/> XMPP errors.
 */
class InvalidNamespaceError(prefix: Option[String], message: Option[String])
  extends XmlStreamError(prefix, "invalid-namespace", message)