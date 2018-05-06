package xyz.thomaslee.yojik.xmlstream

import akka.actor.ActorRef
import akka.actor.Actor.Receive
import akka.event.LoggingAdapter
import akka.util.ByteString

import xyz.thomaslee.yojik.tls.TlsActor
import xyz.thomaslee.yojik.xml.{ XmlParsingActor, XmlResponse, XmlStreamError }

object OpenStreamForBindResourceBehavior {
  def apply(log: LoggingAdapter, self: XmlStreamActor, xmlParser: ActorRef, prefix: Option[String], tlsActor: ActorRef, user: String): Receive = {
    case request: XmlParsingActor.OpenStream =>
      self.validateOpenStreamRequest(request) match {
        case Some(error: XmlStreamError) =>
          self.handleStreamErrorWithTls(error, tlsActor)
        case None => {
          tlsActor ! TlsActor.SendEncryptedToClient(ByteString(
            self.buildOpenStreamTag(request) +
            XmlResponse.resourceBindFeature(request.prefix)))

          self.context.become(BindResourceBehavior(
            log,
            self,
            xmlParser,
            request.prefix,
            tlsActor,
            user))
        }
      }
    case message => HandleTlsMessage(log, self, xmlParser, prefix, tlsActor)(message)
  }
}
