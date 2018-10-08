import akka.actor.{ ActorNotFound, ActorPath, ActorSystem }
import akka.testkit.{ ImplicitSender, TestKit }
import org.scalamock.scalatest.MockFactory
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpecLike }
import org.scalatest.concurrent.ScalaFutures

import xyz.thomaslee.yojik.resource.JidActor

class JidActorSpec extends TestKit(ActorSystem("JidActorSpec"))
    with MockFactory
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "JidActor" must {
    "return valid actor name on getActorName()" in {
      val words = List("ёжик", "yojik", "test", "example", "123")
      for (user <- words)
        for (domain <- words)
          assert(ActorPath.isValidPathElement(JidActor.getActorName(user, domain)))
    }

    "return Failure when findActor() searches for a nonexistent actor" in {
      val findActor = JidActor.findActor(system, "user", "domain")
      ScalaFutures.whenReady(findActor.failed) { e =>
        assert(e.isInstanceOf[ActorNotFound])
      }
    }
  }
}
