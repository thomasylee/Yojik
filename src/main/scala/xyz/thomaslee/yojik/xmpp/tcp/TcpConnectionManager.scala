package xyz.thomaslee.yojik.xmpp.tcp

import akka.actor.{ Actor, ActorRef, Props }
import akka.io.Tcp.{ PeerClosed, Received, Write }
import java.net.InetSocketAddress

/**
 * Router determines which Actor to route the incoming message to
 * based on the message contents.
 */
object TcpConnectionManager {
  def props(remote: InetSocketAddress) =
    Props(classOf[TcpConnectionManager], remote)
}

class TcpConnectionManager(remote: InetSocketAddress) extends Actor {
  var xmlStreamNegotiating = false
  var xmlStreamNegotiated = false
  var outputStreamEstablished = false
  lazy val outputStream = {
    outputStreamEstablished = true
    context.actorOf(TcpOutputStream.props(remote, sender()))
  }

  // TODO: Establish XML session, then pass stanzas to handlers.
  def receive = {
    case Received(data) => sender() ! Write(data)
    case PeerClosed     => context stop self
  }
}
