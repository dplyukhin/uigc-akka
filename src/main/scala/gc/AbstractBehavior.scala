package gc

import akka.actor.typed.{Behavior => AkkaBehavior}
import akka.actor.typed.scaladsl.{AbstractBehavior => AkkaAbstractBehavior, Behaviors => AkkaBehaviors}


/**
 * Parent class for behaviors that implement the GC message protocol.
 *
 * Unlike [[AkkaAbstractBehavior]], child classes of [[AbstractBehavior]] must implement
 * [[processMessage]].
 */
abstract class AbstractBehavior[T <: Message](context: ActorContext[T])
  extends AkkaAbstractBehavior[GCMessage[T]](context.context) {

  def onMessage(msg : T) : Behavior[T]

  final def onMessage(msg : GCMessage[T]) : AkkaBehavior[GCMessage[T]] =
    msg match {
      case ReleaseMsg(from, releasing, created, sequenceNum) =>
        val readyToTerminate: Boolean = context.handleRelease(releasing, created)
        from ! AckReleaseMsg(releasing, created, sequenceNum)
        if (readyToTerminate) {
          AkkaBehaviors.stopped
        }
        else {
          AkkaBehaviors.same
        }
      case AckReleaseMsg(releasing, created, sequenceNum) =>
        context.finishRelease(releasing, created, sequenceNum)
        AkkaBehaviors.same
      case AppMsg(payload) =>
        context.addRefs(payload.refs)
        onMessage(payload)
    }
}
