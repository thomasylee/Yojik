import akka.actor.{ ActorSystem, Props }
import akka.testkit.{ ImplicitSender, TestKit, TestProbe }
import akka.util.ByteString
import java.nio.ByteBuffer
import java.nio.channels.ByteChannel
import org.scalamock.scalatest.MockFactory
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpecLike }
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

import xyz.thomaslee.yojik.tls.{ TlsActor, TlsListeningActor, RawTlsChannel }
import xyz.thomaslee.yojik.xmlstream.XmlStreamActor

class TlsActorSpec extends TestKit(ActorSystem("TlsActorSpec"))
    with MockFactory
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "TlsActor" must {
    "store messages and notifies TlsListeningActor when ProcessMessage received" in {
      val rawTlsChannel = new RawTlsChannel(TestProbe("TlsActor").ref)
      val tlsChannel = stub[ByteChannel]
      val xmlStreamActor = TestProbe("XmlStreamActor")
      val tlsListener = TestProbe("TlsListenerActor")
      val tlsActor = system.actorOf(Props(new TlsActor {
        override def receive: Receive = handleTlsMessages(
          rawTlsChannel, tlsChannel, xmlStreamActor.ref, tlsListener.ref)
      }))

      val data = ByteString("UTF-8 message: ёжик")
      tlsActor ! TlsActor.ProcessMessage(data)

      tlsListener.expectMsg(200 millis, TlsListeningActor.Listen)

      val retrieved = ByteBuffer.allocate(data.size)
      rawTlsChannel.read(retrieved)
      assert(retrieved.array === data.toArray[Byte])
    }

    "send ProcessDecryptedMessage to XmlStreamActor when SendToServer received" in {
      val rawTlsChannel = stub[RawTlsChannel]
      val tlsChannel = stub[ByteChannel]
      val xmlStreamActor = TestProbe("XmlStreamActor")
      val tlsListener = TestProbe("TlsListenerActor")
      val tlsActor = system.actorOf(Props(new TlsActor {
        override def receive: Receive = handleTlsMessages(
          rawTlsChannel, tlsChannel, xmlStreamActor.ref, tlsListener.ref)
      }))

      val data = ByteString("UTF-8 message: ёжик")
      tlsActor ! TlsActor.SendToServer(data)

      xmlStreamActor.expectMsg(200 millis, XmlStreamActor.ProcessDecryptedMessage(data))
    }

    "send PassToClient to XmlStreamActor when SendToClient received" in {
      val rawTlsChannel = stub[RawTlsChannel]
      val tlsChannel = stub[ByteChannel]
      val xmlStreamActor = TestProbe("XmlStreamActor")
      val tlsListener = TestProbe("TlsListenerActor")
      val tlsActor = system.actorOf(Props(new TlsActor {
        override def receive: Receive = handleTlsMessages(
          rawTlsChannel, tlsChannel, xmlStreamActor.ref, tlsListener.ref)
      }))

      val data = ByteString("UTF-8 message: ёжик")
      tlsActor ! TlsActor.SendToClient(data)

      xmlStreamActor.expectMsg(200 millis, XmlStreamActor.PassToClient(data))
    }

    "write to TlsChannel when SendEncryptedToClient received" in {
      val rawTlsChannel = stub[RawTlsChannel]
      val tlsChannel = stub[ByteChannel]
      val xmlStreamActor = TestProbe("XmlStreamActor")
      val tlsListener = TestProbe("TlsListenerActor")
      val tlsActor = system.actorOf(Props(new TlsActor {
        override def receive: Receive = handleTlsMessages(
          rawTlsChannel, tlsChannel, xmlStreamActor.ref, tlsListener.ref)
      }))

      val data = ByteString("UTF-8 message: ёжик")
      tlsActor ! TlsActor.SendEncryptedToClient(data)

      (tlsChannel.write _).when(*).returns(data.size)

      (tlsChannel.write _).verify(where { (bytes: ByteBuffer) => 
        bytes.array() === data.toArray[Byte]
      })
    }

    "closes channels and stops self when Stop received" in {
      val rawTlsChannel = stub[RawTlsChannel]
      val tlsChannel = stub[ByteChannel]
      val xmlStreamActor = TestProbe("XmlStreamActor")
      val tlsListener = TestProbe("TlsListenerActor")
      val tlsActor = system.actorOf(Props(new TlsActor {
        override def receive: Receive = handleTlsMessages(
          rawTlsChannel, tlsChannel, xmlStreamActor.ref, tlsListener.ref)
      }))

      val deathWatcher = TestProbe()
      deathWatcher.watch(tlsActor)

      tlsActor ! TlsActor.Stop

      (rawTlsChannel.close _).when().returns(_)
      (tlsChannel.close _).when().returns(_)

      (rawTlsChannel.close _).verify
      (tlsChannel.close _).verify

      deathWatcher.expectTerminated(tlsActor)
    }
  }
}
