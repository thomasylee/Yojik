package xyz.thomaslee.yojik.resource

import akka.actor.{ Actor, ActorLogging, ActorRef, ActorSystem, Props }
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.TimeUnit
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

/**
 * Contains messages, constants, and methods pertaining to
 * [[xyz.thomaslee.yojik.resource.JidActor]] actors.
 */
object JidActor {
  /**
   * Indicates that a [[xyz.thomaslee.yojik.resource.ResourceActor]] should
   * be created for a particular resource.
   *
   * @param xmlStreamManager the actor managing the XML stream
   * @param connActor the actor managing the connection
   * @param xmlParser the actor parsing XML into tags
   * @param prefix the XML prefix for the XML stream, if there is one
   * @param tlsActor the actor managing the TLS connection details
   * @param resource the resourcepart of the JID
   */
  case class CreateResourceActor(xmlStreamManager: ActorRef,
                                 connActor: ActorRef,
                                 xmlParser: ActorRef,
                                 prefix: Option[String],
                                 tlsActor: ActorRef,
                                 resource: String)

  /**
   * Indicates that the [[xyz.thomaslee.yojik.resource.ResourceActor]]
   * was created successfully.
   *
   * @param resourceActor the created actor
   * @param user the username, or "localpart" of the JID according to RFC-6122
   * @param domain the domainpart of the JID
   * @param resource the resourcepart of the JID
   */
  case class ResourceActorCreated(resourceActor: ActorRef,
                                  user: String,
                                  domain: String,
                                  resource: String)

  /** The prefix used when searching for JidActors. */
  val JidActorPathPrefix = "/user/jid/"

  /** The timeout for how long to wait to find a JidActor. */
  val FindActorTimeout = new FiniteDuration(10, TimeUnit.SECONDS)

  /**
   * Returns [[akka.actor.Props]] to use to create a
   * [[xyz.thomaslee.yojik.resource.JidActor]].
   *
   * @param user the username, or "localpart" of the JID according to RFC-6122
   * @param domain the domainpart of the JID
   * @return a new [[akka.actor.Props]] instance to use to create a
   *   [[xyz.thomaslee.yojik.resource.JidActor]]
   */
  def props(user: String, domain: String): Props =
    Props(classOf[JidActor], user, domain)

  /**
   * Returns the actor name for a particular user and domain. Due to naming
   * constraints for actors, the name is the base64 encoding of the user@domain
   * with all "/" replaced by ".".
   *
   * @param user the username, or "localpart" of the JID according to RFC-6122
   * @param domain the domainpart of the JID
   * @return the actor name for the user and domain
   */
  def getActorName(user: String, domain: String): String =
    new String(
      Base64.getEncoder()
        .encode((user + "@" + domain).getBytes(StandardCharsets.UTF_8)))
      .replace('/', '.')

  /**
   * Returns a Future which, if successful, contains an [[akka.actor.ActorRef]] to
   * the [[xyz.thomaslee.yojik.resource.ResourceActor]] for the given user and domain.
   *
   * @param system the [[akka.actor.ActorSystem]] to use to search for the actor
   * @param user the username, or "localpart" of the JID according to RFC-6122
   * @param domain the domainpart of the JID
   * @return a Future of the ActorRef to the actor for the user and domain
   */
  def findActor(system: ActorSystem, user: String, domain: String): Future[ActorRef] =
    system.actorSelection(JidActorPathPrefix + getActorName(user, domain))
      .resolveOne(FindActorTimeout)
}

/**
 * Coordinates actions related to a particular user (localpart) and domain
 * (domainpart), such as forwarding messages sent to user@domain to all
 * presently bound resources for user@domain.
 *
 * @param user the username, or "localpart" of the JID according to RFC-6122
 * @param domain the domainpart of the JID
 */
class JidActor(user: String, domain: String) extends Actor with ActorLogging {
  override def postStop: Unit = log.debug(s"JidActor($user, $domain) stopped")

  /**
   * Receives Akka messages with different behaviors, starting with the
   * receiveMessages behavior.
   */
  def receive: Receive = receiveMessages

  /**
   * Handles messages sent to this actor.
   */
  def receiveMessages: Receive = {
    case JidActor.CreateResourceActor(xmlStreamManager, connActor, xmlParser, prefix, tlsActor, resource) => {
      val resourceActor = context.actorOf(
        ResourceActor.props(connActor, xmlParser, prefix, tlsActor, user, resource),
          ResourceActor.getActorName(user, domain, resource))
      xmlStreamManager ! JidActor.ResourceActorCreated(resourceActor, user, domain, resource)
    }
  }
}
