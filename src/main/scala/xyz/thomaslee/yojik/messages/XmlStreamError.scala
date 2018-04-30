package xyz.thomaslee.yojik.messages

/**
 * XmlStreamError is the parent class of all XMPP stream errors.
 */
case class XmlStreamError(val prefix: Option[String], val errorType: String, val message: Option[String]) {
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

class StartTlsError {
  override def toString: String = "<failure xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>"
}

class FailureWithDefinedCondition(condition: String) {
  override def toString: String =
    s"""<failure xmlns='urn:ietf:params:xml:ns:xmpp-sasl'>
       |  <${ condition }/>
       |</failure>""".stripMargin
}
