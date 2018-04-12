import akka.actor.{ Actor, ActorRef, ActorSystem, Props }
import akka.io.Tcp.{ NoAck, PeerClosed, Received, Write }
import akka.testkit.{ ImplicitSender, TestActors, TestKit }
import akka.util.ByteString
import java.net.InetSocketAddress
import org.scalamock.scalatest.MockFactory
import org.scalatest.{ BeforeAndAfterAll, FlatSpec, Matchers, WordSpecLike }
import scala.concurrent.duration.FiniteDuration

import xyz.thomaslee.yojik.xmpp.tcp.TcpConnectionManager

class TcpConnectionManagerSpec extends TestKit(ActorSystem("TcpConnectionManagerSpec"))
  with MockFactory
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "EchoHandler actor" must {
    "stops when PeerClosed is received" in {
      val connManager = system.actorOf(TcpConnectionManager.props(mock[InetSocketAddress]))
      connManager ! PeerClosed
      expectNoMessage(FiniteDuration(10, "ms"))
    }
  }
}
