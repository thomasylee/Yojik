package xyz.thomaslee.yojik.xml

import akka.actor.{ Actor, ActorLogging }

/**
 * Contains Akka messages to be handled by instances of
 * [[xyz.thomaslee.yojik.xml.StanzaHandlingActor]].
 */
object StanzaHandlingActor {
  /** Indicates that the XML tag and its contents should be handled. */
  case class HandleStanza(tag: XmlTag)
}

/** Handles XML stanzas supported by XMPP according to RFC-6120. */
class StanzaHandlingActor extends Actor with ActorLogging {
  /**
   * Receives Akka messages with different behaviors, starting with handleStanza.
   */
  def receive: Receive = handleStanza

  def handleStanza: Receive = {
    case StanzaHandlingActor.HandleStanza(tag @ XmlTag("iq", _, _, attributes)) => {
      log.debug("iq received: " + tag)
    }
  }
}
