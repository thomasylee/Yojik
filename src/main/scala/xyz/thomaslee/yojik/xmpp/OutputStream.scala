package xyz.thomaslee.yojik.xmpp

import akka.actor.Actor

trait OutputStream extends Actor {
  def write(content: String)
}
