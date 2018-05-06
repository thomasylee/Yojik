package xyz.thomaslee.yojik

import akka.actor.{ ActorRef, ActorSystem, DeadLetter, Props }

import xyz.thomaslee.yojik.tcp.TcpServer

object Main {
  def main(args: Array[String]) {
    val system: ActorSystem = ActorSystem("yojik")
    system.actorOf(Props[TcpServer], "tcp-server")

    val deadLetterActor: ActorRef = system.actorOf(Props[DeadLetterActor], "dead-letter-actor")
    system.eventStream.subscribe(deadLetterActor, classOf[DeadLetter])
  }
}
