package xyz.thomaslee.yojik.xmpp.tcp

import akka.actor.{ Actor, ActorRef, Props }
import akka.io.Tcp.{ PeerClosed, Received, Write }
import java.net.InetSocketAddress

import xyz.thomaslee.yojik.xmpp.handlers.{ Handler, XmlStreamHandler }

object TcpConnectionManager {
  case class SetJabberId(jabberId: String)
  case object XmlStreamOpened

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
      if (!xmlStreamOpen)
        xmlStreamHandler ! Handler.Handle(data.utf8String)
      else
        println("Create handler for authenticated stanzas!")
    case TcpConnectionManager.SetJabberId(jid) => jabberId = Some(jid)
    case TcpConnectionManager.XmlStreamOpened => xmlStreamOpen = true
    case PeerClosed => context.stop(self)
  }
}
