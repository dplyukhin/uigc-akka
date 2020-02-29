package example
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.WordSpecLike
import gc._

object SimpleActor {
  case class StringMsg(str: String, sharedRefs: Seq[ActorRef[StringMsg]]) extends Message {
    /**
     * This method must return all the references contained in the message.
     */
    override def refs: Seq[ActorRef[Nothing]] = sharedRefs
  }

  def apply(): ActorFactory[SimpleActor.StringMsg] = {
    Behaviors.setup(context => new SimpleActor(context))
  }
}

class SimpleActor(context: ActorContext[SimpleActor.StringMsg]) extends AbstractBehavior[SimpleActor.StringMsg](context) {
  import SimpleActor._
  private var childB = None: Option[ActorRef[StringMsg]]
  private var childC = None: Option[ActorRef[StringMsg]]

  override def onMessage(msg: StringMsg): Behavior[StringMsg] = {
    msg.str match {
      case "init" => // spawn B and C
        childB = Some(context.spawn(SimpleActor(), "B"))
        childC = Some(context.spawn(SimpleActor(), "C"))
        this
      case "share" => // A shares C with B
        val refToShare = context.createRef(childC.get, childB.get)
        childC.get ! StringMsg("here O_O", Seq(refToShare))
        this
      case "release" => // A release C
        context.release(Seq(childC.get))
        this
    }
  }
}

class MyActorSpec extends ScalaTestWithActorTestKit with WordSpecLike {
  import SimpleActor._
  "SimpleActor must" must {
    val probe = testKit.createTestProbe[GCMessage[String]]()
    val actorA = testKit.spawn(SimpleActor()(probe.ref, Token(probe.ref, 0)))
    "create children" in {
      actorA ! AppMsg(StringMsg("init", Seq()))
    }
  }

}
