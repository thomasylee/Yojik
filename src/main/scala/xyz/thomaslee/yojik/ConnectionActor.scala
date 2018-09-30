package xyz.thomaslee.yojik

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
}
