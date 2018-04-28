package xyz.thomaslee.yojik.tcp

import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import akka.io.{ IO, Tcp }
import akka.util.ByteString
import java.net.InetSocketAddress
import scala.util.Random
import Tcp.{ Bind, Bound, CommandFailed, Connected, Register }

class TcpServer extends Actor with ActorLogging {
  implicit val system = context.system

  val port = 5222

  IO(Tcp) ! Bind(self, new InetSocketAddress("localhost", port))

  override def postStop: Unit = log.debug("TcpServer stopped")

  def receive: Receive = {
    case bound @ Bound(localAddress) => context.parent ! bound
    case CommandFailed(_: Bind) => context.stop(self)
    case Connected(remote, _) =>
      val handler = context.actorOf(
        TcpConnectionActor.props(sender),
        "tcp-connection-actor-" + Random.alphanumeric.take(10).mkString)
      sender ! Register(handler)
  }
}
