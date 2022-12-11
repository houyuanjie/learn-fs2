package chapter7communication

import cats.*
import cats.effect.*
import cats.effect.std.{Console, Queue}
import cats.implicits.*
import fs2.*
import fs2.concurrent.*

import java.time.LocalDateTime
import scala.concurrent.duration.*
import scala.util.Random

object Queues extends IOApp.Simple {
  override val run: IO[Unit] = {

    def createProducer(queue: Queue[IO, Int]): Stream[IO, Nothing] = {
      Stream
        .iterate(0) { _ + 1 }
        .covary[IO]
        .evalTap { e => IO.println(s"[counter] $e 入队列") }
        .evalMap(queue.offer)
        .drain
    }

    def createConsumer(queue: Queue[IO, Int], ref: Ref[IO, Int]): Stream[IO, Nothing] = {
      Stream
        .fromQueueUnterminated(queue)
        .evalTap { e => IO.println(s"[adder] 加上 $e") }
        .evalMap { e => ref.update { _ + e } }
        .metered(300.millis)
        .drain
    }

    val program1 =
      for {
        queue   <- Stream.eval(Queue.unbounded[IO, Int])
        ref     <- Stream.eval(IO.ref(0))
        producer = createProducer(queue)
        consumer = createConsumer(queue, ref)
        effects <- (producer merge consumer).interruptAfter(300.seconds) ++ Stream.eval(ref.get.flatMap(IO.println))
      } yield effects
    program1.compile.drain

    val program2 =
      for {
        queue   <- Stream.eval(Queue.unbounded[IO, Option[Int]])
        producer = (Stream.range(0, 10).map(Some.apply) ++ Stream(Some(11), None, Some(12)))
                     .evalMap(queue.offer)
        consumer = Stream
                     .fromQueueNoneTerminated(queue)
                     .evalMap(IO.println)
        effects <- producer merge consumer
      } yield effects
    program2.compile.drain

    // 练习: 模拟 Http 端口服务器

    trait Controller[F[_]] {
      def postAccount(customerId: Long, accountType: String, creationDate: LocalDateTime): F[Unit]
    }

    // 示例
    object IOPrintlnController extends Controller[IO] {
      override def postAccount(customerId: Long, accountType: String, creationDate: LocalDateTime): IO[Unit] = {
        IO.println(s"POST => 创建账户: [customerId=$customerId] [accountType=$accountType] [creationDate=$creationDate]")
      }
    }

    trait Server[F[_]] {
      def start(): F[Nothing]
    }

    object Server {
      def impl[F[_]: Async](controller: Controller[F]): Server[F] = new Server[F] {
        override def start(): F[Nothing] = {
          val program = for {
            randomWait <- Async[F].delay(math.abs(Random.nextInt()) % 500)
            _          <- Async[F].sleep(randomWait.millis)
            _          <- controller.postAccount(
                            customerId = Random.between(1L, 1000L),
                            accountType = if Random.nextBoolean() then "ira" else "brokerage",
                            creationDate = LocalDateTime.now()
                          )
          } yield ()
          program.foreverM
        }
      }
    }

    // 示例
    Server.impl[IO](IOPrintlnController).start()

    case class CreateAccountDto(customerId: Long, accountType: String, creationDate: LocalDateTime)

    case class QueueController[F[_]](private val queue: Queue[F, CreateAccountDto]) extends Controller[F] {
      override def postAccount(customerId: Long, accountType: String, creationDate: LocalDateTime): F[Unit] = {
        val dto = CreateAccountDto(customerId, accountType, creationDate)
        queue.offer(dto)
      }
    }

    // QueueController 是数据的生产者
    // 我们需要定义数据的消费者

    case class CreateAccountService[F[_]: Functor: Console](queue: Queue[F, CreateAccountDto]) {
      val stream: Stream[F, Nothing] = {
        Stream
          .fromQueueUnterminated(queue)
          .evalMap { dto => Console[F].println(s"[CreateAccountService] 正在处理 $dto") }
          .drain
      }
    }

    val app: Stream[IO, Nothing] =
      for {
        queue   <- Stream.eval(Queue.unbounded[IO, CreateAccountDto])
        service  = CreateAccountService(queue)         // 从队列取数据 处理
        server   = Server.impl(QueueController(queue)) // 接受请求 送入队列
        effects <- Stream.eval(server.start()) concurrently service.stream
      } yield effects

    app.compile.drain
  }
}
