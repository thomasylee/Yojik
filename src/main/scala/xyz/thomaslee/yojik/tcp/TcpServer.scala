package xyz.thomaslee.yojik.tcp

import akka.actor.{ Actor, ActorLogging }
import akka.io.{ IO, Tcp }
import java.net.InetSocketAddress
import scala.util.Random
import Tcp.{ Bind, Bound, CommandFailed, Connected, Register }

import xyz.thomaslee.yojik.config.ConfigMap

/**
 * Creates new [[xyz.thomaslee.yojik.tcp.TcpConnectionActor]] actors when
 * a new TCP connection is started.
 */
class TcpServer extends Actor with ActorLogging {
  implicit val system = context.system

  IO(Tcp) ! Bind(self, new InetSocketAddress("localhost", ConfigMap.serverPort))

  override def postStop: Unit = log.debug("TcpServer stopped")

  /**
   * Handles messages from TCP connections and
   * [[xyz.thomaslee.yojik.tcp.TcpConnectionActor]] instances.
   */
  def receive: Receive = {
    case bound @ Bound(_) => context.parent ! bound
    case CommandFailed(_: Bind) => context.stop(self)
    case Connected(_, _) =>
      val handler = context.actorOf(
        TcpConnectionActor.props(sender),
        "tcp-connection-actor-" + Random.alphanumeric.take(
          ConfigMap.randomCharsInActorNames).mkString)
      sender ! Register(handler)
  }
}
