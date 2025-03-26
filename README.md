> [!IMPORTANT]
> UIGC has moved from Akka to Apache Pekko. See the latest developments [here](https://github.com/dplyukhin/uigc-pekko)!

UIGC is a middleware for Akka that provides _actor garbage collection (actor GC)_. 
When you implement an Akka actor with UIGC, the middleware will 
automatically detect when the actor is safe to terminate and stop it for
you. Say goodbye to memory leaks and buggy memory management code!

> UIGC is currently a _research prototype_. We are actively looking for 
> contributors of all skill levels to help bring it out of the research lab and into the real world! 
> See [Contributing](#contributing) for details.

## Overview

UIGC consists of two parts:

1. A _unified API_ for implementing Akka actors with automatic memory management.
2. A collection of _GC engines_ that UIGC uses to detect when actors are garbage.
   UIGC currently supports two GC engines, described below.

### Conflict-Replicated Garbage Collection (CRGC)

This is the default GC engine, based on 
[Dan Plyukhin's PhD thesis](https://youtu.be/akBNZLNp05M). CRGC has many good properties:

1. _No message delivery requirements:_ CRGC can be used in Akka Cluster because
   it does not require that messages are delivered in any particular order.
2. _Fault-tolerance:_ CRGC continues to collect garbage, even if messages are
   dropped or systems are unexpectedly downed.
3. _Fault-recovery:_ If a system is downed and it had references to actors,
  those actors can still be garbage collected.

That last point means you don't have to worry about garbage collection,
even when things go wrong!

### Message-Based Actor Collection (MAC)

An implementation of Pony's [MAC algorithm](https://doi.org/10.1145/2509136.2509557).
To collect acyclic garbage, MAC uses [weighted reference counting (WRC)](https://en.wikipedia.org/wiki/Reference_counting#Weighted_reference_counting).
To collect cyclic garbage (e.g. when two garbage actors have references to each other) actors send messages to a centralized *cycle detector*.
Currently, the UIGC implementation of MAC only collects acyclic garbage.

This engine requires causal message delivery, which Akka only supports [within a single JVM](https://doc.akka.io/docs/akka/current/general/message-delivery-reliability.html#ordering-of-local-message-sends).
Consequently, MAC cannot be used with Akka Cluster.

## Installation

UIGC requires Java 17 and a [forked version of Akka](https://github.com/dplyukhin/akka) that
exposes some of Akka's internals to the garbage collector. Eventually, we plan for 
UIGC to build against the official Akka binaries. See
the Github issue that tracks our progress towards this goal.

1. Clone [this fork of Akka](https://github.com/dplyukhin/akka).
2. In the Akka build directory, run `sbt publishLocal`. When the command is finished, 
   search the output for a string like `2.8.0-M3+18-6fadd9a8+20230727-1556-SNAPSHOT`.
   Copy the string. Let's call it `YOUR_AKKA_VERSION`.
3. In the CRGC build directory, open `build.sbt` and set `akkaVersion` to `YOUR_AKKA_VERSION`. 
   It should now read: `val akkaVersion = <YOUR_AKKA_VERSION>`.
4. In the CRGC build directory, run `sbt publishLocal`.

Or you can run the scripts/build-linux-mac.sh script on Linux, macOS, or in the Bash environment on Windows.
If the above instructions don't work for you, or if you can think of an easier install process,
please raise a Github issue!
 
## Usage 


For a guide to using UIGC, see [GUIDE.md](./GUIDE.md).

## Contributing

Working on UIGC is a great way to get involved in research and open source software!
Start by following the [Installation](#installation) guide. Then check out
the [issues page](https://github.com/dplyukhin/UIGC/issues?q=is%3Aopen+is%3Aissue+label%3A%22good+first+issue%22) 
to find a starter project.
Once you find a project, leave a comment to let us know you're working on it.

## License

Please note that this software is released under the _Hippocratic License, v3.0_; see [LICENSE.md](./LICENSE.md) for 
details. If this license is too restrictive for your organization, feel free to discuss by
[raising an issue](https://github.com/dplyukhin/uigc/issues) or [starting a discussion](https://github.com/dplyukhin/uigc/discussions)
on Github.
Please remember to be polite and thoughtful!
