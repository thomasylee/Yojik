package xyz.thomaslee.yojik.xmpp.tcp

import akka.actor.{ Actor, ActorRef, Props }
import akka.io.{ IO, Tcp }
import akka.util.ByteString
import java.net.InetSocketAddress
import Tcp.{ Close, CommandFailed, Connect, Connected, ConnectionClosed, Received, Register, Write }

import xyz.thomaslee.yojik.xmpp.OutputStream

object TcpOutputStream {
  def props(remote: InetSocketAddress, replies: ActorRef) =
    Props(classOf[OutputStream], remote, replies)
}

class TcpOutputStream(remote: InetSocketAddress, listener: ActorRef) extends Actor with OutputStream {
  import context.system

  IO(Tcp) ! Connect(remote)

  override def write(content: String) = sender() ! Write(ByteString(content))

  def receive = {
    case CommandFailed(_: Connect) ⇒
      listener ! "connect failed"
      context stop self

    // TODO: Only close should ever be received by this stream.
    case conn @ Connected(remote, local) ⇒
      listener ! conn
      val connection = sender()
      connection ! Register(self)
      context become {
        case data: ByteString ⇒
          connection ! Write(data)
        case CommandFailed(w: Write) ⇒
          // O/S buffer was full
          listener ! "write failed"
        case Received(data) ⇒
          listener ! data
        case "close" ⇒
          connection ! Close
        case _: ConnectionClosed ⇒
          listener ! "connection closed"
          context stop self
      }
  }
}
