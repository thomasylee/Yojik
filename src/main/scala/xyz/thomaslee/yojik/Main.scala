package xyz.thomaslee.yojik

import akka.actor.{ ActorRef, ActorSystem, DeadLetter, Props }

import xyz.thomaslee.yojik.tcp.TcpServer

/** This object starts the XMPP server by calling its main(Array[String]) method. */
object Main {
  /**
   * Starts the XMPP server.
   *
   * @param args arguments passed to the server through the command line
   */
  def main(args: Array[String]): Unit = {
    val system: ActorSystem = ActorSystem("yojik")
    system.actorOf(Props[TcpServer], "tcp-server")

    val deadLetterActor: ActorRef = system.actorOf(Props[DeadLetterActor], "dead-letter-actor")
    system.eventStream.subscribe(deadLetterActor, classOf[DeadLetter])
  }
}
