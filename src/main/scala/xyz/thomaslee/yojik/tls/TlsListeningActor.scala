package xyz.thomaslee.yojik.tls

import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import akka.util.ByteString
import java.nio.ByteBuffer
import java.nio.channels.ByteChannel

object TlsListeningActor {
  case object Listen

  def props(tlsChannel: ByteChannel): Props =
    Props(classOf[TlsListeningActor], tlsChannel)
}

class TlsListeningActor(tlsChannel: ByteChannel) extends Actor with ActorLogging {
  val bufferSize = 1000

  override def postStop: Unit = log.debug("TlsListeningActor stopped")

  def receive: Receive = listenForMessages(context.parent)

  def listenForMessages(tlsActor: ActorRef): Receive = {
    case TlsListeningActor.Listen => {
      val response = ByteBuffer.allocate(bufferSize);
      try {
        if (tlsChannel.read(response) != -1) {
          tlsActor ! TlsActor.SendToServer(ByteString(response.array()))
          self ! TlsListeningActor.Listen
        }
      } catch { case _: Throwable => {} }
    }
  }
}
