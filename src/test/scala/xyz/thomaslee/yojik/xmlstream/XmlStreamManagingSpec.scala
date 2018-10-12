import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.pattern.ask
import akka.testkit.{ ImplicitSender, TestKit, TestProbe }
import akka.util.{ ByteString, Timeout }
import org.scalamock.scalatest.MockFactory
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpecLike }
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

import xyz.thomaslee.yojik.ConnectionActor
import xyz.thomaslee.yojik.tls.TlsActor
import xyz.thomaslee.yojik.xml.{ XmlParsingActor, XmlStreamError }
import xyz.thomaslee.yojik.xmlstream.{ XmlStreamActor, XmlStreamManaging }

class XmlStreamManagingSpec extends TestKit(ActorSystem("XmlStreamManagingSpec"))
    with MockFactory
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  /** Requests a reference to the instance of the actor. */
  case object GetInstance

  /** Extends XmlStreamManaging for testing the trait. */
  class XmlStreamManager extends XmlStreamManaging {
    val conn = TestProbe()

    override def connectionActor: ActorRef = conn.ref

    /** Echoes received messages to its parent. */
    override def receive: Receive = {
      case GetInstance => sender ! this
      case message => context.parent ! message
    }
  }

  def getXsmInstance: XmlStreamManager = {
    val xsmActorRef = system.actorOf(Props(new XmlStreamManager))

    implicit val timeout = Timeout(200 millis)
    Await.result(xsmActorRef ? GetInstance, timeout.duration)
      .asInstanceOf[XmlStreamManager]
  }

  "XmlStreamManaging" must {
    "stop self and close the connection when stop() is called" in {
      val xsmActor = getXsmInstance
      val xsmActorRef = xsmActor.self

      // Listen for the actors to terminate.
      val deathWatcher = TestProbe()
      deathWatcher watch xsmActorRef

      xsmActor.stop

      xsmActor.conn.expectMsg(200 millis, ConnectionActor.Disconnect)
      deathWatcher.expectTerminated(xsmActorRef)
    }

    "return a closed XML stream when includeOpenStream = false in handleStreamError()" in {
      val xsmActor = getXsmInstance

      val error = new XmlStreamError(Some("stream"), "test-error", None)

      xsmActor.handleStreamError(error, false)

      xsmActor.conn.expectMsgPF(200 millis) {
        case ConnectionActor.ReplyToSender(bytes) if bytes == ByteString(error.toString) => {}
      }
    }

    "return an opened/closed XML stream when includeOpenStream = true in handleStreamError()" in {
      val xsmActor = getXsmInstance

      val error = new XmlStreamError(Some("stream"), "test-error", None)

      val expectedBytes = ByteString(xsmActor.buildOpenStreamTag(XmlParsingActor.OpenStream(
        error.prefix, Some(XmlStreamActor.ValidStreamNamespace), Map())) + "\n" +
        error.toString)

      xsmActor.handleStreamError(error, true)

      xsmActor.conn.expectMsgPF(200 millis) {
        case ConnectionActor.ReplyToSender(bytes) if bytes == expectedBytes => {}
      }
    }

    "return a closed XML stream to tlsActor in handleStreamErrorWithTls()" in {
      val xsmActor = getXsmInstance
      val xsmActorRef = xsmActor.self
      val tlsActor = TestProbe("TlsActor")

      val error = new XmlStreamError(Some("stream"), "test-error", None)

      val deathWatcher = TestProbe()
      deathWatcher watch xsmActorRef

      xsmActor.handleStreamErrorWithTls(error, tlsActor.ref)

      tlsActor.expectMsgPF(200 millis) {
        case TlsActor.SendEncryptedToClient(bytes) if bytes == ByteString(error.toString) => {}
      }
      deathWatcher.expectTerminated(xsmActorRef)
    }

    "return an XmlStreamError when validateOpenStreamRequest() is given invalid request" in {
      val xsmActor = getXsmInstance

      val request = XmlParsingActor.OpenStream(None, None, Map())
      assert(xsmActor.validateOpenStreamRequest(request).isDefined)
    }

    "return None when validateOpenStreamRequest() is given valid request" in {
      val xsmActor = getXsmInstance

      val request = XmlParsingActor.OpenStream(None, Some(XmlStreamActor.ValidStreamNamespace), Map())
      assert(xsmActor.validateOpenStreamRequest(request).isEmpty)
    }
  }
}
