package xyz.thomaslee.yojik.xmlstream

import akka.actor.ActorRef
import scala.util.Random

import xyz.thomaslee.yojik.config.ConfigMap
import xyz.thomaslee.yojik.xml.XmlParsingActor

/**
 * Contains constants to be used by instances of
 * [[xyz.thomaslee.yojik.xmlstream.XmlStreamActor]].
 */
object XmlStreamActor {
  /** The valid XML stream namespace, as defined by RFC-6120. */
  val ValidStreamNamespace = "http://etherx.jabber.org/streams"
}

/**
 * Establishes and handles unauthenticated XML streams as per RFC-6120 until
 * the stream has been authenticated and bound to a JID.
 */
class XmlStreamActor extends XmlStreamManaging {
  /**
   * Returns an [[akka.actor.ActorRef]] to the actor responsible for the connection.
   *
   * @return an [[akka.actor.ActorRef]] to the actor responsible for the connection
   */
  override def connectionActor: ActorRef = context.parent

  val xmlParsingActor = context.system.actorOf(
    XmlParsingActor.props(self, xmlInputStream),
      "xml-parsing-actor-" + Random.alphanumeric.take(ConfigMap.randomCharsInActorNames).mkString)

  /**
   * Receives Akka messages with different behaviors, starting with the
   * OpenStreamBehavior to establish an XML stream.
   */
  def receive: Receive = OpenStreamBehavior(
    log,
    this,
    xmlParsingActor)
}
