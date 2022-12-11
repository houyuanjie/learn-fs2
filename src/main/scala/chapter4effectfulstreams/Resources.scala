package chapter4effectfulstreams

import cats.effect.*
import fs2.*

import java.io.{BufferedReader, FileReader}

object Resources extends IOApp.Simple {
  override val run: IO[Unit] = {
    def readLines(reader: BufferedReader): Stream[IO, String] = {
      Stream
        .repeatEval(IO.blocking(reader.readLine()))
        .takeWhile { _ != null }
    }

    val filename = "./data/lego/sets.csv"

    val acquireReader = IO.println("Acquiring") *> IO.blocking(new BufferedReader(new FileReader(filename)))
    def releaseReader(reader: BufferedReader) = IO.println("Releasing") *> IO.blocking(reader.close())
    val readerR = Resource.make(acquireReader)(releaseReader)

    Stream
      // .bracket(acquireReader)(releaseReader)
      // .resource(readerR)
      .fromAutoCloseable(acquireReader)
      .flatMap(readLines)
      .take(10)
      .printlns
      .compile
      .drain
  }
}
