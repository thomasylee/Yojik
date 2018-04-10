package im.yojik.xmpp.server

import akka.actor.{ Actor, ActorRef, Props }
import akka.io.{ IO, Tcp }
import akka.util.ByteString
import java.net.InetSocketAddress
import Tcp._

class Server extends Actor {
  import context.system

  IO(Tcp) ! Bind(self, new InetSocketAddress("localhost", 5222))

  def receive = {
    case b @ Bound(localAddress) =>
      context.parent ! b

    case CommandFailed(_: Bind) => context stop self

    case c @ Connected(remote, local) =>
      val handler = context.actorOf(Props[EchoHandler])
      val connection = sender()
      connection ! Register(handler)
  }
}

class EchoHandler extends Actor {
  def receive = {
    case Received(data) => sender() ! Write(data)
    case PeerClosed     => context stop self
  }
}
