package xyz.thomaslee.yojik.handlers

import akka.actor.{ Actor, ActorRef, Props }
import akka.io.Tcp.Write
import akka.util.ByteString

object XmlStreamHandler {
  def props(connection: ActorRef) =
    Props(classOf[XmlStreamHandler], connection)
}

/**
 * XmlStreamHandler establishes an XML stream with the client.
 */
class XmlStreamHandler(connection: ActorRef) extends Actor {
  def receive = {
    case Handler.Handle(message) => connection! Write(ByteString(message))
  }
}
