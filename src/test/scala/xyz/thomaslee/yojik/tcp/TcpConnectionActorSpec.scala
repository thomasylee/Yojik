import akka.actor.{ ActorSystem, Props }
import akka.io.Tcp
import akka.testkit.{ ImplicitSender, TestKit, TestProbe }
import akka.util.ByteString
import org.scalamock.scalatest.MockFactory
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpecLike }
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

import xyz.thomaslee.yojik.ConnectionActor
import xyz.thomaslee.yojik.tcp.TcpConnectionActor
import xyz.thomaslee.yojik.xmlstream.XmlStreamActor

class TcpConnectionActorSpec extends TestKit(ActorSystem("TcpConnectionActorSpec"))
  with MockFactory
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "TcpConnectionActor" must {
    "disconnect when Disconnect is received" in {
      val connection = TestProbe("Tcp")
      val xmlStreamActor = TestProbe("XmlStreamActor")
      val connActor = system.actorOf(Props(new TcpConnectionActor(connection.ref) {
        override def receive: Receive = unauthenticatedConnection(xmlStreamActor.ref)
      }))

      val deathWatcher = TestProbe()
      deathWatcher.watch(connActor)

      connActor ! ConnectionActor.Disconnect

      connection.expectMsg(200 millis, Tcp.Close)
      xmlStreamActor.expectMsg(200 millis, XmlStreamActor.Stop)
      deathWatcher.expectTerminated(connActor)
    }

    "stop when PeerClosed is received" in {
      val connection = TestProbe("Tcp")
      val xmlStreamActor = TestProbe("XmlStreamActor")
      val connActor = system.actorOf(Props(new TcpConnectionActor(connection.ref) {
        override def receive: Receive = unauthenticatedConnection(xmlStreamActor.ref)
      }))

      val deathWatcher = TestProbe()
      deathWatcher.watch(connActor)

      connActor ! Tcp.PeerClosed

      xmlStreamActor.expectMsg(200 millis, XmlStreamActor.Stop)
      deathWatcher.expectTerminated(connActor)
    }

    "forward data from Received to XmlStreamActor" in {
      val connection = TestProbe("Tcp")
      val xmlStreamActor = TestProbe("XmlStreamActor")
      val connActor = system.actorOf(Props(new TcpConnectionActor(connection.ref) {
        override def receive: Receive = unauthenticatedConnection(xmlStreamActor.ref)
      }))

      val data = ByteString("UTF-8 message: ёжик")
      connActor ! Tcp.Received(data)

      xmlStreamActor.expectMsg(200 millis, XmlStreamActor.ProcessMessage(data))
    }

    "send ReplyToSender messages to the TCP sender" in {
      val connection = TestProbe("Tcp")
      val xmlStreamActor = TestProbe("XmlStreamActor")
      val tcpSender = TestProbe("TcpSender")
      val connActor = system.actorOf(Props(new TcpConnectionActor(connection.ref) {
        mostRecentSender = Some(tcpSender.ref)
        override def receive: Receive = unauthenticatedConnection(xmlStreamActor.ref)
      }))

      val data = ByteString("UTF-8 message: ёжик")
      connActor ! ConnectionActor.ReplyToSender(data)

      tcpSender.expectMsg(200 millis, Tcp.Write(data))
    }
  }
}
