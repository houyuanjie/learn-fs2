package chapter7communication

import cats.effect.*
import fs2.*
import fs2.concurrent.*
import syntex.stream.*

import scala.concurrent.duration.*
import scala.util.Random

object Signals extends IOApp.Simple {
  override def run: IO[Unit] = {

    def signaller(signal: SignallingRef[IO, Boolean]): Stream[IO, Nothing] = {
      Stream
        .repeatEval(IO(Random.between(1, 1000)).flatTap { i => IO.println(s"[signaller] 生成了 $i") })
        .metered(100.millis)
        .evalMap { i =>
          if i % 5 == 0 then IO.println("[signaller] 设置停止信号!") *> signal.set(true)
          else IO.unit
        }
        .drain
    }

    def worker(signal: SignallingRef[IO, Boolean]): Stream[IO, Nothing] = {
      Stream
        .repeatEval(IO.println("[worker] 工作中"))
        .metered(15.millis)
        .interruptWhen(signal)
        .drain
    }

    Stream
      .eval(SignallingRef.of[IO, Boolean](false))
      .flatMap { signal =>
        val workerS = worker(signal)
        val signallerS = signaller(signal)
        workerS concurrently signallerS
      }
      .compile
      .drain

    type Temperature = Double

    def createTemperatureSensor(alarm: SignallingRef[IO, Temperature], threshold: Temperature): Stream[IO, Nothing] = {
      Stream
        .repeatEval(IO(Random.between(-40.0, 40.0)))
        .evalTap { t => IO.println(f"当前温度: $t%.1f") }
        .evalMap { t => if t > threshold then alarm.set(t) else IO.unit }
        .metered(300.millis)
        .drain
    }

    def createCooler(alarm: SignallingRef[IO, Temperature]): Stream[IO, Nothing] = {
      alarm.discrete.evalMap { t => IO.println(f"$t%.1f 摄氏度太高了! 正在降温...") }.drain
    }

    val threshold = 20.0
    val initialTemperature = 20.0

    Stream
      .eval(SignallingRef.of[IO, Temperature](initialTemperature))
      .flatMap { signal =>
        val temperatureSensor = createTemperatureSensor(signal, threshold)
        val cooler = createCooler(signal)
        temperatureSensor.concurrently(cooler)
      }
      .interruptAfter(3.seconds)
      .compile
      .drain
  }
}
