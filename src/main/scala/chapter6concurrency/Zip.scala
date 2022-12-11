package chapter6concurrency

import cats.effect.*
import fs2.*
import syntex.stream.*

import java.time.LocalDateTime
import scala.concurrent.duration.*

object Zip extends IOApp.Simple {
  override def run: IO[Unit] = {
    val s1 = Stream(1, 2, 3).covary[IO].metered(1.second)
    val s2 = Stream(4, 5, 6, 7).covary[IO].metered(100.millis)
    (s1 zip s2).log()

    val s2Inf = Stream.iterate(0) { _ + 1 }.covary[IO].metered(200.millis)
    (s1 zip s2Inf).log()

    val s1Failing = s1 ++ Stream.raiseError[IO](new Exception("s1 failed"))
    (s1Failing zip s2).log()

    val s2Failing = s2 ++ Stream.raiseError[IO](new Exception("s2 failed"))
    (s1 zip s2Failing).log()
    (s1 zip s2).compile.toList.flatMap(IO.println)

    val s3 = Stream.repeatEval(IO(LocalDateTime.now())).evalTap(IO.println).metered(1.second)
    (s3 zipRight s2Inf) // 运行左流的副作用, 但不需要它的结果
      .interruptAfter(3.seconds)
      .compile
      .toList
      .flatMap(IO.println)

    val s4 = Stream.iterateEval(0) { i => IO.println(s"Pulling left $i").as(i + 1) }
    val s5 = Stream.iterateEval(0) { i => IO.println(s"Pulling right $i").as(i + 1) }
    (s4 zip s5).take(15).compile.toList.flatMap(IO.println)
    (s4 parZip s5).take(15).compile.toList.flatMap(IO.println)
  }
}
