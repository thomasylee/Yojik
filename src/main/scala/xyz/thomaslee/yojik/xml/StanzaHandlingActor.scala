package xyz.thomaslee.yojik.xml

import akka.actor.{ Actor, ActorLogging }

object StanzaHandlingActor {
  case class HandleStanza(tag: XmlTag)
}

class StanzaHandlingActor extends Actor with ActorLogging {
  def receive: Receive = handleStanza

  def handleStanza: Receive = {
    case StanzaHandlingActor.HandleStanza(tag @ XmlTag("iq", _, _, attributes)) => {
      log.debug("iq received: " + tag)
    }
  }
}
