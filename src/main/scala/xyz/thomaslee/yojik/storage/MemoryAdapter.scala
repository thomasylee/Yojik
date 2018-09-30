package xyz.thomaslee.yojik.storage

import com.typesafe.config.{ Config, ConfigValue }
import java.util.HashMap
import scala.collection.JavaConverters.asScalaBuffer

/** Contains constants to be used by [[xyz.thomaslee.yojik.storage.MemoryAdapter]]. */
object MemoryAdapter {
  val UsersConfigKey = "yojik.storage.memory.users"
}

/**
 * MemoryAdapter stores everything in memory, with initial values loaded from
 * TypeSafe configuration files.
 */
class MemoryAdapter(config: Config) extends StorageAdapter(config) {
  val users: Map[String, String] = {
    asScalaBuffer(config.getList(MemoryAdapter.UsersConfigKey)).map { entry => {
      entry match {
        case userConfig: ConfigValue => {
          userConfig.unwrapped match {
            case user: HashMap[String, String] =>
              (user.get("username"), user.get("password"))
          }
        }
      }
    }}.toMap
  }

  /**
   * Returns true if the credentials are valid for the username, false otherwise.
   *
   * The hashAlgorithm must be a supported SASL mechanism.
   *
   * @param username the username the credentials are for
   * @param credentials the credentials to validate against the data store
   * @param hashAlgorithm the hashing algorithm used, or PLAIN for plaintext
   * @return true if the credentials are valid for the username, false otherwise
   */
  def validateCredentials(username: String, password: String, hashAlgorithm: String): Boolean = hashAlgorithm match {
    case "PLAIN" => users.get(username) match {
      case Some(pswd: String) if pswd == password => true
      case _ => false
    }
  }
}
