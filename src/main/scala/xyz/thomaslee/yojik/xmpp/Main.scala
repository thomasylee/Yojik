package xyz.thomaslee.yojik.xmpp

import akka.actor.{ Actor, ActorLogging, ActorRef, ActorSystem, Props }

import xyz.thomaslee.yojik.xmpp.tcp.TcpServer

object Main {
  def main(args: Array[String]) {
    val system: ActorSystem = ActorSystem("yojik")
    val server: ActorRef = system.actorOf(Props[TcpServer], "tcp-server")
  }
}
