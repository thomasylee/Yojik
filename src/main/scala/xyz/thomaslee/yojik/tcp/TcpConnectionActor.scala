package xyz.thomaslee.yojik.tcp

import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import akka.io.Tcp.{ Close, PeerClosed, Received, Write }
import akka.util.ByteString
import scala.util.Random

import xyz.thomaslee.yojik.ConnectionActor
import xyz.thomaslee.yojik.config.ConfigMap
import xyz.thomaslee.yojik.tls.TlsActor
import xyz.thomaslee.yojik.xmlstream.{ XmlStreamActor, XmlStreamManaging }

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
   *   [[xyz.thomaslee.yojik.tcp.TcpConnectionActor]]
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
  def receive: Receive = handleConnection(
    context.actorOf(
      Props(classOf[XmlStreamActor]),
      "xml-stream-actor-" + Random.alphanumeric.take(
        ConfigMap.randomCharsInActorNames).mkString),
    None)

  /**
   * Handles active TCP connections.
   *
   * @param xmlStreamManager the actor that is currently managing the XML stream
   * @param tlsActor the actor handling TLS details, if there is one
   */
  def handleConnection(xmlStreamManager: ActorRef, tlsActor: Option[ActorRef]): Receive = {
    case XmlStreamManaging.XmlStreamManagerChanged(newXmlStreamManager) => {
      tlsActor match {
        case Some(foundActor) =>
          foundActor ! XmlStreamManaging.XmlStreamManagerChanged(newXmlStreamManager)
        case None => {}
      }
      context.become(handleConnection(newXmlStreamManager, tlsActor))
    }
    case ConnectionActor.Disconnect => {
      log.debug("TCP connection disconnected")
      connection ! Close
      xmlStreamManager ! XmlStreamManaging.Stop
      context.stop(self)
    }
    case Received(data: ByteString) => {
      mostRecentSender = Some(sender)
      xmlStreamManager ! XmlStreamManaging.ProcessMessage(data)
    }
    case ConnectionActor.ReplyToSender(message) => {
      mostRecentSender match {
        case Some(sender) => sender ! Write(message)
        case None => {}
      }
    }
    case PeerClosed => {
      log.debug("TCP connection peer closed")
      xmlStreamManager ! XmlStreamManaging.Stop
      context.stop(self)
    }
    case ConnectionActor.CreateTlsActor(respondTo) => tlsActor match {
      case Some(foundActor) => sender ! ConnectionActor.TlsActorCreated(foundActor)
      case None => {
        val newTlsActor = context.actorOf(
          TlsActor.props(xmlStreamManager),
          "tls-actor-" + Random.alphanumeric.take(
            ConfigMap.randomCharsInActorNames).mkString)
        respondTo ! ConnectionActor.TlsActorCreated(newTlsActor)
        context.become(handleConnection(xmlStreamManager, Some(newTlsActor)))
      }
    }
  }
}
