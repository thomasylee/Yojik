import org.scalatest.{ FlatSpec, Matchers }

import xyz.thomaslee.yojik.config.ConfigMap

class ConfigMapSpec extends FlatSpec with Matchers {
  it should "load configurations correctly" in {
    assert(ConfigMap.randomCharsInActorNames == 10)
    assert(ConfigMap.keyStore == "keystore.jks")
    assert(ConfigMap.serverPort == 5222)
  }
}
