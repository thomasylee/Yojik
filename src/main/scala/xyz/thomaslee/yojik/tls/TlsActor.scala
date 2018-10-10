package xyz.thomaslee.yojik.tls

import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import akka.util.ByteString
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.ByteChannel
import java.security.KeyStore
import javax.net.ssl.{ KeyManagerFactory, SSLContext, TrustManagerFactory }
import scala.util.{ Random, Try }
import tlschannel.ServerTlsChannel

import xyz.thomaslee.yojik.config.ConfigMap
import xyz.thomaslee.yojik.xmlstream.XmlStreamManaging

/**
 * Contains Akka messages that [[xyz.thomaslee.yojik.tls.TlsActor]] actors
 * should be able to handle, and also acts as a [[xyz.thomaslee.yojik.tls.TlsActor]]
 * factory.
 */
object TlsActor {
  /**
   * Indicates that the client has sent some encrypted TLS message that needs
   * to be decrypted and handled.
   */
  case class ProcessMessage(bytes: ByteString)

  /**
   * Indicates that the bytes should be encrypted so they can be sent to the client.
   */
  case class SendEncryptedToClient(bytes: ByteString)

  /**
   * Indicates that the bytes are encrypted and ready to be sent directly to
   * the client.
   */
  case class SendToClient(bytes: ByteString)

  /**
   * Indicates that the bytes are decrypted and ready to be sent directly to
   * the [[xyz.thomaslee.yojik.xmlstream.XmlStreamManaging]] actor.
   */
  case class SendToServer(bytes: ByteString)

  /** Indicates that this actor should be stopped. */
  case object Stop

  /**
   * Returns [[akka.actor.Props]] to use to create a
   * [[xyz.thomaslee.yojik.tls.TslActor]].
   *
   * @param xmlStreamManager an ActorRef to an actor with the
   *   [[xyz.thomaslee.yojik.xmlstream.XmlStreamManaging]] trait
   * @return a new [[akka.actor.Props]] instance to use to create a
   *   [[xyz.thomaslee.yojik.tls.TlsActor]]
   */
  def props(xmlStreamManager: ActorRef): Props =
    Props(classOf[TlsActor], xmlStreamManager)
}

/** Wraps the XML stream in a TLS session. */
class TlsActor(initXmlStreamManager: ActorRef) extends Actor with ActorLogging {
  override def postStop: Unit = log.debug("TlsActor stopped")

  /** Wraps the XML stream in a TLS session. */
  def receive: Receive = {
    val rawTlsChannel = new RawTlsChannel(self)
    val sslContext = createSslContext
    val tlsChannel = ServerTlsChannel
      .newBuilder(rawTlsChannel, sslContext)
      .build();

    val tlsListener = context.actorOf(
      TlsListeningActor.props(tlsChannel),
      "xml-listening-actor-" + Random.alphanumeric.take(
        ConfigMap.randomCharsInActorNames).mkString)

    handleTlsMessages(rawTlsChannel, tlsChannel, initXmlStreamManager, tlsListener)
  }

  /**
   * Handles Akka messages to wrap/unwrap XML with regards to the active TLS session.
   *
   * @param rawTlsChannel the channel that tracks encrypted/decrypted input/output
   * @param tlsChannel the channel that handles the intricacies of TLS handshaking,
   *   key exchange, encryption/decryption, etc.
   * @param xmlStreamManager an ActorRef to the
   *   [[xyz.thomaslee.yojik.xmlstream.XmlStreamManaging]] actor that handles the
   *   decrypted server side of the TLS session
   * @param tlsListener an ActorRef to the
   *   [[xyz.thomaslee.yojik.tls.TlsListeningActor]] that passes listens for
   *   decrypted client-to-server messages on the TLS channel
   */
  def handleTlsMessages(rawTlsChannel: RawTlsChannel, tlsChannel: ByteChannel, xmlStreamManager: ActorRef, tlsListener: ActorRef): Receive = {
    case TlsActor.ProcessMessage(bytes) => {
      log.debug("TLS bytes SentFromClient: " + bytes.length)
      rawTlsChannel.storeIncomingBytes(ByteBuffer.wrap(bytes.toArray[Byte]))
      tlsListener ! TlsListeningActor.Listen
    }
    case TlsActor.SendToServer(bytes) =>
      xmlStreamManager ! XmlStreamManaging.ProcessDecryptedMessage(bytes)
    case TlsActor.SendToClient(bytes) => {
      log.debug("TLS bytes SentToClient: " + bytes.length)
      xmlStreamManager ! XmlStreamManaging.PassToClient(bytes)
    }
    case TlsActor.SendEncryptedToClient(bytes) => {
      tlsChannel.write(ByteBuffer.wrap(bytes.toArray[Byte]))
    }
    case XmlStreamManaging.XmlStreamManagerChanged(newXmlStreamManager) =>
      context.become(handleTlsMessages(
        rawTlsChannel, tlsChannel, newXmlStreamManager, tlsListener))
    case TlsActor.Stop => {
      Try(tlsChannel.close)
      Try(rawTlsChannel.close)
      context.stop(self)
    }
  }

  /**
   * Returns the [[javax.net.ssl.SSLContext]] for the TLS session.
   *
   * @return the [[javax.net.ssl.SSLContext]] for the TLS session
   */
  def createSslContext: SSLContext = {
    // Of course, non-development environments should load the password from
    // system variables or other means instead of storing it in the source code.
    val keyStorePassword = Array[Char]('y', 'o', 'j', 'i', 'k', 'y', 'o', 'j', 'i', 'k')

    val keyStore  = KeyStore.getInstance("JKS");
    val fileInputStream = new FileInputStream(ConfigMap.keyStore)
    keyStore.load(fileInputStream, keyStorePassword)
    Try(fileInputStream.close)

    val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    tmf.init(keyStore);

    val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    kmf.init(keyStore, keyStorePassword)

    // Passwords are best stored in mutable data structures so they can be
    // overwritten when no longer needed.
    for (i <- 0 until keyStorePassword.length) keyStorePassword(i) = '\u0000'

    val sslContext = SSLContext.getInstance("TLSv1.2")
    sslContext.init(kmf.getKeyManagers, tmf.getTrustManagers(), null);
    sslContext
  }
}
