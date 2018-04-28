package xyz.thomaslee.yojik.config

import com.typesafe.config.ConfigFactory

object ConfigMap {
  private val config = ConfigFactory.load("yojik")

  val randomCharsInActorNames = config.getInt("yojik.random-chars-in-actor-names")
  val keyStore = config.getString("yojik.server.key-store")
  val serverPort = config.getInt("yojik.server.port")
}
