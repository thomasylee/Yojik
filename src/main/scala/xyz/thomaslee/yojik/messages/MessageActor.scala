package xyz.thomaslee.yojik.messages

import akka.actor.{ Actor, ActorLogging, ActorRef, PoisonPill, Props }
import akka.util.ByteString
import java.io.{ PipedInputStream, PipedOutputStream }
import java.util.Base64
import org.xml.sax.SAXParseException
import scala.annotation.tailrec
import scala.util.Random
import scala.xml.XML

import xyz.thomaslee.yojik.ConnectionActor
import xyz.thomaslee.yojik.tls.TlsActor

// TODO: Break this into separate files by behavior.
object MessageActor {
  case class PassToClient(message: ByteString)
  case class ProcessMessage(message: ByteString)
  case class ProcessDecryptedMessage(message: ByteString)
  case object Stop

  val ValidStreamNamespace = "http://etherx.jabber.org/streams"
  val ValidStartTlsNamespace = "urn:ietf:params:xml:ns:xmpp-tls"
}

class MessageActor extends Actor with ActorLogging {
  var xmlOutputStream = new PipedOutputStream
  var xmlInputStream = new PipedInputStream(xmlOutputStream)

  def stop = {
    context.stop(self)
    try { xmlOutputStream.close } catch { case _: Throwable => {} }
    try { xmlInputStream.close } catch { case _: Throwable => {} }
    context.parent ! ConnectionActor.Disconnect
  }
  override def postStop = println("MessageActor stopped")

  def receive = openXmlStream(context.actorOf(
    XmlParsingActor.props(xmlInputStream),
    "xml-parsing-actor-" + Random.alphanumeric.take(10).mkString))

  def openXmlStream(xmlParser: ActorRef): Receive = {
    case MessageActor.ProcessMessage(message) => {
      xmlParser ! XmlParsingActor.Parse
      xmlOutputStream.write(message.toArray[Byte])
    }
    case error: XmlStreamError => handleStreamError(error, true)
    case request: XmlParsingActor.OpenStream =>
      validateOpenStreamRequest(request) match {
        case Some(error: XmlStreamError) => handleStreamError(error, true)
        case None => {
          context.parent ! ConnectionActor.ReplyToSender(ByteString(
            buildOpenStreamTag(request) + "\n" +
            XmlResponse.startTlsStreamFeature(request.prefix)))
          context.become(startTls(request.prefix, xmlParser))
        }
      }
    case XmlParsingActor.CloseStream(streamPrefix) => {
      context.parent ! ConnectionActor.ReplyToSender(ByteString(
        XmlResponse.closeStream(streamPrefix)))

      stop
    }
    case MessageActor.Stop => stop
  }

  def startTls(prefix: Option[String], xmlParser: ActorRef): Receive = {
    case MessageActor.ProcessMessage(message) => {
      println("Received: " + message.utf8String)
      xmlParser ! XmlParsingActor.Parse
      xmlOutputStream.write(message.toArray[Byte])
    }
    case error: XmlStreamError => handleStreamError(error, true)
    case tls: XmlParsingActor.StartTls => handleStartTls(tls) match {
      case Some(error: StartTlsError) => {
        log.warning(s"StartTls failure: ${error.message}")
        context.parent ! ConnectionActor.ReplyToSender(ByteString(
          error.toString + "\n" + XmlResponse.closeStream(prefix)))
        stop
      }
      case None => {
        context.parent ! ConnectionActor.ReplyToSender(ByteString(
          XmlResponse.proceedWithTls))

        context.become(negotiateTls(
          prefix,
          context.actorOf(
            Props(classOf[TlsActor]),
            "tls-actor-" + Random.alphanumeric.take(10).mkString),
          recreateXmlParser(xmlParser)))
      }
    }
    case XmlParsingActor.CloseStream(streamPrefix) => {
      context.parent ! ConnectionActor.ReplyToSender(ByteString(
        XmlResponse.closeStream(streamPrefix)))

      stop
    }
    case MessageActor.Stop => stop
  }

  def negotiateTls(prefix: Option[String], tlsActor: ActorRef, xmlParser: ActorRef): Receive = {
    case MessageActor.ProcessMessage(message) => {
      tlsActor ! TlsActor.ProcessMessage(message)
    }
    case MessageActor.ProcessDecryptedMessage(message) => {
      println("Decrypted: " + message.utf8String)
      xmlOutputStream.write(removePaddingFromDecryptedBytes(message).toArray[Byte])
      xmlParser ! XmlParsingActor.Parse
    }
    case MessageActor.PassToClient(message) =>
      context.parent ! ConnectionActor.ReplyToSender(message)
    case MessageActor.Stop => {
      tlsActor ! TlsActor.Stop
      stop
    }
    case error: XmlStreamError => handleStreamErrorWithTls(error, tlsActor)
    case request: XmlParsingActor.OpenStream =>
      validateOpenStreamRequest(request) match {
        case Some(error: XmlStreamError) => handleStreamErrorWithTls(error, tlsActor)
        case None => {
          tlsActor ! TlsActor.SendEncryptedToClient(ByteString(
            buildOpenStreamTag(request) + "\n" +
            XmlResponse.saslStreamFeature(request.prefix)))

          context.become(saslAuthenticate(request.prefix, tlsActor, xmlParser))
        }
      }
    case XmlParsingActor.CloseStream => {
      tlsActor ! TlsActor.SendEncryptedToClient(ByteString(
        XmlResponse.closeStream(prefix)))
    }
  }

  def saslAuthenticate(prefix: Option[String], tlsActor: ActorRef, xmlParser: ActorRef): Receive = {
    case MessageActor.ProcessMessage(message) => {
      tlsActor ! TlsActor.ProcessMessage(message)
    }
    case MessageActor.ProcessDecryptedMessage(message) => {
      println("Decrypted: " + message.utf8String)
      xmlOutputStream.write(removePaddingFromDecryptedBytes(message).toArray[Byte])
      xmlParser ! XmlParsingActor.Parse
    }
    case MessageActor.PassToClient(message) =>
      context.parent ! ConnectionActor.ReplyToSender(message)
    case MessageActor.Stop => {
      tlsActor ! TlsActor.Stop
      stop
    }
    case error: XmlStreamError => handleStreamErrorWithTls(error, tlsActor)
    case XmlParsingActor.CloseStream => {
      tlsActor ! TlsActor.SendEncryptedToClient(ByteString(
        XmlResponse.closeStream(prefix)))
    }
    case XmlParsingActor.AuthenticateWithSasl(mechanism, namespace, base64Value) => {
      if (namespace.isEmpty || namespace.get != "urn:ietf:params:xml:ns:xmpp-sasl") {
        tlsActor ! TlsActor.SendEncryptedToClient(ByteString(
          new FailureWithDefinedCondition("malformed-request").toString))
      }
      else mechanism match {
        case Some("PLAIN") =>
          try {
            base64Value match {
              case None => {
                tlsActor ! TlsActor.SendEncryptedToClient(ByteString(
                  new FailureWithDefinedCondition("incorrect-encoding").toString))
              }
              case Some(base64Str) => {
                // Strip out the authcid, so only authzid and passwd remain.
                val lastParts = Base64.getDecoder().decode(base64Str).dropWhile(_ != 0)

                // Split the remainder into Nul-authzid and Nul-passwd.
                val partsWithNul = lastParts.splitAt(lastParts.lastIndexOf(0))

                // Remove the Nuls to get the username and password.
                val username = new String(partsWithNul._1.drop(1))
                val password = new String(partsWithNul._2.drop(1))

                // Use fake credentials until there's a database of some kind.
                if (username == "test_username" && password == "test_password") {
                  tlsActor ! TlsActor.SendEncryptedToClient(ByteString(
                    XmlResponse.saslSuccess))

                  context.become(bindResource(
                    prefix,
                    tlsActor,
                    xmlParser,
                    username))
                }
                else {
                  tlsActor ! TlsActor.SendEncryptedToClient(ByteString(
                    new FailureWithDefinedCondition("not-authorized").toString))
                }
              }
            }
          }
          catch {
            // Base64 decoding failed, so reply with an incorrect-encoding error.
            case _: IllegalArgumentException =>
              tlsActor ! TlsActor.SendEncryptedToClient(ByteString(
                new FailureWithDefinedCondition("incorrect-encoding").toString))
          }
        case _ => {
          tlsActor ! TlsActor.SendEncryptedToClient(ByteString(
            new FailureWithDefinedCondition("invalid-mechanism").toString))
        }
      }
    }
  }

  // TODO: Do the resource binding.
  def bindResource(prefix: Option[String], tlsActor: ActorRef, xmlParser: ActorRef, user: String): Receive = {
    case MessageActor.ProcessMessage(message) => {
      tlsActor ! TlsActor.ProcessMessage(message)
    }
    case MessageActor.ProcessDecryptedMessage(message) => {
      println("Decrypted: " + message.utf8String)
      xmlOutputStream.write(removePaddingFromDecryptedBytes(message).toArray[Byte])
      xmlParser ! XmlParsingActor.Parse
    }
    case MessageActor.PassToClient(message) =>
      context.parent ! ConnectionActor.ReplyToSender(message)
    case MessageActor.Stop => {
      tlsActor ! TlsActor.Stop
      stop
    }
    case error: XmlStreamError => handleStreamErrorWithTls(error, tlsActor)
    case XmlParsingActor.CloseStream => {
      tlsActor ! TlsActor.SendEncryptedToClient(ByteString(
        XmlResponse.closeStream(prefix)))
    }
  }

  /**
   * Returns the ByteString with trailing \u0000 characters removed.
   *
   * @param bytes the ByteString to remove padding from
   * @return a ByteString equal to the original, minus trailing \u0000 characters
   */
  def removePaddingFromDecryptedBytes(bytes: ByteString) = {
    @tailrec
    def findLastNulIndex(index: Int): Int =
      if (index == 0) bytes.length
      else if (bytes(index) != 0) index + 1
      else findLastNulIndex(index - 1)

    bytes.take(findLastNulIndex(bytes.length - 1))
  }

  def handleStreamError(error: XmlStreamError, includeOpenStream: Boolean = false) = {
    error.message match {
      case Some(errorMessage) => log.warning(s"${ error.errorType }: $errorMessage")
      case None => log.warning(error.errorType)
    }

    val openStreamIfNeeded =
      if (includeOpenStream)
        buildOpenStreamTag(XmlParsingActor.OpenStream(
          error.prefix, MessageActor.ValidStreamNamespace, Map())) + "\n"
      else ""

    context.parent ! ConnectionActor.ReplyToSender(ByteString(
      openStreamIfNeeded + error.toString))

    stop
  }

  def handleStreamErrorWithTls(error: XmlStreamError, tlsActor: ActorRef) = {
    error.message match {
      case Some(errorMessage) => log.warning(s"${ error.errorType }: $errorMessage")
      case None => log.warning(error.errorType)
    }

    tlsActor ! TlsActor.SendEncryptedToClient(ByteString(error.toString))

    stop
  }

  def validateOpenStreamRequest(request: XmlParsingActor.OpenStream): Option[XmlStreamError] =
    if (request.namespaceUri != MessageActor.ValidStreamNamespace)
      Some(new InvalidNamespaceError(request.prefix, Some(request.namespaceUri)))
    else
      None

  def buildOpenStreamTag(request: XmlParsingActor.OpenStream) =
    XmlResponse.openStream(
      prefix = request.prefix,
      contentNamespace = Some("jabber:client"),
      streamId = "abc",
      recipient = request.attributes.get("from"))

  def handleStartTls(request: XmlParsingActor.StartTls): Option[StartTlsError] =
    if (request.namespaceUri != MessageActor.ValidStartTlsNamespace)
      Some(new StartTlsError(request.namespaceUri))
    else
      None

  def recreateXmlParser(xmlParser: ActorRef) = {
    context.stop(xmlParser)
    try { xmlOutputStream.close } catch { case _: Throwable => {} }
    try { xmlInputStream.close } catch { case _: Throwable => {} }

    xmlOutputStream = new PipedOutputStream
    xmlInputStream = new PipedInputStream(xmlOutputStream)

    context.actorOf(
      XmlParsingActor.props(xmlInputStream),
      "xml-parsing-actor-" + Random.alphanumeric.take(10).mkString)
  }
}
