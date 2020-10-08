package gc.properties

import gc.QuiescenceDetector
import gc.properties.model.{BecomeIdle, Configuration, DummyName, DummyRef, DummySnapshot, DummyToken, Snapshot}
import org.scalacheck.Prop.{collect, forAll, propBoolean}
import org.scalacheck.util.ConsoleReporter
import org.scalacheck.{Properties, Test}

object QuiescenceDetectionSpec extends Properties("Quiescence detection") {
  import gc.properties.model.Generators._

  val executionSize = 1000

  override def overrideParameters(p: Test.Parameters): Test.Parameters =
    p.withMinSuccessfulTests(100)
      // This prevents Scalacheck console output from getting wrapped at 75 chars
      .withTestCallback(ConsoleReporter(1, Int.MaxValue))
      // This prevents Scalacheck from giving up when it has to discard a lot of tests
      .withMaxDiscardRatio(100000)

  val detective: QuiescenceDetector[DummyName, DummyToken, DummyRef, DummySnapshot] =
    new QuiescenceDetector()

  property(" Quiescence detector has no false positives") =
    forAll(genConfiguration(executionSize, minAmountOfGarbage = 1)) { config => {
      val detectedGarbage = detective.findTerminated(config.snapshots.toMap)
      val approxDetected = (detectedGarbage.size / 5.0).round * 5
      collect(s"~$approxDetected garbage actors detected") {
        detectedGarbage subsetOf config.garbageActors.toSet
      }
    }}

  property(" If all non-terminated actors take snapshots and terminated snapshots are ignored, then all garbage is detected") =
    forAll(genExecutionAndConfiguration(executionSize, minAmountOfGarbage = 1)) {
      case (execution, config) =>
        // Tell all busy actors to become idle, then tell all non-terminated actors to take a snapshot
        val snapshotters = (config.busyActors ++ config.idleActors).toSet -- config.terminatedActors
        val execution2 = execution ++ config.busyActors.map(BecomeIdle) ++ snapshotters.map(Snapshot)
        val config2 = Configuration.fromExecution(execution2)

        // Use the latest snapshots from every actor, excluding any stale snapshots from terminated actors
        val detectedGarbage = detective.findTerminated(config2.snapshots.toMap -- config2.terminatedActors)

        // Notice that this set is different from `config.garbageActors.toSet` because some busy actors became idle,
        // possibly therefore becoming garbage.
        val garbage = config2.garbageActors.toSet

        s"Detected garbage: $detectedGarbage  must equal actual garbage: $garbage" |:
          detectedGarbage == garbage
    }
}
