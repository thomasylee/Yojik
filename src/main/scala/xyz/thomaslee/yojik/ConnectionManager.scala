package xyz.thomaslee.yojik

object ConnectionManager {
  case object Disconnect
  case class ReplyToSender(message: String)
}
