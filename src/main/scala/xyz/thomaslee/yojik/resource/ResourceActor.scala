package xyz.thomaslee.yojik.resource

import akka.actor.{ ActorRef, ActorSystem, Props }
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.TimeUnit
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

import xyz.thomaslee.yojik.xml.{ XmlParsingActor, XmlTag }
import xyz.thomaslee.yojik.xmlstream.{ XmlStreamManaging, HandleTlsMessage }

object ResourceActor {
  val FindActorTimeout = new FiniteDuration(10, TimeUnit.SECONDS)

  def props(connActor: ActorRef,
            xmlParser: ActorRef,
            prefix: Option[String],
            tlsActor: ActorRef,
            user: String,
            resource: String): Props =
    Props(classOf[ResourceActor], connActor, xmlParser, prefix, tlsActor, user, resource)

  def getActorName(user: String, domain: String, resource: String): String =
    new String(
      Base64.getEncoder()
        .encode((user + "@" + domain + "/" + resource).getBytes(StandardCharsets.UTF_8)))
      .replace('/', '.')

  def findActor(system: ActorSystem, user: String, domain: String, resource: String): Future[ActorRef] =
    system.actorSelection(
      JidActor.JidActorPathPrefix + JidActor.getActorName(user, domain) + "/" +
        getActorName(user, domain, resource))
      .resolveOne(FindActorTimeout)
}

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

  def receive: Receive = receiveMessages

  def receiveMessages: Receive = {
    case XmlParsingActor.TagReceived(tag: XmlTag) => {
      log.debug(tag.toString)
    }
    case message => HandleTlsMessage(log, this, xmlParser, prefix, tlsActor)(message)
  }
}
