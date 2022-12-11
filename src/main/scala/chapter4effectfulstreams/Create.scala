package chapter4effectfulstreams

import cats.effect.*
import fs2.*

object Create extends IOApp.Simple {
  override val run: IO[Unit] = {
    val s = Stream.eval(IO.println("my first effectful stream"))
    s.compile.toList.flatMap(IO.println)
    s.compile.drain

    val s2 = Stream.exec(IO.println("my second effectful stream"))
    s2.compile.drain

    val fromPure = Stream(1, 2, 3).covary[IO]
    fromPure.compile.toList.flatMap(IO.println)

    val natsEval = Stream.iterateEval(1) { i =>
      IO.println(s"Producing ${i + 1}") *> IO(i + 1)
    }
    natsEval.take(10).compile.toList.flatMap(IO.println)

    val alphabet = Stream.unfoldEval('a') { c =>
      if c > 'z' then IO.println("Finishing...") *> IO(None)
      else IO.println(s"Producing $c") *> IO(Some((c, (c + 1).toChar)))
    }
    alphabet.compile.toList.flatMap(IO.println)

    // Exercise
    val data = List.range(1, 100)
    val pageSize = 20

    def fetchPage(pageNumber: Int): IO[List[Int]] = {
      val start = pageNumber * pageSize
      val end = start + pageSize
      IO.println(s"Fetching page $pageNumber").as(data.slice(start, end))
    }

    def fetchAll(): Stream[IO, Int] = {
      Stream
        .unfoldEval(0) { pageNumber =>
          fetchPage(pageNumber).map { list =>
            if list.isEmpty then None
            else Some((Stream.emits(list), pageNumber + 1))
          }
        }
        .flatten
    }
    fetchAll().compile.toList.flatMap(IO.println)
  }
}
