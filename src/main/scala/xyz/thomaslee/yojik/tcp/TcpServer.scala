package xyz.thomaslee.yojik.tcp

import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import akka.io.{ IO, Tcp }
import akka.util.ByteString
import java.net.InetSocketAddress
import scala.util.Random
import Tcp.{ Bind, Bound, CommandFailed, Connected, Register }

class TcpServer extends Actor with ActorLogging {
  import context.system

  IO(Tcp) ! Bind(self, new InetSocketAddress("localhost", 5222))

  override def postStop = println("TcpServer stopped")

  def receive = {
    case bound @ Bound(localAddress) => context.parent ! bound
    case CommandFailed(_: Bind) => context.stop(self)
    case Connected(remote, _) =>
      val handler = context.actorOf(
        TcpConnectionActor.props(sender),
        "tcp-connection-actor-" + Random.alphanumeric.take(10).mkString)
      sender ! Register(handler)
  }
}
