package xyz.thomaslee.yojik.resource

import akka.actor.{ ActorRef, ActorSystem, Props }
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.TimeUnit
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

import xyz.thomaslee.yojik.xml.{ XmlParsingActor, XmlTag }
import xyz.thomaslee.yojik.xmlstream.{ XmlStreamManaging, HandleTlsMessage }

/**
 * Contains messages, constants, and methods pertaining to
 * [[xyz.thomaslee.yojik.resource.ResourceActor]] actors.
 */
object ResourceActor {
  /** The timeout for how long to wait to find a ResourceActor. */
  val FindActorTimeout = new FiniteDuration(10, TimeUnit.SECONDS)

  /**
   * Returns [[akka.actor.Props]] to use to create a
   * [[xyz.thomaslee.yojik.resource.ResourceActor]].
   *
   * @param connActor the actor managing the connection
   * @param xmlParser the actor parsing the XML stream
   * @param prefix the XML stream prefix, if there is one
   * @param tlsActor the actor managing the TLS session details
   * @param user the localpart of the JID
   * @param resource the resourcepart of the JID
   * @return a new [[akka.actor.Props]] instance to use to create a
   *   [[xyz.thomaslee.yojik.resource.ResourceActor]]
   */
  def props(connActor: ActorRef,
            xmlParser: ActorRef,
            prefix: Option[String],
            tlsActor: ActorRef,
            user: String,
            resource: String): Props =
    Props(classOf[ResourceActor], connActor, xmlParser, prefix, tlsActor, user, resource)

  /**
   * Returns the actor name for a particular full JID. Due to naming constraints
   * for actors, the name is the base64 encoding of the user@domain/resource with
   * all "/" replaced by ".".
   *
   * @param user the localpart of the JID
   * @param domain the domainpart of the JID
   * @param resource the resourcepart of the JID
   * @return the actor name for the full JID
   */
  def getActorName(user: String, domain: String, resource: String): String =
    new String(
      Base64.getEncoder()
        .encode((user + "@" + domain + "/" + resource).getBytes(StandardCharsets.UTF_8)))
      .replace('/', '.')

  /**
   * Returns a Future which, if successful, contains an [[akka.actor.ActorRef]] to
   * the [[xyz.thomaslee.yojik.resource.ResourceActor]] for the given full JID.
   *
   * @param system the [[akka.actor.ActorSystem]] to use to search for the actor
   * @param user the localpart of the JID
   * @param domain the domainpart of the JID
   * @param resource the resourcepart of the JID
   * @return a Future of the ActorRef to the actor for the full JID
   */
  def findActor(system: ActorSystem, user: String, domain: String, resource: String): Future[ActorRef] =
    system.actorSelection(
      JidActor.JidActorPathPrefix + JidActor.getActorName(user, domain) + "/" +
        getActorName(user, domain, resource))
      .resolveOne(FindActorTimeout)
}

/**
 * Handles messages to and from the client bound to the actor's full JID.
 * ResourceActor is a subclass of [[xyz.thomaslee.yojik.xmlstream.XmlStreamManaging]]
 * since it takes over the XML stream from
 * [[xyz.thomaslee.yojik.xmlstream.XmlStreamActor]] once the client has been bound
 * to a resource.
 *
 * @param connActor the actor managing the connection
 * @param xmlParser the actor parsing the XML into tags
 * @param prefix the XML prefix for the XML stream, if there is one
 * @param tlsActor the actor managing the TLS connection details
 * @param resource the resourcepart of the JID
 */
class ResourceActor(connActor: ActorRef,
                    xmlParser: ActorRef,
                    prefix: Option[String],
                    tlsActor: ActorRef,
                    user: String,
                    resource: String) extends XmlStreamManaging {
  override def postStop: Unit = log.debug(s"ResourceActor($resource) stopped")

  /**
   * Returns an [[akka.actor.ActorRef]] to the actor responsible for the connection.
   *
   * @return an [[akka.actor.ActorRef]] to the actor responsible for the connection
   */
  override def connectionActor: ActorRef = connActor

  /**
   * Receives Akka messages with different behaviors, starting with the
   * receiveMessages behavior.
   */
  def receive: Receive = receiveMessages

  /**
   * Handles messages sent to this actor.
   */
  def receiveMessages: Receive = {
    case XmlParsingActor.TagReceived(tag: XmlTag) => {
      log.debug(tag.toString)
    }
    case message => HandleTlsMessage(log, this, xmlParser, prefix, tlsActor)(message)
  }
}
