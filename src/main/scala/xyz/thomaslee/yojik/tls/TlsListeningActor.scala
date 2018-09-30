package xyz.thomaslee.yojik.tls

import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import akka.util.ByteString
import java.nio.ByteBuffer
import java.nio.channels.ByteChannel

/**
 * Contains Akka messages that [[xyz.thomaslee.yojik.tls.TlsListeningActor]]
 * instances should be able to handle and acts as a factory for [[akka.actor.Props]]
 * used to create a [[xyz.thomaslee.yojik.tls.TlsListeningActor]].
 */
object TlsListeningActor {
  /** Indicates that the actor should try to read from the TLS channel. */
  case object Listen

  /**
   * Returns a [[akka.actor.Props]] instance used to create an instance of
   * [[xyz.thomaslee.yojik.tls.TlsListeningActor]].
   *
   * @param tlsChannel the TLS channel to watch for incoming bytes
   * @return the [[akka.actor.Props]] to create an instance of
   *   [[xyz.thomaslee.yojik.tls.TlsListeningActor]]
   */
  def props(tlsChannel: ByteChannel): Props =
    Props(classOf[TlsListeningActor], tlsChannel)
}

/**
 * Listens to the tlsChannel for incoming decrypted client-to-server bytes.
 *
 * @param tlsChannel the TLS channel to watch for incoming bytes
 */
class TlsListeningActor(tlsChannel: ByteChannel) extends Actor with ActorLogging {
  val bufferSize = 1000

  override def postStop: Unit = log.debug("TlsListeningActor stopped")

  /**
   * Receives Akka messages with different behaviors, starting with listenForMessages.
   */
  def receive: Receive = listenForMessages(context.parent)

  /**
   * Listens for incoming decrypted client-to-server bytes on the TLS channel.
   *
   * @param tlsActor an ActorRef to the [[xyz.thomaslee.yojik.tls.TlsActor]]
   *   to which decrypted bytes should be sent
   */
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
