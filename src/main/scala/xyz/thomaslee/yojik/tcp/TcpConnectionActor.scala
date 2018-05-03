package xyz.thomaslee.yojik.tcp

import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import akka.io.Tcp.{ Abort, Close, PeerClosed, Received, Write }
import akka.util.ByteString
import scala.util.Random

import xyz.thomaslee.yojik.ConnectionActor
import xyz.thomaslee.yojik.config.ConfigMap
import xyz.thomaslee.yojik.xmlstream.XmlStreamActor

object TcpConnectionActor {
  def props(connection: ActorRef): Props =
    Props(classOf[TcpConnectionActor], connection)
}

/**
 * TcpConnectionActor handles a single TCP connection provided by TcpServer.
 * It creates a ConnectionActor to handle messages received over the TCP
 * connection.
 */
class TcpConnectionActor(connection: ActorRef) extends Actor with ActorLogging {
  var mostRecentSender: Option[ActorRef] = None

  override def postStop: Unit = log.debug("TcpConnectionActor stopped")

  def receive: Receive = unauthenticatedConnection(
    context.actorOf(
      Props(classOf[XmlStreamActor]),
      "xml-stream-actor-" + Random.alphanumeric.take(
        ConfigMap.randomCharsInActorNames).mkString))

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
