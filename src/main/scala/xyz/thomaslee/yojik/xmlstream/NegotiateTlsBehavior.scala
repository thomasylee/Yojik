package xyz.thomaslee.yojik.xmlstream

import akka.actor.ActorRef
import akka.actor.Actor.Receive
import akka.event.LoggingAdapter
import akka.util.ByteString
import scala.util.{ Failure, Success, Try }

import xyz.thomaslee.yojik.ConnectionActor
import xyz.thomaslee.yojik.tls.TlsActor
import xyz.thomaslee.yojik.xml.{
  BadFormatError, XmlParsingActor, XmlResponse, XmlStreamError
}

object NegotiateTlsBehavior {
  def apply(log: LoggingAdapter, self: XmlStreamActor, xmlParser: ActorRef, prefix: Option[String], tlsActor: ActorRef): Receive = {
    case XmlStreamActor.ProcessMessage(message) =>
      tlsActor ! TlsActor.ProcessMessage(message)
    case XmlStreamActor.ProcessDecryptedMessage(message) => {
      log.debug("Decrypted: " + message.utf8String)
      Try(self.xmlOutputStream.write(message.toArray[Byte])) match {
        case Success(_) => xmlParser ! XmlParsingActor.Parse
        case Failure(error) => {
          log.warning(error.toString)
          self.handleStreamError(new BadFormatError(prefix, None), false)
          self.stop
        }
      }
    }
    case XmlStreamActor.PassToClient(message) =>
      self.tcpConnectionActor ! ConnectionActor.ReplyToSender(message)
    case XmlStreamActor.Stop => {
      tlsActor ! TlsActor.Stop
      self.stop
    }
    case error: XmlStreamError => self.handleStreamErrorWithTls(error, tlsActor)
    case request: XmlParsingActor.OpenStream =>
      self.validateOpenStreamRequest(request) match {
        case Some(error: XmlStreamError) =>
          self.handleStreamErrorWithTls(error, tlsActor)
        case None => {
          tlsActor ! TlsActor.SendEncryptedToClient(ByteString(
            self.buildOpenStreamTag(request) + "\n" +
            XmlResponse.saslStreamFeature(request.prefix)))

          self.context.become(SaslAuthenticateBehavior(
            log,
            self,
            xmlParser,
            request.prefix,
            tlsActor))
        }
      }
    case XmlParsingActor.CloseStream => {
      tlsActor ! TlsActor.SendEncryptedToClient(ByteString(
        XmlResponse.closeStream(prefix)))
    }
  }
}
