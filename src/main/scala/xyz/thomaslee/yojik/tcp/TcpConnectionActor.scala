package xyz.thomaslee.yojik.tcp

import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import akka.io.Tcp.{ Abort, Close, PeerClosed, Received, Write }
import akka.util.ByteString
import scala.util.Random

import xyz.thomaslee.yojik.ConnectionActor
import xyz.thomaslee.yojik.messages.MessageActor

object TcpConnectionActor {
  def props(connection: ActorRef) =
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

  override def postStop = println("TcpConnectionActor stopped")

  def receive = {
    case ConnectionActor.Disconnect => {
      println("Disconnect!")
      connection ! Close
      messageActor ! MessageActor.Stop
      context.stop(self)
    }
    case Received(data) => {
      mostRecentSender = Some(sender)
      messageActor ! MessageActor.ProcessMessage(data.utf8String)
    }
    case ConnectionActor.ReplyToSender(message) => {
      println("Sent: " + message)
      if (mostRecentSender.isDefined) mostRecentSender.get ! Write(ByteString(message))
    }
    case PeerClosed => {
      println("Peer closed!")
      messageActor ! MessageActor.Stop
      context.stop(self)
    }
  }
}
