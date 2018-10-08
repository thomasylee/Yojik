package xyz.thomaslee.yojik.resource

import akka.actor.{ Actor, ActorLogging, ActorRef, ActorSystem, Props }
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.TimeUnit
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

object JidActor {
  case class CreateResourceActor(xmlStreamManager: ActorRef,
                                 connActor: ActorRef,
                                 xmlParser: ActorRef,
                                 prefix: Option[String],
                                 tlsActor: ActorRef,
                                 resource: String)

  case class ResourceActorCreated(resourceActor: ActorRef,
                                  user: String,
                                  domain: String,
                                  resource: String)

  val JidActorPathPrefix = "/user/jid/"

  val FindActorTimeout = new FiniteDuration(10, TimeUnit.SECONDS)

  def props(user: String, domain: String): Props =
    Props(classOf[JidActor], user, domain)

  def getActorName(user: String, domain: String): String =
    new String(
      Base64.getEncoder()
        .encode((user + "@" + domain).getBytes(StandardCharsets.UTF_8)))
      .replace('/', '.')

  def findActor(system: ActorSystem, user: String, domain: String): Future[ActorRef] =
    system.actorSelection(JidActorPathPrefix + getActorName(user, domain))
      .resolveOne(FindActorTimeout)
}

class JidActor(user: String, domain: String) extends Actor with ActorLogging {
  override def postStop: Unit = log.debug(s"JidActor($user, $domain) stopped")

  def receive: Receive = receiveMessages

  def receiveMessages: Receive = {
    case JidActor.CreateResourceActor(xmlStreamManager, connActor, xmlParser, prefix, tlsActor, resource) => {
      val resourceActor = context.actorOf(
        ResourceActor.props(connActor, xmlParser, prefix, tlsActor, user, resource),
          ResourceActor.getActorName(user, domain, resource))
      xmlStreamManager ! JidActor.ResourceActorCreated(resourceActor, user, domain, resource)
    }
  }
}
