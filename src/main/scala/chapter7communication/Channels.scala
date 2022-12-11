package chapter7communication

import cats.*
import cats.effect.*
import cats.implicits.*
import fs2.*
import fs2.concurrent.*

import scala.concurrent.duration.*
import scala.util.Random

object Channels extends IOApp.Simple {
  override def run: IO[Unit] = {
    Stream
      .eval(Channel.bounded[IO, Int](1))
      .flatMap { channel =>
        val producer =
          Stream
            .iterate(1) { _ + 1 }
            .covary[IO]
            .evalMap(channel.send)
            .drain

        val consumer =
          channel.stream
            .metered(200.millis)
            .evalMap { i => IO.println(s"Read $i") }
            .drain

        (consumer concurrently producer).interruptAfter(3.seconds)
      }
      .compile
      .drain

    sealed trait Measurement
    case class Temperature(value: Double) extends Measurement
    case class Humidity(value: Double) extends Measurement

    given Order[Temperature] = Order.by[Temperature, Double] { _.value }
    given Order[Humidity] = Order.by[Humidity, Double] { _.value }

    def createTemperatureSensor(alarmChannel: Channel[IO, Measurement], threshold: Temperature): Stream[IO, Nothing] = {
      Stream
        .repeatEval(IO(Temperature(Random.between(-40.0, 40.0))))
        .evalTap { t => IO.println(f"当前温度: ${t.value}%.1f") }
        .evalMap { t => if t > threshold then alarmChannel.send(t) else IO.unit }
        .metered(300.millis)
        .drain
    }

    // Exercise
    def createHumiditySensor(alarmChannel: Channel[IO, Measurement], threshold: Humidity): Stream[IO, Nothing] = {
      Stream
        .repeatEval(IO(Humidity(Random.between(0.0, 100.0))))
        .evalTap { h => IO.println(f"当前湿度: ${h.value}%.1f") }
        .evalMap { h => if h > threshold then alarmChannel.send(h) else IO.unit }
        .metered(100.millis)
        .drain
    }

    def createCooler(alarmChannel: Channel[IO, Measurement]): Stream[IO, Nothing] = {
      alarmChannel.stream.evalMap {
        case Temperature(value) =>
          IO.println(f"温度太高! ${value}%.1f 摄氏度, 冷却中...")
        case Humidity(value) =>
          IO.println(f"湿度太高! ${value}%.1f %%, 干燥中...")
      }.drain
    }

    Stream
      .eval(Channel.unbounded[IO, Measurement])
      .flatMap { channel =>
        val temperatureSensor = createTemperatureSensor(channel, Temperature(10.0))
        val humiditySensor = createHumiditySensor(channel, Humidity(50.0))
        val cooler = createCooler(channel)
        Stream(temperatureSensor, humiditySensor, cooler).parJoinUnbounded
      }
      .interruptAfter(3.seconds)
      .compile
      .drain
  }
}
