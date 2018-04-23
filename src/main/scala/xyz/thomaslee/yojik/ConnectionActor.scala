package xyz.thomaslee.yojik

import akka.util.ByteString

object ConnectionActor {
  case object Disconnect
  case class ReplyToSender(message: ByteString)
}
