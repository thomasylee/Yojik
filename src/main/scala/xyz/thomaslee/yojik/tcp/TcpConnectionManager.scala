package xyz.thomaslee.yojik.tcp

import akka.actor.{ Actor, ActorRef, Props }
import akka.io.Tcp.{ PeerClosed, Received, Write }
import akka.util.ByteString

import xyz.thomaslee.yojik.ConnectionManager
import xyz.thomaslee.yojik.messages.MessageActor

/**
 * TcpConnectionManager handles a single TCP connection provided by TcpServer.
 * It creates a ConnectionActor to handle messages received over the TCP
 * connection.
 */
class TcpConnectionManager extends Actor {
  lazy val messageActor = context.actorOf(Props(classOf[MessageActor]))

  var mostRecentSender: Option[ActorRef] = None

  def receive = {
    case ConnectionManager.Disconnect => context.stop(self)
    case Received(data) => {
      mostRecentSender = Some(sender)
      messageActor ! MessageActor.ProcessMessage(data.utf8String)
    }
    case ConnectionManager.ReplyToSender(message) => {
      println("Sent: " + message)
      if (mostRecentSender.isDefined) mostRecentSender.get ! Write(ByteString(message))
    }
    case PeerClosed => context.stop(self)
  }
}
