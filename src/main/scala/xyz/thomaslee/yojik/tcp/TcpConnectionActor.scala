package xyz.thomaslee.yojik.tcp

import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import akka.io.Tcp.{ Close, PeerClosed, Received, Write }
import akka.util.ByteString
import scala.util.Random

import xyz.thomaslee.yojik.ConnectionActor
import xyz.thomaslee.yojik.config.ConfigMap
import xyz.thomaslee.yojik.xmlstream.XmlStreamActor

/**
 * Factory for [[akka.actor.Props]] used to create a
 * [[xyz.thomaslee.yojik.tcp.TcpConnectionActor]].
 */
object TcpConnectionActor {
  /**
   * Returns [[akka.actor.Props]] to use to create a
   * [[xyz.thomaslee.yojik.tcp.TcpConnectionActor]].
   *
   * @param connection an ActorRef to an actor that responds to
   *   [[xyz.thomaslee.yojik.ConnectionActor]] messages
   * @return a new [[akka.actor.Props]] instance to use to create a
   *   [[xyz.thomaslee.yojik.Tcp.ConnectionActor]]
   */
  def props(connection: ActorRef): Props =
    Props(classOf[TcpConnectionActor], connection)
}

/**
 * Handles a single TCP connection provided by TcpServer and creates a
 * ConnectionActor to handle messages received over the TCP connection.
 *
 * @param connection an ActorRef to an actor that responds to
 *   [[xyz.thomaslee.yojik.ConnectionActor]] messages
 */
class TcpConnectionActor(connection: ActorRef) extends Actor with ActorLogging {
  var mostRecentSender: Option[ActorRef] = None

  override def postStop: Unit = log.debug("TcpConnectionActor stopped")

  /**
   * Receives Akka messages with different behaviors, starting with the
   * unauthenticatedConnection behavior.
   */
  def receive: Receive = unauthenticatedConnection(
    context.actorOf(
      Props(classOf[XmlStreamActor]),
      "xml-stream-actor-" + Random.alphanumeric.take(
        ConfigMap.randomCharsInActorNames).mkString))

  /**
   * Handles TCP connections for XML streams that have not yet been authenticated
   * and thus cannot be mapped to an actor by JID.
   *
   * @param xmlStreamActor the ActorRef to handle the XML stream
   */
  def unauthenticatedConnection(xmlStreamActor: ActorRef): Receive = {
    case ConnectionActor.Disconnect => {
      log.debug("TCP connection disconnected")
      connection ! Close
      xmlStreamActor ! XmlStreamActor.Stop
      context.stop(self)
    }
    case Received(data: ByteString) => {
      mostRecentSender = Some(sender)
      xmlStreamActor ! XmlStreamActor.ProcessMessage(data)
    }
    case ConnectionActor.ReplyToSender(message) => {
      mostRecentSender match {
        case Some(sender) => sender ! Write(message)
        case None => {}
      }
    }
    case PeerClosed => {
      log.debug("TCP connection peer closed")
      xmlStreamActor ! XmlStreamActor.Stop
      context.stop(self)
    }
  }
}
