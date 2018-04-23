package xyz.thomaslee.yojik.tls

import akka.actor.{ Actor, ActorLogging, Props }
import akka.util.ByteString
import java.nio.ByteBuffer
import tlschannel.ServerTlsChannel

object TlsListeningActor {
  case object Listen

  def props(tlsChannel: ServerTlsChannel) =
    Props(classOf[TlsListeningActor], tlsChannel)
}

class TlsListeningActor(tlsChannel: ServerTlsChannel) extends Actor with ActorLogging {
  override def postStop = println("TlsListeningActor stopped")

  def receive: Receive = {
    case TlsListeningActor.Listen => {
      val response = ByteBuffer.allocate(1000);
      try {
        if (tlsChannel.read(response) != -1) {
          context.parent ! TlsActor.SendToServer(ByteString(response.array()))
          self ! TlsListeningActor.Listen
        }
      } catch { case _: Throwable => {} }
    }
  }
}
