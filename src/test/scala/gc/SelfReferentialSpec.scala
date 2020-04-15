package gc

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.{PostStop, Signal, Behavior => AkkaBehavior}
import org.scalatest.wordspec.AnyWordSpecLike



sealed trait SelfRefMsg extends Message

final case class Countdown(n: Int) extends SelfRefMsg with NoRefsMessage
case object SelfRefTestInit extends SelfRefMsg with NoRefsMessage
case object SelfRefTerminated extends SelfRefMsg with NoRefsMessage

class SelfReferentialSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  val probe: TestProbe[SelfRefMsg] = testKit.createTestProbe[SelfRefMsg]()

  "Isolated actors" must {
    val actorA = testKit.spawn(ActorA(), "actorA")
    "not self-terminate when self-messages are in transit" in {
      actorA ! SelfRefTestInit
      probe.expectMessage( SelfRefTerminated)
    }
  }


  object ActorA {
    def apply(): AkkaBehavior[SelfRefMsg] = Behaviors.setupReceptionist(context => new ActorA(context))
  }
  class ActorA(context: ActorContext[SelfRefMsg]) extends AbstractBehavior[SelfRefMsg](context) {
    val actorB: ActorRef[SelfRefMsg] = context.spawn(ActorB(), "actorB")

    override def onMessage(msg: SelfRefMsg): Behavior[SelfRefMsg] = {
      msg match {
        case SelfRefTestInit =>
          actorB ! Countdown(100000)
          context.release(actorB)
          this
        case _ =>
          this
      }
    }
  }

  object ActorB {
    def apply() : ActorFactory[SelfRefMsg] = {
      Behaviors.setup(context => new ActorB(context))
    }
  }
  class ActorB(context: ActorContext[SelfRefMsg]) extends AbstractBehavior[SelfRefMsg](context) {
    override def onMessage(msg: SelfRefMsg): Behavior[SelfRefMsg] = {
      msg match {
        case Countdown(n) =>
          println(n)
          if (n > 0) {
            context.self ! Countdown(n - 1)
          }
          this
        case _ =>
          this
      }
    }
    override def onSignal: PartialFunction[Signal, AkkaBehavior[GCMessage[SelfRefMsg]]] = {
      case PostStop =>
        probe.ref ! SelfRefTerminated
        this
    }
  }
}
