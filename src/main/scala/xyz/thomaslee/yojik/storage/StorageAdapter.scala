package xyz.thomaslee.yojik.storage

import com.typesafe.config.Config

/**
 * An adapter for storing and retrieving data. Subclasses can use various backends
 * such as memory, MySQL, and others to make Yojik easily support multiple data
 * stores.
 *
 * @param config the configurations loaded from the TypeSafe configuration files
 */
abstract class StorageAdapter(config: Config) {
  /**
   * Returns true if the credentials are valid for the username, false otherwise.
   *
   * The hashAlgorithm should be one of the supported SASL mechanisms, such as
   * "PLAIN", "SCRAM-SHA-1", etc.
   *
   * @param username the username the credentials are for
   * @param credentials the credentials to validate against the data store
   * @param hashAlgorithm the hashing algorithm used, or PLAIN for plaintext
   * @return true if the credentials are valid for the username, false otherwise
   */
  def validateCredentials(username: String, credentials: String, hashAlgorithm: String): Boolean
}
