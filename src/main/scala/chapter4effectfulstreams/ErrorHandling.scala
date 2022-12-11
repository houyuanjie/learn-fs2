package chapter4effectfulstreams

import cats.effect.*
import fs2.*

object ErrorHandling extends IOApp.Simple {
  override val run: IO[Unit] = {
    val s = Stream.eval(IO.raiseError(new Exception("boom")))
    val s2 = Stream.raiseError[IO](new Exception("boom 2"))
    val s3 = Stream.repeatEval(IO.println("emitting 42...").as(42)).take(3) ++ Stream.raiseError[IO](
      new Exception("error after")
    )
    val s4 = Stream.raiseError[IO](new Exception("error before")) ++ Stream.eval(IO.println("the end"))
    s4.compile.toList.flatMap(IO.println)

    def doWork(i: Int): Stream[IO, Int] = {
      Stream.eval(IO(math.random())).flatMap { flag =>
        if flag < 0.8 then Stream.eval(IO.println(s"Processing $i").as(i))
        else Stream.raiseError[IO](new Exception(s"Error while handling $i"))
      }
    }

    extension [A](s: Stream[IO, A]) {
      def flatAttempt: Stream[IO, A] = {
        s.attempt
          .flatMap {
            case Right(a) => Stream.emit(a)
            case Left(_)  => Stream.empty
          }
      }
    }

    Stream
      .iterate(1) { _ + 1 }
      .flatMap(doWork)
      .take(10)
      .flatAttempt
      // .attempt
      // .handleErrorWith { e =>
      //   Stream.exec(IO.println(s"Recovering: ${e.getMessage}"))
      // }
      .compile
      .toList
      .flatMap(IO.println)
  }
}
