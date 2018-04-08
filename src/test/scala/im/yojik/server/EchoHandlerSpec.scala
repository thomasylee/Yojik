import akka.actor.{ Actor, ActorRef, ActorSystem, Props }
import akka.io.Tcp.{ NoAck, PeerClosed, Received, Write }
import akka.testkit.{ ImplicitSender, TestActors, TestKit }
import akka.util.ByteString
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpecLike }
import scala.concurrent.duration.FiniteDuration

import im.yojik.server.EchoHandler

class MySpec() extends TestKit(ActorSystem("MySpec")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "EchoHandler actor" must {

    "send back messages unchanged" in {
      val echo = system.actorOf(Props[EchoHandler])
      echo ! Received(ByteString("Test!"))
      expectMsg(FiniteDuration(10, "ms"), Write(ByteString("Test!"), NoAck(null)))
    }

    "stops when PeerClosed is received" in {
      val echo = system.actorOf(Props[EchoHandler])
      echo ! PeerClosed
      expectNoMessage(FiniteDuration(10, "ms"))
    }

  }
}
