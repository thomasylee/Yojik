import akka.actor.ActorSystem
import akka.testkit.{ ImplicitSender, TestActors, TestKit, TestProbe }
import akka.util.ByteString
import java.nio.ByteBuffer
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpecLike }
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

import xyz.thomaslee.yojik.tls.{ RawTlsChannel, TlsActor }

class RawTlsChannelSpec extends TestKit(ActorSystem("RawTlsChannelSpec"))
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "RawTlsChannel" must {
    "be closed after close()" in {
      val tlsActor = system.actorOf(TestActors.blackholeProps)
      val channel = new RawTlsChannel(tlsActor)
      assert(channel.isOpen)
      channel.close
      assert(!channel.isOpen)
    }

    "send write() bytes to the TlsActor" in {
      val tlsActor = TestProbe("TlsActor")
      val channel = new RawTlsChannel(tlsActor.ref)
      val message = "UTF-8: ёжик"

      channel.write(ByteBuffer.wrap(message.getBytes))

      tlsActor.expectMsg(200 millis, TlsActor.SendToClient(ByteString(message)))
    }

    "data inputted by storeIncomingBytes() can be read by read()" in {
      val tlsActor = system.actorOf(TestActors.blackholeProps)
      val channel = new RawTlsChannel(tlsActor)
      val message = ByteBuffer.wrap("ABC".getBytes)

      channel.storeIncomingBytes(message)

      val firstPart = ByteBuffer.allocateDirect(2)
      val secondPart = ByteBuffer.allocateDirect(2)

      assert(channel.read(firstPart) == 2)
      firstPart.rewind
      assert(firstPart.get == 'A')
      assert(firstPart.get == 'B')

      assert(channel.read(secondPart) == 1)
      secondPart.rewind
      assert(secondPart.get == 'C')
    }
  }
}
