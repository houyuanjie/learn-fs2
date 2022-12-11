package chapter6concurrency

import cats.effect.*
import cats.effect.std.Queue
import fs2.*
import syntex.stream.*

import scala.concurrent.duration.*

object Join extends IOApp.Simple {
  override val run: IO[Unit] = {
    val s1Finite = Stream(1, 2, 3).covary[IO].metered(100.millis)
    val s2Finite = Stream(4, 5, 6).covary[IO].metered(50.millis)
    val jFinite = Stream(s1Finite, s2Finite).parJoinUnbounded // same as (s1 merge s2)
    jFinite.log()

    val s3Infinite = Stream.iterate(300_0000) { _ + 1 }.covary[IO].metered(50.millis)
    val s4Infinite = Stream.iterate(400_0000) { _ + 1 }.covary[IO].metered(50.millis)
    val jAll = Stream(s1Finite, s2Finite, s3Infinite, s4Infinite).parJoinUnbounded
    jAll.interruptAfter(5.seconds).log()

    val s1Failing = s1Finite ++ Stream.raiseError[IO](new Exception("s1 failed"))
    val jFailingS1 = Stream(s1Failing, s2Finite, s3Infinite, s4Infinite).parJoinUnbounded
    jFailingS1.interruptAfter(5.seconds).log()

    val jBounded = Stream(s1Finite, s2Finite, s3Infinite).parJoin(2)
    jBounded.interruptAfter(5.seconds).log()

    val s1Infinite = Stream.iterate(100_0000) { _ + 1 }.covary[IO].metered(50.millis)
    val jBounded2 = Stream(s1Infinite, s3Infinite, s4Infinite).parJoin(2)
    jBounded2.log()

    // Exercise
    def producer(id: Int, queue: Queue[IO, Int]): Stream[IO, Nothing] = {
      Stream.repeatEval(queue.offer(id)).drain
    }

    def consumer(id: Int, queue: Queue[IO, Int]): Stream[IO, Nothing] = {
      Stream.repeatEval(queue.take).foreach { i =>
        IO.println(s"Consuming message from producer [id=$i] with consumer [id=$id]")
      }
    }

    val stream =
      Stream
        .eval(Queue.unbounded[IO, Int])
        .flatMap { queue =>
          val producers = Stream.range(0, 5).map { id => producer(id, queue) }
          val consumers = Stream.range(0, 10).map { id => consumer(id, queue) }
          (producers ++ consumers).parJoinUnbounded
        }
        .interruptAfter(5.seconds)

    stream.log()
  }
}
