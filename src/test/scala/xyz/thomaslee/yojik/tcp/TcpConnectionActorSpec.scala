import akka.actor.{ Actor, ActorSystem, Props }
import akka.io.Tcp.PeerClosed
import akka.testkit.{ ImplicitSender, TestKit }
import org.scalamock.scalatest.MockFactory
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpecLike }
import scala.concurrent.duration.FiniteDuration

import xyz.thomaslee.yojik.tcp.TcpConnectionActor

class MockActor extends Actor {
  def receive: Receive = { case _ => {} }
}

class TcpConnectionActorSpec extends TestKit(ActorSystem("TcpConnectionActorSpec"))
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
      val connection = system.actorOf(Props(classOf[MockActor]))
      val connActor = system.actorOf(TcpConnectionActor.props(connection))
      val waitTimeMilliseconds = 10

      connActor ! PeerClosed

      expectNoMessage(FiniteDuration(waitTimeMilliseconds, "ms"))
    }
  }
}
