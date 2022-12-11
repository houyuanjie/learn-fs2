package syntex

import cats.effect.IO
import fs2.Stream

object stream {
  extension [A](s: Stream[IO, A]) {
    def log(): IO[Unit] =
      s.printlns.compile.drain
  }
}
