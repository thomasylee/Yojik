package im.yojik.xmpp

import akka.actor.{ Actor, ActorLogging, ActorRef, ActorSystem, Props }
import im.yojik.xmpp.server.Server

object Main {
  def main(args: Array[String]) {
    val system: ActorSystem = ActorSystem("yojik")
    val server: ActorRef = system.actorOf(Props[Server], "server")
  }
}
