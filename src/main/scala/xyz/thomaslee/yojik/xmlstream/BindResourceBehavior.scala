package xyz.thomaslee.yojik.xmlstream

import akka.actor.ActorRef
import akka.actor.Actor.Receive
import akka.event.LoggingAdapter
import akka.util.ByteString
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{ Failure, Success }

import xyz.thomaslee.yojik.config.ConfigMap
import xyz.thomaslee.yojik.resource.{ JidActor, ResourceActor }
import xyz.thomaslee.yojik.tls.TlsActor
import xyz.thomaslee.yojik.xml.{
  BadRequestError, InvalidNamespaceError, XmlParsingActor, XmlString, XmlTag
}

/** Handles requests to bind a resource to the XML stream. */
object BindResourceBehavior {
  /** The resource binding XML namespace for XMPP. */
  val ValidBindResourceNamespace = "urn:ietf:params:xml:ns:xmpp-bind"

  /** Regex that matches valid resourceparts in a JID. */
  val ValidResourceRegex = "^[\\0021-\\007E\\u00A1-\\u00AC\\u00AE-\\u00FF\\u0400-\\u04FF]{1,1023}$"

  /**
   * Handles the messages that attempt to bind a resource to the XML stream.
   *
   * @param log the [[akka.event.LoggingAdapter]] to use for logging
   * @param self the [[xyz.thomaslee.yojik.xmlstream.XmlStreamManaging]] actor
   *   instance that is handling unauthenticated XML requests and responses
   * @param xmlParser an ActorRef to the [[xyz.thomaslee.yojik.xml.XmlParsingActor]]
   *   responsible for parsing XML
   * @param prefix the stream prefix for the opening tag of the XML stream
   * @param tlsActor an ActorRef to the [[xyz.thomaslee.yojik.tls.TlsActor]]
   *   responsible for handling the TLS session
   * @param user the authenticated username
   */
  def apply(log: LoggingAdapter,
            self: XmlStreamManaging,
            xmlParser: ActorRef,
            prefix: Option[String],
            tlsActor: ActorRef,
            user: String): Receive = {
    case XmlParsingActor.TagReceived(tag @ XmlTag("iq", _, _, attributes)) => {
      log.debug("Logged in as " + user)
      val id = attributes.get("id")
      val bindTags = tag.getChildTags("bind")
      if (id.isEmpty || !attributes.getOrElse("type", "").equals("set") || bindTags.size != 1) {
        self.handleStreamErrorWithTls(new BadRequestError(id.getOrElse("?")), tlsActor)
      } else if (bindTags.head.namespaceUri.getOrElse("") != ValidBindResourceNamespace) {
        self.handleStreamErrorWithTls(new InvalidNamespaceError(prefix, None), tlsActor)
      } else {
        val jidTags = bindTags.head.getChildTags("jid").headOption
        val resource = jidTags match {
          case Some(jidTag: XmlTag) if jidTag.getContents.size == 1 &&
              jidTag.getContents.head.toString.matches(ValidResourceRegex) =>
            jidTag.getContents.head.toString
          case _ => UUID.randomUUID.toString
        }

        bindResource(self, xmlParser, prefix, tlsActor, user, resource)
      }
    }
    case JidActor.ResourceActorCreated(resourceActor, user, domain, resource) => {
      xmlParser ! XmlParsingActor.Register(resourceActor)
      tlsActor ! XmlStreamManaging.XmlStreamManagerChanged(resourceActor)

      val iqTag = new XmlTag("iq", None, None, Map("type" -> "result"))
      val bindTag = new XmlTag("bind", None, Some(BindResourceBehavior.ValidBindResourceNamespace), Map())
      val jidTag = new XmlTag("jid", None, None, Map())
      jidTag.addContent(new XmlString(user + "@" + domain + "/" + resource))
      bindTag.addContent(jidTag)
      iqTag.addContent(bindTag)

      tlsActor ! TlsActor.SendEncryptedToClient(ByteString(iqTag.toString))

      // Stop the XmlStreamActor without stopping the XmlParsingActor or closing
      // the connection.
      self.context.stop(self.self)
    }
    case message => HandleTlsMessage(log, self, xmlParser, prefix, tlsActor)(message)
  }

  def bindResource(self: XmlStreamManaging,
                   xmlParser: ActorRef,
                   prefix: Option[String],
                   tlsActor: ActorRef,
                   user: String,
                   resource: String): Unit =
    ResourceActor.findActor(self.context.system, user, ConfigMap.domain, resource).onComplete {
      case Success(_) => bindResource(self,
                                      xmlParser,
                                      prefix,
                                      tlsActor,
                                      user,
                                      UUID.randomUUID.toString)
      case _ => {
        JidActor.findActor(self.context.system, user, ConfigMap.domain).onComplete {
          case Success(jidActor) =>
            jidActor ! JidActor.CreateResourceActor(
              self.self,
              self.connectionActor,
              xmlParser,
              prefix,
              tlsActor,
              resource)
          case Failure(_) => {
            val jidActor = self.context.system.actorOf(
              JidActor.props(user, ConfigMap.domain),
              JidActor.getActorName(user, ConfigMap.domain))
            jidActor ! JidActor.CreateResourceActor(
              self.self,
              self.connectionActor,
              xmlParser,
              prefix,
              tlsActor,
              resource)
          }
        }
      }
    }
}
