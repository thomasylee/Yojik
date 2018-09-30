package xyz.thomaslee.yojik

import akka.actor.{ Actor, ActorLogging, DeadLetter }
import akka.util.ByteString

import xyz.thomaslee.yojik.xmlstream.XmlStreamActor

/**
 * Contains constants to be used by instances of
 * [[xyz.thomaslee.yojik.DeadLetterActor]].
 */
object DeadLetterActor {
  /** The prefix for [[xyz.thomaslee.yojik.xmlstream.XmlStreamActor]] actors. */
  val XmlStreamActorPrefix = "xml-stream-actor-"

  /** The prefix for [[xyz.thomaslee.yojik.tcp.TcpConnectionActor]] actors. */
  val TcpConnectionPrefix = "tcp-connection-actor-"

  /** The name of the TCP server. */
  val TcpServerName = "tcp-server"

  /** The prefix for [[xyz.thomaslee.yojik.xml.XmlParsingActor]] actors. */
  val XmlParsingActorPrefix = "xml-parsing-actor-"
}

/**
 * Handles dead letters from actors that receive messages after they have
 * been stopped.
 */
class DeadLetterActor extends Actor with ActorLogging {
  /**
   * Handles dead letters by ignoring the ones that are expected from various
   * connection termination scenarios and logging the rest.
   */
  def receive: Receive = {
    case DeadLetter(msg: ByteString, from, to) => {
      val fromName = from.path.name
      val toName = to.path.name
      if (!fromName.startsWith(DeadLetterActor.XmlParsingActorPrefix) &&
          !toName.startsWith(DeadLetterActor.XmlParsingActorPrefix) &&
          msg != ConnectionActor.Disconnect &&
          msg != XmlStreamActor.Stop) {
        log.warning("Message failed to send from " + fromName + " to " +
          toName + ": " + msg.utf8String)
      }
    }
  }
}
