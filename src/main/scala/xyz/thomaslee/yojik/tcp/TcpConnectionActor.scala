package xyz.thomaslee.yojik.tcp

import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import akka.io.Tcp.{ Abort, Close, PeerClosed, Received, Write }
import akka.util.ByteString
import scala.util.Random

import xyz.thomaslee.yojik.ConnectionActor
import xyz.thomaslee.yojik.messages.MessageActor

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
  lazy val messageActor = context.actorOf(
    Props(classOf[MessageActor]),
    "message-actor-" + Random.alphanumeric.take(10).mkString)

  var mostRecentSender: Option[ActorRef] = None

  override def postStop: Unit = log.debug("TcpConnectionActor stopped")

  def receive: Receive = {
    case ConnectionActor.Disconnect => {
      log.debug("TCP connection disconnected")
      connection ! Close
      messageActor ! MessageActor.Stop
      context.stop(self)
    }
    case Received(data: ByteString) => {
      mostRecentSender = Some(sender)
      messageActor ! MessageActor.ProcessMessage(data)
    }
    case ConnectionActor.ReplyToSender(message) => {
      log.debug("Sent: " + message.utf8String)
      if (mostRecentSender.isDefined) mostRecentSender.get ! Write(message)
    }
    case PeerClosed => {
      log.debug("TCP connection peer closed")
      messageActor ! MessageActor.Stop
      context.stop(self)
    }
  }
}
