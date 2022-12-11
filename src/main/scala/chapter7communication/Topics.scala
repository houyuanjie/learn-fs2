package chapter7communication

import cats.effect.*
import fs2.*
import fs2.concurrent.*

import scala.concurrent.duration.*
import scala.util.Random

// 多生产者, 多消费者模型
object Topics extends IOApp.Simple {
  override val run: IO[Unit] = {
    Stream
      .eval(Topic[IO, Int])
      .flatMap { topic =>
        val producer = Stream.iterate(1) { _ + 1 }.covary[IO].through(topic.publish).drain

        val consumer1 =
          topic
            .subscribe(10)
            .evalMap { i =>
              IO.println(s"[consumer1] 读取到了 $i ")
            }
            .drain

        val consumer2 =
          topic
            .subscribe(10)
            .evalMap { i =>
              IO.println(s"[consumer2] 读取到了 $i ")
            }
            .metered(200.millis)
            .drain

        // 同一个数据会保证能被所有消费者读取到
        // 换句话说, 任何一个慢消费者都会阻塞生产者
        // 专业术语称之为"背压"

        Stream(producer, consumer1, consumer2).parJoinUnbounded
      }
      .interruptAfter(3.seconds)
      .compile
      .drain

    case class CarPosition(carId: Long, lat: Double, lng: Double)

    def createCar(carId: Long, topic: Topic[IO, CarPosition]): Stream[IO, Nothing] = {
      Stream
        .repeatEval(IO(CarPosition(carId, Random.between(-90.0, 90.0), Random.between(-180.0, 180.0))))
        .metered(1.second)
        .through(topic.publish)
        .drain
    }

    def createGoogleMapUpdater(topic: Topic[IO, CarPosition]): Stream[IO, Nothing] = {
      topic
        .subscribe(10)
        .evalMap { pos =>
          IO.println(f"[谷歌地图] 车辆 [carId=${pos.carId}]: 正在绘制位置 (${pos.lat}%.2f, ${pos.lng}%.2f)")
        }
        .drain
    }

    // Exercise
    def createDriverNotifier(
        topic: Topic[IO, CarPosition],
        shouldNotify: CarPosition => Boolean,
        notify: CarPosition => IO[Unit]
    ): Stream[IO, Nothing] = {
      topic
        .subscribe(10)
        .filter(shouldNotify)
        .evalMap(notify)
        .drain
    }

    Stream
      .eval(Topic[IO, CarPosition])
      .flatMap { topic =>
        val cars = Stream.range(1, 10).map { carId => createCar(carId, topic) }
        val googleMapUpdater = createGoogleMapUpdater(topic)
        val driverNotifier = createDriverNotifier(
          topic = topic,
          shouldNotify = _.lat > 0.0,
          notify = pos => IO.println(f"[Notifier] ${pos.carId}%d 号车 注意您当前的位置! (${pos.lat}%.2f, ${pos.lng}%.2f)")
        )

        (cars ++ Stream(googleMapUpdater, driverNotifier)).parJoinUnbounded
      }
      .interruptAfter(3.seconds)
      .compile
      .drain
  }
}
