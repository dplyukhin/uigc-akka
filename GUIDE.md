# Guide

Once you've installed UIGC, you can use it in a separate SBT project as follows.

## Project dependencies

To use UIGC in your Akka Cluster project, add the following dependencies to `build.sbt`:

```scala 
libraryDependencies ++= Seq(
    "edu.illinois.osl" %% "uigc" % "0.1.0-SNAPSHOT",
    "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster-typed" % akkaVersion,
    "ch.qos.logback" % "logback-classic" % "1.2.3",
)
```

where `akkaVersion` is your version of the [forked Akka project](https://github.com/dplyukhin/akka).

## Configuration

UIGC requires the following settings in your `application.conf`:
``` 
akka {
  actor {
    provider = cluster
    allow-java-serialization = on
    warn-about-java-serializer-usage = off
  }
  remote {
    artery {
      advanced {
        outbound-lanes = 1
        inbound-lanes = 1
      }
    }
  }
}
```

To select a GC engine, set `uigc.engine` to "crgc", "mac", or "manual";
the default is "crgc".

If you are using the CRGC engine, you must also configure the value of
`uigc.crgc.num-nodes`. Set this property to the total number of ActorSystems
in the cluster. Note that garbage collection will not begin until all the
nodes have joined the cluster.

## Overview

> Currently, the UIGC API is complex and leaves a lot of room
> for error. Things like `Message` traits, root vs managed actors,
> manual reference creation/deletion, will all be obsolete in
> future releases.

To use UIGC, you need to understand the following concepts:
1. How to implement actors;
2. How to implement the `uigc.interfaces.Message` trait;
3. How to spawn actors;
4. How to create and deactivate references.

The following sections explain.

### Using the UIGC API

UIGC provides an API that mirrors the `akka.actor.typed` API.
You can implement UIGC actors by extending the `uigc.AbstractBehavior` class:

```scala
import edu.illinois.osl.uigc

/* Messages handled by MyActor. */
trait Msg extends uigc.interfaces.Message
...

/* An example UIGC actor. */
class MyActor(context: uigc.ActorContext[Msg]) 
  extends uigc.AbstractBehavior[Msg](context) {

  // Handle messages:
  override def onMessage(msg: Msg): uigc.Behavior[Msg] =
    msg match {
      ...
    }

  // Handle signals:
  override def onSignal: PartialFunction[Signal, uigc.Behavior[Msg]] = {
    case PostStop =>
      println("Stopping!")
      this
  }
}
```

Notice that:
1. Instead of inheriting from `akka.actor.typed.AbstractBehavior`, you need to
   use `osl.illinois.edu.uigc.AbstractBehavior`. The `uigc` package contains 
   its own versions of most Akka classes, including `ActorContext`, `ActorRef`, and `Behavior`.
2. Messages handled by `MyActor` need to implement the trait `uigc.interfaces.Message`.
   This trait requires a method `refs: Iterable[ActorRef[Nothing]]` which tells
   UIGC about any actor references in the message.

> Eventually, UIGC will automatically detect actor references in messages.
> For now, you need to tell UIGC about these references manually by implementing the `Message` trait.

### Implementing messages

Here's an example of how to properly implement `uigc.interfaces.Message`:

```scala
trait Msg extends uigc.interfaces.Message
case class FooMsg(x: Array[Int], sender: uigc.ActorRef[Msg]) extends Msg {
  override def refs: Iterable[uigc.ActorRef[Nothing]] = List(sender)
}
case class BarMsg(friends: uigc.ActorRef[T]) extends Msg {
  override def refs: Iterable[uigc.ActorRef[Nothing]] = friends
}
case class BazMsg(y: String) extends Msg with uigc.interfaces.NoRefs
```

The last message, `BazMsg`, doesn't contain any actor references.
As a shortcut instead of implementing `def refs = Nil`, you can simply mix in
the trait `uigc.interfaces.NoRefs`.

### Creating an actor system

We say that an actor is *managed* if it was created using the
UIGC APIs. UIGC has its own versions of the `akka.actor.typed` APIs for 
creating a managed actor system:

```scala
// Import from UIGC, *not* akka.actor.typed!
import edu.illinois.osl.uigc.{ActorSystem, Behaviors, ActorContext, AbstractBehavior, ActorFactory}

trait GuardianMsg extends uigc.interfaces.Message

class MyGuardianActor(ctx: ActorContext[GuardianMsg]) 
  extends AbstractBehavior[GuardianMsg](ctx) {
    ...
}

object MyGuardianActor {
  def apply(): ActorFactory[GuardianMsg] =
    Behaviors.setup { context =>
      new MyGuardianActor(context)
    }
}

val system: ActorSystem[GuardianMsg] = ActorSystem(MyGuardianActor())
```

**Note:** In Akka Typed, `Behaviors.setup` returns a `Behavior[T]`.
In UIGC, `uigc.Behaviors.setup` returns a `uigc.ActorFactory[T]`.
You can use `uigc.ActorFactory` the same way you would use `typed.Behavior`---e.g.
you can pass it as an argument to `ctx.spawn` or `ActorSystem`.

### Spawning managed actors

To spawn a managed actor, use `uigc.Behaviors.setup`:

```scala
object MyActor {
  /** A recipe for spawning MyActor as a managed actor. */
  def apply(): uigc.Behavior[Msg] =
    Behaviors.setup { context =>
      new MyActor(context)
    }
}

// Instances of MyActor can be spawned by MyGuardianActor:
class MyGuardianActor(ctx: ActorContext[GuardianMsg])
  extends AbstractBehavior[GuardianMsg](ctx) {
  
  val child1 = ctx.spawn(MyActor())
  val child2 = ctx.spawn(MyActor())
}
```

### Creating and deactivating references

Garbage collecting actors is all about tracking references.
In the current version of UIGC, you'll need to create and delete references
manually.

Suppose our guardian actor wants `child1` to send messages to `child2`.
The guardian actor needs to create a reference to `child2` for `child1`
to use:
```scala
val child1 = ctx.spawn(MyActor())
val child2 = ctx.spawn(MyActor())
val child2_ref_for_child1 = ctx.createRef(child2, child1)
child1 ! FriendMsg(child2_ref_for_child1)
```

Now suppose our guardian actor no longer needs references to `child1` and
`child2`. Then the guardian needs to manually deactivate those references:
```scala
ctx.release(child1)
ctx.release(child2)
```

### Garbage actors

Different GC engines collect different kinds of garbage.
The MAC engine implements *weighted reference counting*, which detects when an actor has no incoming
references---i.e. every incoming reference to the actor has had
`ctx.release` invoked on it. The CRGC engine detects a more general
kind of garbage, called _quiescent_ garbage.

### Coexistence

Sometimes the `uigc` API isn't enough, and you need access to the underlying
`akka.typed` API. For example, the `Cluster` extension requires an `akka.typed.ActorSystem`,
not a `uigc.ActorSystem`:
```scala
val cluster = Cluster(context.system)
                   // ^^^^^^^^^^^^^^ ERROR: Expected akka.typed.ActorSystem, got uigc.ActorSystem
```

To solve this problem, each class has a `.typedXXX` method that returns the underlying
`akka.typed` object. For example, the code above can be fixed by writing:
```scala
val cluster = Cluster(context.system.typedSystem)
```
Or equivalently:
```scala
val cluster = Cluster(context.typedContext.system)
```

*WARNING:* Sometimes you need to turn a `uigc.ActorRef` into a `typed.ActorRef`. You
can accomplish this with the method `uigc.ActorRef.typedActorRef`. However, UIGC won't
be able to keep track of the resulting `typed.ActorRef`. This means that UIGC could
garbage collect the target actor before you have a chance to send it a message.

To convert a `typed.ActorRef` *back* into a `uigc.ActorRef`, use the `.toManaged`
implicit method provided by `edu.illinois.osl.uigc.implicits`.


### Complete example

TODO
