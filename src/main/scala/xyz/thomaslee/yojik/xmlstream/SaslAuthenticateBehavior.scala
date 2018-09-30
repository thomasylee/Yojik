package xyz.thomaslee.yojik.xmlstream

import akka.actor.ActorRef
import akka.actor.Actor.Receive
import akka.event.LoggingAdapter
import akka.util.ByteString
import java.util.Base64
import scala.util.{ Failure, Success, Try }

import xyz.thomaslee.yojik.config.ConfigMap
import xyz.thomaslee.yojik.tls.TlsActor
import xyz.thomaslee.yojik.xml.{
  FailureWithDefinedCondition, XmlParsingActor, XmlResponse
}

/** Handles requests to authenticate using SASL. */
object SaslAuthenticateBehavior {
  val ValidSaslNamespace = "urn:ietf:params:xml:ns:xmpp-sasl"

  /**
   * Handles the messages that attempt to authenticate via SASL.
   *
   * @param log the [[akka.event.LoggingAdapter]] to use for logging
   * @param self the [[xyz.thomaslee.yojik.xmlstream.XmlStreamActor]] instance
   *   that is handling unauthenticated XML requests and responses
   * @param xmlParser an ActorRef to the [[xyz.thomaslee.yojik.xml.XmlParsingActor]]
   *   responsible for parsing XML
   * @param prefix the stream prefix for the opening tag of the XML stream
   * @param tlsActor an ActorRef to the [[xyz.thomaslee.yojik.tls.TlsActor]]
   *   responsible for handling the TLS session
   */
  def apply(log: LoggingAdapter, self: XmlStreamActor, xmlParser: ActorRef, prefix: Option[String], tlsActor: ActorRef): Receive = {
    case XmlParsingActor.AuthenticateWithSasl(mechanism, namespace, base64Value) => namespace match {
      case Some(ns) if ns == ValidSaslNamespace => mechanism match {
        case Some("PLAIN") =>
          authenticateWithSaslPlain(log, self, xmlParser, prefix, tlsActor, base64Value)
        case _ =>
          tlsActor ! TlsActor.SendEncryptedToClient(ByteString(
            new FailureWithDefinedCondition("invalid-mechanism").toString))
      }
      case _ =>
        tlsActor ! TlsActor.SendEncryptedToClient(ByteString(
          new FailureWithDefinedCondition("malformed-request").toString))
    }
    case message => HandleTlsMessage(log, self, xmlParser, prefix, tlsActor)(message)
  }

  /**
   * Authenticate with SASL with the PLAIN mechanism.
   *
   * @param log the LoggingAdapter to use for logging
   * @param self the XmlStreamActor using this behavior
   * @param xmlParser the XmlParsingActor to parse the XML stream elements
   * @param prefix the XML stream namespace prefix
   * @param tlsActor the TlsActor for encrypting the connection
   * @param base64Value the base64 value passed in the auth element
   */
  def authenticateWithSaslPlain(log: LoggingAdapter,
                                self: XmlStreamActor,
                                xmlParser: ActorRef,
                                prefix: Option[String],
                                tlsActor: ActorRef,
                                base64Value: Option[String]): Unit =
    base64Value match {
      case None =>
        tlsActor ! TlsActor.SendEncryptedToClient(ByteString(
          new FailureWithDefinedCondition("incorrect-encoding").toString))
      case Some(base64Str) => {
        Try(Base64.getDecoder().decode(base64Str)) match {
          case Success(decoded) => {
            // Strip out the authcid, so only authzid and passwd remain.
            val lastParts = decoded.dropWhile(_ != 0)

            // Split the remainder into Nul-authzid and Nul-passwd, then extract
            // the username and password.
            val (username: String, password: String) = lastParts
              .splitAt(lastParts.lastIndexOf(0))
              .productIterator
              .toList
              .map { case bytes: Array[Byte] => new String(bytes) } match {
                // Remove the Nuls to get the username and password.
                case List(user, pswd) => (user.drop(1), pswd.drop(1))
                case _ => ("", "")
              }

            // Use fake credentials until there's a database of some kind.
            if (ConfigMap.storageAdapter.validateCredentials(username, password, "PLAIN")) {
              tlsActor ! TlsActor.SendEncryptedToClient(ByteString(
                XmlResponse.saslSuccess))

              self.context.become(OpenStreamForBindResourceBehavior(
                log,
                self,
                self.recreateXmlParser(xmlParser),
                prefix,
                tlsActor,
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
}
