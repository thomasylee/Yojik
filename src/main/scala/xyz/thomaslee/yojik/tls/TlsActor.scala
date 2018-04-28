package xyz.thomaslee.yojik.tls

import akka.actor.{ Actor, ActorLogging, Props }
import akka.util.ByteString
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.net.ssl.{ KeyManagerFactory, SSLContext, TrustManagerFactory }
import scala.util.Try
import tlschannel.ServerTlsChannel

import xyz.thomaslee.yojik.messages.MessageActor

object TlsActor {
  case class ProcessMessage(bytes: ByteString)
  case class SendEncryptedToClient(bytes: ByteString)
  case class SendToClient(bytes: ByteString)
  case class SendToServer(bytes: ByteString)
  case object Stop
}

class TlsActor extends Actor with ActorLogging {
  lazy val rawTlsChannel = new RawTlsChannel(self)
  lazy val sslContext = createSslContext
  lazy val tlsChannel = ServerTlsChannel
    .newBuilder(rawTlsChannel, sslContext)
    .build();

  lazy val tlsListener = context.actorOf(TlsListeningActor.props(tlsChannel))

  override def postStop: Unit = log.debug("TlsActor stopped")

  def receive: Receive = {
    case TlsActor.ProcessMessage(bytes) => {
      log.debug("TLS bytes SentFromClient: " + bytes.length)
      rawTlsChannel.storeIncomingBytes(ByteBuffer.wrap(bytes.toArray[Byte]))
      tlsListener ! TlsListeningActor.Listen
    }
    case TlsActor.SendToServer(bytes) =>
      context.parent ! MessageActor.ProcessDecryptedMessage(bytes)
    case TlsActor.SendToClient(bytes) =>
      log.debug("TLS bytes SentToClient: " + bytes.length)
      context.parent ! MessageActor.PassToClient(bytes)
    case TlsActor.SendEncryptedToClient(bytes) =>
      tlsChannel.write(ByteBuffer.wrap(bytes.toArray[Byte]))
    case TlsActor.Stop => {
      Try(tlsChannel.close)
      Try(rawTlsChannel.close)
      context.stop(self)
    }
  }

  def createSslContext: SSLContext = {
    // Of course, non-development environments should load the password from
    // system variables or other means instead of storing it in the source code.
    val keyStorePassword = Array[Char]('y', 'o', 'j', 'i', 'k', 'y', 'o', 'j', 'i', 'k')

    val keyStore  = KeyStore.getInstance("JKS");
    val fileInputStream = new FileInputStream("keystore.jks")
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
