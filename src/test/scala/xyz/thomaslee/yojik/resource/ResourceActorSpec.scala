import akka.actor.{ ActorNotFound, ActorPath, ActorSystem }
import akka.testkit.{ ImplicitSender, TestKit }
import org.scalamock.scalatest.MockFactory
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpecLike }
import org.scalatest.concurrent.ScalaFutures

import xyz.thomaslee.yojik.resource.ResourceActor

class ResourceActorSpec extends TestKit(ActorSystem("ResourceActorSpec"))
    with MockFactory
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "ResourceActor" must {
    "return valid actor name on getActorName()" in {
      val words = List("ёжик", "yojik", "test", "example", "123")
      for (user <- words)
        for (domain <- words)
          for (resource <- words)
            assert(ActorPath.isValidPathElement(ResourceActor.getActorName(user, domain, resource)))
    }

    "return Failure when findActor() searches for a nonexistent actor" in {
      val findActor = ResourceActor.findActor(system, "user", "domain", "resource")
      ScalaFutures.whenReady(findActor.failed) { e =>
        assert(e.isInstanceOf[ActorNotFound])
      }
    }
  }
}
