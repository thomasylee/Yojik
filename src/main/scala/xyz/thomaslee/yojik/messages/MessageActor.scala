package xyz.thomaslee.yojik.messages

import akka.actor.{ Actor, ActorLogging, ActorRef, PoisonPill, Props }
import akka.util.ByteString
import java.io.{ PipedInputStream, PipedOutputStream }
import java.util.Base64
import org.xml.sax.SAXParseException
import scala.annotation.tailrec
import scala.util.{ Failure, Random, Success, Try }
import scala.xml.XML

import xyz.thomaslee.yojik.ConnectionActor
import xyz.thomaslee.yojik.config.ConfigMap
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

  def stop: Unit = {
    context.stop(self)
    try { xmlOutputStream.close } catch { case _: Throwable => {} }
    context.parent ! ConnectionActor.Disconnect
  }
  override def postStop: Unit = log.debug("MessageActor stopped")

  def receive: Receive = openXmlStream(context.actorOf(
    XmlParsingActor.props(xmlInputStream),
    "xml-parsing-actor-" + Random.alphanumeric.take(
      ConfigMap.randomCharsInActorNames).mkString))

  def openXmlStream(xmlParser: ActorRef): Receive = {
    case MessageActor.ProcessMessage(message) => {
      Try(xmlOutputStream.write(message.toArray[Byte])) match {
        case Success(_) => xmlParser ! XmlParsingActor.Parse
        case Failure(error) => {
          log.warning(error.toString)
          handleStreamError(new BadFormatError(None, None), true)
          stop
        }
      }
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
      log.debug("Received: " + message.utf8String)
      Try(xmlOutputStream.write(message.toArray[Byte])) match {
        case Success(_) => xmlParser ! XmlParsingActor.Parse
        case Failure(error) => {
          log.warning(error.toString)
          handleStreamError(new BadFormatError(prefix, None), false)
          stop
        }
      }
    }
    case error: XmlStreamError => handleStreamError(error, true)
    case tls: XmlParsingActor.StartTls => handleStartTls(tls) match {
      case Some(error: StartTlsError) => {
        log.warning("StartTls failure")
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
            "tls-actor-" + Random.alphanumeric.take(
              ConfigMap.randomCharsInActorNames).mkString),
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
    case MessageActor.ProcessMessage(message) =>
      tlsActor ! TlsActor.ProcessMessage(message)
    case MessageActor.ProcessDecryptedMessage(message) => {
      log.debug("Decrypted: " + message.utf8String)
      Try(xmlOutputStream.write(message.toArray[Byte])) match {
        case Success(_) => xmlParser ! XmlParsingActor.Parse
        case Failure(error) => {
          log.warning(error.toString)
          handleStreamError(new BadFormatError(prefix, None), false)
          stop
        }
      }
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
      log.debug("Decrypted: " + message.utf8String)
      Try(xmlOutputStream.write(message.toArray[Byte])) match {
        case Success(_) => xmlParser ! XmlParsingActor.Parse
        case Failure(error) => {
          log.warning(error.toString)
          handleStreamError(new BadFormatError(prefix, None), false)
          stop
        }
      }
    }
    case MessageActor.PassToClient(message) =>
      context.parent ! ConnectionActor.ReplyToSender(message)
    case MessageActor.Stop => {
      tlsActor ! TlsActor.Stop
      stop
    }
    case error: XmlStreamError => handleStreamErrorWithTls(error, tlsActor)
    case XmlParsingActor.CloseStream =>
      tlsActor ! TlsActor.SendEncryptedToClient(ByteString(
        XmlResponse.closeStream(prefix)))
    case XmlParsingActor.AuthenticateWithSasl(mechanism, namespace, base64Value) => namespace match {
      case Some(ns) if ns == "urn:ietf:params:xml:ns:xmpp-sasl" =>
        mechanism match {
          case Some("PLAIN") => authenticateWithSaslPlain(prefix, tlsActor, xmlParser, base64Value)
          case _ =>
            tlsActor ! TlsActor.SendEncryptedToClient(ByteString(
              new FailureWithDefinedCondition("invalid-mechanism").toString))
        }
      case _ =>
        tlsActor ! TlsActor.SendEncryptedToClient(ByteString(
          new FailureWithDefinedCondition("malformed-request").toString))
    }
  }

  // TODO: Do the resource binding.
  def bindResource(prefix: Option[String], tlsActor: ActorRef, xmlParser: ActorRef, user: String): Receive = {
    case MessageActor.ProcessMessage(message) =>
      tlsActor ! TlsActor.ProcessMessage(message)
    case MessageActor.ProcessDecryptedMessage(message) => {
      log.debug("Decrypted: " + message.utf8String)
      Try(xmlOutputStream.write(message.toArray[Byte])) match {
        case Success(_) => xmlParser ! XmlParsingActor.Parse
        case Failure(error) => {
          log.warning(error.toString)
          handleStreamError(new BadFormatError(prefix, None), false)
          stop
        }
      }
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
   * Authenticate with SASL with the PLAIN mechanism.
   *
   * @param prefix the XML stream namespace prefix
   * @param tlsActor the TlsActor for encrypting the connection
   * @param xmlParser the XmlParsingActor to parse the XML stream elements
   * @param base64Value the base64 value passed in the auth element
   */
  def authenticateWithSaslPlain(prefix: Option[String], tlsActor: ActorRef, xmlParser: ActorRef, base64Value: Option[String]): Unit =
    base64Value match {
      case None => {
        tlsActor ! TlsActor.SendEncryptedToClient(ByteString(
          new FailureWithDefinedCondition("incorrect-encoding").toString))
      }
      case Some(base64Str) => {
        Try(Base64.getDecoder().decode(base64Str)) match {
          case Success(decoded) => {
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
          case Failure(_) =>
            // Base64 decoding failed, so reply with an incorrect-encoding error.
            tlsActor ! TlsActor.SendEncryptedToClient(ByteString(
              new FailureWithDefinedCondition("incorrect-encoding").toString))
        }
      }
    }

  /**
   * Returns the ByteString with trailing \u0000 characters removed.
   *
   * @param bytes the ByteString to remove padding from
   * @return a ByteString equal to the original, minus trailing \u0000 characters
   */
  def removePaddingFromDecryptedBytes(bytes: ByteString): ByteString = {
    @tailrec
    def findLastNulIndex(index: Int): Int =
      if (index == 0) {
        bytes.length
      }
      else if (bytes(index) != 0) {
        index + 1
      }
      else {
        findLastNulIndex(index - 1)
      }

    bytes.take(findLastNulIndex(bytes.length - 1))
  }

  def handleStreamError(error: XmlStreamError, includeOpenStream: Boolean = false): Unit = {
    error.message match {
      case Some(errorMessage) => log.warning(s"${ error.errorType }: $errorMessage")
      case None => log.warning(error.errorType)
    }

    val openStreamIfNeeded =
      if (includeOpenStream) {
        buildOpenStreamTag(XmlParsingActor.OpenStream(
          error.prefix, Some(MessageActor.ValidStreamNamespace), Map())) + "\n"
      }
      else {
        ""
      }

    context.parent ! ConnectionActor.ReplyToSender(ByteString(
      openStreamIfNeeded + error.toString))

    stop
  }

  def handleStreamErrorWithTls(error: XmlStreamError, tlsActor: ActorRef): Unit = {
    error.message match {
      case Some(errorMessage) => log.warning(s"${ error.errorType }: $errorMessage")
      case None => log.warning(error.errorType)
    }

    tlsActor ! TlsActor.SendEncryptedToClient(ByteString(error.toString))

    stop
  }

  def validateOpenStreamRequest(request: XmlParsingActor.OpenStream): Option[XmlStreamError] = request.namespaceUri match {
    case Some(uri) if uri == MessageActor.ValidStreamNamespace => None
    case _ => Some(new InvalidNamespaceError(request.prefix, request.namespaceUri))
  }

  def buildOpenStreamTag(request: XmlParsingActor.OpenStream): String =
    XmlResponse.openStream(
      prefix = request.prefix,
      contentNamespace = Some("jabber:client"),
      streamId = "abc",
      recipient = request.attributes.get("from"))

  def handleStartTls(request: XmlParsingActor.StartTls): Option[StartTlsError] = request.namespaceUri match {
    case Some(uri) if uri == MessageActor.ValidStartTlsNamespace => None
    case _ => Some(new StartTlsError)
  }

  def recreateXmlParser(xmlParser: ActorRef): ActorRef = {
    context.stop(xmlParser)
    try { xmlOutputStream.close } catch { case _: Throwable => {} }

    xmlOutputStream = new PipedOutputStream
    xmlInputStream = new PipedInputStream(xmlOutputStream)

    context.actorOf(
      XmlParsingActor.props(xmlInputStream),
      "xml-parsing-actor-" + Random.alphanumeric.take(
        ConfigMap.randomCharsInActorNames).mkString)
  }
}
