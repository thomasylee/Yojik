package xyz.thomaslee.yojik

object ConnectionActor {
  case object Disconnect
  case class ReplyToSender(message: String)
}
