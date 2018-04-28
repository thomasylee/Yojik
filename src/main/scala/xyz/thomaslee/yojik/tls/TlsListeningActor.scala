package xyz.thomaslee.yojik.tls

import akka.actor.{ Actor, ActorLogging, Props }
import akka.util.ByteString
import java.nio.ByteBuffer
import tlschannel.ServerTlsChannel

object TlsListeningActor {
  case object Listen

  def props(tlsChannel: ServerTlsChannel): Props =
    Props(classOf[TlsListeningActor], tlsChannel)
}

class TlsListeningActor(tlsChannel: ServerTlsChannel) extends Actor with ActorLogging {
  val bufferSize = 1000

  override def postStop: Unit = log.debug("TlsListeningActor stopped")

  def receive: Receive = {
    case TlsListeningActor.Listen => {
      val response = ByteBuffer.allocate(bufferSize);
      try {
        if (tlsChannel.read(response) != -1) {
          context.parent ! TlsActor.SendToServer(ByteString(response.array()))
          self ! TlsListeningActor.Listen
        }
      } catch { case _: Throwable => {} }
    }
  }
}
