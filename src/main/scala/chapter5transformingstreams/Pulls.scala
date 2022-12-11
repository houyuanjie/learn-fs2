package chapter5transformingstreams

import cats.effect.*
import fs2.*

object Pulls extends IOApp.Simple {
  override val run: IO[Unit] = {
    val s = Stream(1, 2) ++ Stream(3) ++ Stream(4, 5)

    val outputPull = Pull.output1(1)
    IO.println(outputPull.stream.toList)

    val outputChunk = Pull.output(Chunk(1, 2, 3))
    IO.println(outputChunk.stream.toList)

    val donePull = Pull.done
    IO.println(donePull)

    val purePull = Pull.pure(5)

    val combined = for {
      _ <- Pull.output1(1)
      _ <- Pull.output(Chunk(2, 3, 4))
    } yield ()
    IO.println(combined.stream.toList)
    IO.println(combined.stream.chunks.toList)

    val toPull = s.pull
    val echoPull = s.pull.echo
    val takePull: Pull[Pure, Int, Option[Stream[Pure, Int]]] = s.pull.take(3)
    //                                   |剩余流
    val dropPull = s.pull.drop(3)

    // Exercise
    def skipLimit[A](skip: Int, limit: Int)(s: Stream[IO, A]): Stream[IO, A] = {
      val pull = for {
        maybeTail <- s.pull.drop(skip)
        _ <- maybeTail match {
          case Some(tail) => tail.pull.take(limit)
          case None       => Pull.done
        }
      } yield ()

      pull.stream
    }

    skipLimit(10, 10)(Stream.range(1, 100)).compile.toList.flatMap(IO.println)
    skipLimit(1, 15)(Stream.range(1, 5)).compile.toList.flatMap(IO.println)

    val unconsedRange: Pull[Pure, Nothing, Option[(Chunk[Int], Stream[Pure, Int])]] = s.pull.uncons
    //                                             |第一个Chunk |
    //                                                         |剩余流

    def firstChunk[A](s: Stream[Pure, A]): Stream[Pure, A] = {
      s.pull.uncons.flatMap {
        case Some((chunk, _)) => Pull.output(chunk)
        case None             => Pull.done
      }.stream
    }

    def firstChunkPipe[A]: Pipe[Pure, A, A] = firstChunk[A]

    IO.println(firstChunk(s).compile.toList)
    IO.println(s.through(firstChunkPipe).compile.toList)

    def drop[A](n: Int): Pipe[Pure, A, A] = inputStream => {
      def loop(s: Stream[Pure, A], n: Int): Pull[Pure, A, Unit] = {
        s.pull.uncons.flatMap {
          case Some((chunk, stream)) =>
            if chunk.size < n then loop(stream, n - chunk.size)
            else Pull.output(chunk.drop(n)) >> stream.pull.echo
          case None => Pull.done
        }
      }

      loop(inputStream, n).stream
    }
    IO.println(s.through(drop(3)).toList)

    // Exercise
    def filter[A](p: A => Boolean): Pipe[Pure, A, A] = inputStream => {
      def loop(s: Stream[Pure, A]): Pull[Pure, A, Unit] = {
        s.pull.uncons.flatMap {
          case Some((chunk, stream)) => Pull.output(chunk.filter(p)) >> loop(stream)
          case None                  => Pull.done
        }
      }

      loop(inputStream).stream
    }
    IO.println(s.through(filter(_ % 2 == 1)).toList)

    def runningSum(s: Stream[Pure, Int]): Stream[Pure, Int] = {
      s.scanChunksOpt(0) { (acc: Int) =>
        Option { (chunk: Chunk[Int]) =>
          val newAcc = chunk.foldLeft(acc) { _ + _ }
          (newAcc, Chunk.singleton(newAcc))
        }
      }
    }
    IO.println(s.through(runningSum).toList)

    // Exercise
    def runningMax(s: Stream[Pure, Int]): Stream[Pure, Int] = {
      s.scanChunksOpt(Int.MinValue) { (acc: Int) =>
        Option { (chunk: Chunk[Int]) =>
          val newAcc = chunk.foldLeft(acc) { _ max _ }
          (newAcc, Chunk.singleton(newAcc))
        }
      }
    }
    val t = Stream(-1, -2, -3) ++ Stream(10) ++ Stream(4, 7, 1)
    IO.println(t.through(runningMax).toList)
  }
}
