package xyz.thomaslee.yojik.xmlstream

import akka.actor.{ ActorRef, Props }
import akka.actor.Actor.Receive
import akka.event.LoggingAdapter
import akka.util.ByteString
import scala.util.{ Failure, Random, Success, Try }

import xyz.thomaslee.yojik.ConnectionActor
import xyz.thomaslee.yojik.config.ConfigMap
import xyz.thomaslee.yojik.tls.TlsActor
import xyz.thomaslee.yojik.xml.{
  BadFormatError, InvalidNamespaceError, StartTlsError, XmlParsingActor,
  XmlResponse, XmlStreamError
}

object StartTlsBehavior {
  val ValidStartTlsNamespace = "urn:ietf:params:xml:ns:xmpp-tls"

  def apply(log: LoggingAdapter, self: XmlStreamActor, xmlParser: ActorRef, prefix: Option[String]): Receive = {
    case XmlStreamActor.ProcessMessage(message) => {
      log.debug("Received: " + message.utf8String)
      Try(self.xmlOutputStream.write(message.toArray[Byte])) match {
        case Success(_) => xmlParser ! XmlParsingActor.Parse
        case Failure(error) => {
          log.warning(error.toString)
          self.handleStreamError(new BadFormatError(prefix, None), false)
          self.stop
        }
      }
    }
    case error: XmlStreamError => self.handleStreamError(error, true)
    case tls: XmlParsingActor.StartTls => handleStartTls(tls) match {
      case Some(error: StartTlsError) => {
        log.warning("StartTls failure")
        self.tcpConnectionActor ! ConnectionActor.ReplyToSender(ByteString(
          error.toString + "\n" + XmlResponse.closeStream(prefix)))
        self.stop
      }
      case None => {
        self.tcpConnectionActor ! ConnectionActor.ReplyToSender(ByteString(
          XmlResponse.proceedWithTls))

        self.context.become(NegotiateTlsBehavior(
          log,
          self,
          self.recreateXmlParser(xmlParser),
          prefix,
          self.context.actorOf(
            Props(classOf[TlsActor]),
            "tls-actor-" + Random.alphanumeric.take(
              ConfigMap.randomCharsInActorNames).mkString)))
      }
    }
    case XmlParsingActor.CloseStream(streamPrefix) => {
      self.tcpConnectionActor ! ConnectionActor.ReplyToSender(ByteString(
        XmlResponse.closeStream(streamPrefix)))

      self.stop
    }
    case XmlStreamActor.Stop => self.stop
  }

  def handleStartTls(request: XmlParsingActor.StartTls): Option[StartTlsError] = request.namespaceUri match {
    case Some(uri) if uri == ValidStartTlsNamespace => None
    case _ => Some(new StartTlsError)
  }
}
