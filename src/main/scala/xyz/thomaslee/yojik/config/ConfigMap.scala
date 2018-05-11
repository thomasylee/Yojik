package xyz.thomaslee.yojik.config

import com.typesafe.config.ConfigFactory

import xyz.thomaslee.yojik.storage.StorageAdapter

object ConfigMap {
  private val config = ConfigFactory.load("yojik")

  val randomCharsInActorNames = config.getInt("yojik.random-chars-in-actor-names")
  val keyStore = config.getString("yojik.server.key-store")
  val serverPort = config.getInt("yojik.server.port")

  val storageAdapter: StorageAdapter = {
    val klass = Class.forName(config.getString("yojik.storage.class"))
    val constructor = klass.getConstructor(Class.forName("com.typesafe.config.Config"))
    constructor.newInstance(config) match {
      case adapter: StorageAdapter => adapter
    }
  }
}
