package chapter6concurrency

import cats.effect.*
import fs2.*
import syntex.stream.*

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.concurrent.duration.*
import scala.util.Random

object TimedRevisited extends IOApp.Simple {
  override def run: IO[Unit] = {
    val format = DateTimeFormatter.ofPattern("hh:mm:ss")
    val printNow: IO[Unit] = IO(println(LocalDateTime.now().format(format)))
    val randomResult: IO[Int] = IO(Random.between(1, 1000))

    def process(executionTime: FiniteDuration): IO[Int] = {
      IO.sleep(executionTime) *> randomResult.flatTap { _ => printNow }
    }

    val s = Stream.repeatEval(process(1.second))
    s.take(3).compile.toList.flatMap(IO.println)

    val fixedRateStream = Stream.fixedRate[IO](2.seconds)
    fixedRateStream.take(3).log()

    (fixedRateStream zipRight s).take(3).compile.toList.flatMap(IO.println)

    // Exercise
    def metered[A](s: Stream[IO, A], d: FiniteDuration): Stream[IO, A] = {
      Stream.fixedRate[IO](d) zipRight s
    }

    metered(s, 2.seconds).take(3).compile.toList.flatMap(IO.println)

    val fixedDelayStream = Stream.fixedDelay[IO](2.seconds)
    fixedDelayStream.take(3).log()

    // Exercise
    def spaced[A](s: Stream[IO, A], d: FiniteDuration): Stream[IO, A] = {
      Stream.fixedDelay[IO](d) zipRight s
    }

    spaced(s, 2.seconds).take(3).log()
  }
}
