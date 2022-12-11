package chapter6concurrency

import cats.effect.*
import cats.effect.syntax.*
import fs2.*

import scala.concurrent.duration.*

object Concurrently extends IOApp.Simple {
  override def run: IO[Unit] = {
    val s1 = Stream(1, 2, 3).covary[IO].printlns
    val s2 = Stream(4, 5, 6).covary[IO].printlns
    (s1 concurrently s2).compile.drain

    val s1Inf = Stream.iterate(0) { _ + 1 }.covary[IO].printlns
    (s1Inf concurrently s2).interruptAfter(3.seconds).compile.drain

    val s2Inf = Stream.iterate(2000) { _ + 1 }.covary[IO].printlns
    (s1 concurrently s2Inf).compile.drain // 右流的结束取决于左流

    val s1Failing = Stream.repeatEval(IO(42)).take(500).printlns ++ Stream.raiseError[IO](new Exception("s1 failed"))
    val s2Failing = Stream.repeatEval(IO(6000)).take(500).printlns ++ Stream.raiseError[IO](new Exception("s2 failed"))
    (s1Inf concurrently s2Failing).compile.drain
    (s1Failing concurrently s2Inf).compile.drain

    val s3 = Stream.iterate(3000) { _ + 1 }.covary[IO]
    val s4 = Stream.iterate(4000) { _ + 1 }.covary[IO]
    (s3 concurrently s4).take(100).compile.toList.flatMap(IO.println) // 只会收集 s3 中的元素
    (s3 merge s4).take(100).compile.toList.flatMap(IO.println) // 同时收集 s3 和 s4 中的元素
    Stream(s3, s4).parJoinUnbounded.take(100).compile.toList.flatMap(IO.println) // 同时收集 s3 和 s4 中的元素

    // Exercise
    val numItems = 30

    def processor(itemsProcessed: Ref[IO, Int]): Stream[IO, Nothing] = {
      Stream
        .repeatEval(itemsProcessed.update { _ + 1 })
        .take(numItems) // 有限流
        .metered(100.millis)
        .drain
    }

    def progressTracker(itemsProcessed: Ref[IO, Int]): Stream[IO, Nothing] = {
      Stream
        .repeatEval(itemsProcessed.get.flatMap { n =>
          val total = 20
          val length = n * total / numItems
          IO.println(s"Progress: [${"#" * length}${" " * (total - length)}]")
        })
        .metered(100.millis)
        .drain
    }

    Stream
      .eval(IO.ref(0))
      .flatMap { itemsProcessed =>
        val processorS = processor(itemsProcessed)
        val progressTrackerS = progressTracker(itemsProcessed)
        (processorS concurrently progressTrackerS) // 不要交换, 要让无限流的停止取决于有限流
      }
      .compile
      .drain
  }
}
