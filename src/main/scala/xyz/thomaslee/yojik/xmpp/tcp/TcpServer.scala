package xyz.thomaslee.yojik.xmpp.tcp

import akka.actor.{ Actor, ActorRef, Props }
import akka.io.{ IO, Tcp }
import akka.util.ByteString
import java.net.InetSocketAddress
import Tcp.{ Bind, Bound, CommandFailed, Connected, Register }

class TcpServer extends Actor {
  import context.system

  IO(Tcp) ! Bind(self, new InetSocketAddress("localhost", 5222))

  def receive = {
    case bound @ Bound(localAddress) => context.parent ! bound
    case CommandFailed(_: Bind) => context.stop(self)
    case Connected(remote, _) =>
      val handler = context.actorOf(TcpConnectionManager.props(remote))
      sender() ! Register(handler)
  }
}
