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

object HandleTlsMessage {
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
    case XmlParsingActor.CloseStream(streamPrefix) =>
      tlsActor ! TlsActor.SendEncryptedToClient(ByteString(
        XmlResponse.closeStream(streamPrefix)))
  }
}
