package xyz.thomaslee.yojik.xmlstream

import akka.actor.ActorRef
import akka.actor.Actor.Receive
import akka.event.LoggingAdapter
import akka.util.ByteString
import scala.util.{ Failure, Success, Try }

import xyz.thomaslee.yojik.ConnectionActor
import xyz.thomaslee.yojik.xml.{
  BadFormatError, XmlParsingActor, XmlResponse, XmlStreamError
}

/** Handles the opening of a new XML stream as defined in RFC-6120. */
object OpenStreamBehavior {
  /**
   * Handles messages that attempt to negotiate a new XML stream.
   *
   * @param log the [[akka.event.LoggingAdapter]] to use for logging
   * @param self the [[xyz.thomaslee.yojjik.xmlstream.XmlStreamActor]] instance
   *   that is handling unauthenticated XML requests and responses
   * @param xmlParser an ActorRef to the [[xyz.thomaslee.yojik.xml.XmlParsingActor]]
   *   responsible for parsing XML
   */
  def apply(log: LoggingAdapter, self: XmlStreamActor, xmlParser: ActorRef): Receive = {
    case XmlStreamActor.ProcessMessage(message) => {
      Try(self.xmlOutputStream.write(message.toArray[Byte])) match {
        case Success(_) => xmlParser ! XmlParsingActor.Parse
        case Failure(error) => {
          log.warning(error.toString)
          self.handleStreamError(new BadFormatError(None, None), true)
          self.stop
        }
      }
    }
    case error: XmlStreamError => self.handleStreamError(error, true)
    case request: XmlParsingActor.OpenStream =>
      self.validateOpenStreamRequest(request) match {
        case Some(error: XmlStreamError) =>
          self.handleStreamError(error, true)
        case None => {
          self.tcpConnectionActor ! ConnectionActor.ReplyToSender(ByteString(
            self.buildOpenStreamTag(request) + "\n" +
            XmlResponse.startTlsStreamFeature(request.prefix)))

          self.become(StartTlsBehavior(log, self, xmlParser, request.prefix))
        }
      }
    case XmlParsingActor.CloseStream(streamPrefix) => {
      self.tcpConnectionActor ! ConnectionActor.ReplyToSender(ByteString(
        XmlResponse.closeStream(streamPrefix)))

      self.stop
    }
    case XmlStreamActor.Stop => self.stop
  }
}
