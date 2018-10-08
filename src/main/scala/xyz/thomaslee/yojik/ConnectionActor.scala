package xyz.thomaslee.yojik

import akka.actor.ActorRef
import akka.util.ByteString

/**
 * Contains objects and case classes to be recognized by any connection-handling
 * actor.
 */
object ConnectionActor {
  /** Indicates that the connection should be disconnected. */
  case object Disconnect

  /** Indicates that the given message should be passed on to the sender. */
  case class ReplyToSender(message: ByteString)

  /** Indicates that a [[xyz.thomaslee.yojik.tls.TlsActor]] should be created. */
  case class CreateTlsActor(respondTo: ActorRef)

  /** Indicates that the actor created a [[xyz.thomaslee.yojik.tls.TlsActor]]. */
  case class TlsActorCreated(tlsActor: ActorRef)
}
