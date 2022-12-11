package chapter6concurrency

import cats.effect.*
import fs2.*

import scala.concurrent.duration.*
import scala.util.Random

object Merge extends IOApp.Simple {
  override val run: IO[Unit] = {
    val s1Inf = Stream.iterate("s1Inf> 1") { _ + "1" }.covary[IO].metered(100.millis)
    val s2Inf = Stream.iterate("s2Inf> a") { _ + "a" }.covary[IO].metered(200.millis)

    val s3Inf = s1Inf merge s2Inf
    s3Inf.interruptAfter(5.seconds).printlns.compile.drain

    val s1Failing =
      Stream("a", "b", "c").covary[IO].metered(100.millis) ++ Stream.raiseError[IO](new Exception("s1 failed"))
    val s3LeftFailing = s1Failing merge s2Inf
    s3LeftFailing.interruptAfter(5.seconds).printlns.compile.drain

    val s2Failing =
      Stream("a", "b", "c").covary[IO].metered(100.millis) ++ Stream.raiseError[IO](new Exception("s2 failed"))
    val s3RightFailing = s1Inf merge s2Failing
    s3RightFailing.interruptAfter(5.seconds).printlns.compile.drain

    val s1Finite = Stream(1, 2, 3).covary[IO].metered(100.millis)
    val s2Finite = Stream(4, 5, 6).covary[IO].metered(200.millis)
    val s3Finite = s1Finite merge s2Finite
    s3Finite.interruptAfter(5.seconds).printlns.compile.drain

    val s3Mixed = s1Finite mergeHaltBoth s2Inf
    s3Mixed.interruptAfter(5.seconds).printlns.compile.drain

    // Exercise
    val fetchRandomQuoteFromSource1: IO[String] = IO(Random.nextString(5))
    val fetchRandomQuoteFromSource2: IO[String] = IO(Random.nextString(25))

    val quotes: Stream[IO, Nothing] = {
      val s1 = Stream.repeatEval(fetchRandomQuoteFromSource1).take(100)
      val s2 = Stream.repeatEval(fetchRandomQuoteFromSource2).take(150)

      (s1 merge s2).interruptAfter(5.seconds).printlns
    }

    quotes.compile.drain
  }
}
