package chapter4effectfulstreams

import cats.effect.*
import fs2.*

import scala.concurrent.duration.*
import scala.util.Random

object Retry extends IOApp.Simple {
  override val run: IO[Unit] = {
    def doEffectFailing[A](io: IO[A]): IO[A] = {
      IO(math.random()).flatMap { flag =>
        if flag < 0.5 then IO.println("Failing...") *> IO.raiseError(new Exception("Boom"))
        else IO.println("Successful!") *> io
      }
    }

    Stream.eval(doEffectFailing(IO(42))).printlns.compile.drain

    Stream
      .retry(
        fo = doEffectFailing(IO(42)),
        delay = 1.second,
        nextDelay = { _ * 2 },
        maxAttempts = 5
      )
      .printlns
      .compile
      .drain

    // Exercise
    val searches = Stream.iterateEval("") { s =>
      IO(Random.nextPrintableChar())
        .map { s + _ }
    }

    def performSearch(text: String): IO[Unit] = doEffectFailing(IO.println(s"Performing search for text: $text"))

    val processing =
      searches
        .metered(200.millis) // 模拟用户输入
        .debounce(500.millis) // 采样
        .interruptAfter(5.seconds) // 最长运行时间
        .evalMap(performSearch)
        .compile
        .drain

    Stream
      .retry(
        fo = processing,
        delay = 1.second,
        nextDelay = { d => d },
        maxAttempts = 5
      )
      .printlns
      .compile
      .drain
  }
}
