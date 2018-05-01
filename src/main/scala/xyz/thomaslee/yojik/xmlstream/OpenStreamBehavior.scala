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

object OpenStreamBehavior {
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
