package chapter4effectfulstreams

import cats.effect.*
import fs2.*

import scala.concurrent.duration.*

object Timed extends IOApp.Simple {
  override def run: IO[Unit] = {
    val drinkWater = Stream.iterateEval(1) { n =>
      (IO.sleep(500.millis) *> IO.println("Drink more water")).as(n + 1)
    }

    drinkWater.compile.drain
    drinkWater.timeout(1.second).compile.drain
    drinkWater.interruptAfter(1.second).compile.drain
    drinkWater.delayBy(2.seconds).interruptAfter(4.seconds).compile.drain

    // Throttling
    drinkWater
      // .metered(1.second)
      .meteredStartImmediately(1.second)
      .interruptAfter(5.seconds)
      .compile
      .drain

    // Debounce
    val resizeEvents = Stream.iterate((0, 0)) { case (w, h) => (w + 1, h + 1) }.covary[IO]
    resizeEvents
      .debounce(200.millis)
      .evalTap { case (h, w) => IO.println(s"Resizing window to [$h x $w]") }
      .interruptAfter(3.seconds)
      .compile
      .toList
      .flatMap(IO.println)
  }
}
