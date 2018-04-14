package xyz.thomaslee.yojik.xmpp.tcp

import akka.actor.{ Actor, ActorRef, Props }
import akka.io.Tcp.{ PeerClosed, Received, Write }
import java.net.InetSocketAddress

import xyz.thomaslee.yojik.xmpp.handlers.{ Handler, XmlStreamHandler }

object TcpConnectionManager {
  def props(remote: InetSocketAddress) =
    Props(classOf[TcpConnectionManager], remote)
}

/**
 * TcpConnectionManager handles a single incoming TCP connection provided by
 * TcpServer. It creates a SessionHandler to establish an XML stream session,
 * and then forwards XML stanzas to the actor bound to the specific JID.
 */
class TcpConnectionManager(remote: InetSocketAddress) extends Actor {
  /**
   * The XmlStreamHandler that establishes an XML stream
   */
  lazy val xmlStreamHandler = context.actorOf(XmlStreamHandler.props(sender()))

  /**
   * True if the XML stream has already been negotiated and started, false otherwise.
   */
  var xmlStreamOpen = false

  /**
   * The JID of the connected client.
   */
  var jabberId: Option[String] = None

  def receive = {
    case Received(data) =>
      if (xmlStreamOpen) println("XML stream open!")
        // Context.ActorOf("/user/yojik/jid/" + jabberId) ! RouteToJid(data, sender())
      else
        xmlStreamHandler ! Handler.Handle(data.utf8String)
    case PeerClosed => context.stop(self)
  }
}
