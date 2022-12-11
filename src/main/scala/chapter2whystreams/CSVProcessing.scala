package chapter2whystreams

import cats.effect.{IO, IOApp, Resource}
import fs2.io.file.{Files, Path}
import fs2.text

import java.io.{BufferedReader, FileReader}
import java.nio.file.{Paths, Files as JFiles}
import scala.collection.mutable.ListBuffer
import scala.io.Source
import scala.util.{Try, Using}

object CSVProcessing extends IOApp.Simple {

  case class LegoSet(
      setNum: String,
      name: String,
      year: Int,
      themeId: Int,
      numParts: Int,
      imgUrl: String
  )

  def parseLegoSet(line: String): Option[LegoSet] = {
    Try {
      val arr = line.split(",")
      LegoSet(arr(0), arr(1), arr(2).toInt, arr(3).toInt, arr(4).toInt, arr(5))
    }.toOption
  }

  def readLegoSetImperative(filename: String, predicate: LegoSet => Boolean, limit: Int): IO[List[LegoSet]] = {

    def doRead(reader: BufferedReader, legoSetBuffer: ListBuffer[LegoSet]): IO[Unit] = IO {
      var count = 0
      var line = reader.readLine()

      while count < limit && line != null do
        parseLegoSet(line)
          .filter(predicate)
          .foreach { legoSet =>
            legoSetBuffer += legoSet
            count += 1
          }

        line = reader.readLine()
      end while
    }

    val legoSetBuffer = ListBuffer.empty[LegoSet]
    val readerR = Resource.fromAutoCloseable(IO { new BufferedReader(new FileReader(filename)) })

    readerR
      .use { reader => doRead(reader, legoSetBuffer) }
      .flatMap { _ => IO { legoSetBuffer.toList } }
  }

  def readLegoSetList(filename: String, predicate: LegoSet => Boolean, limit: Int): IO[List[LegoSet]] = IO {
    import scala.jdk.CollectionConverters.*

    JFiles
      .readAllLines(Paths.get(filename))
      .asScala
      .flatMap(parseLegoSet)
      .filter(predicate)
      .take(limit)
      .toList
  }

  def readLegoSetIterator(filename: String, predicate: LegoSet => Boolean, limit: Int): IO[List[LegoSet]] = IO {
    Using(Source.fromFile(filename)) { source =>
      source
        .getLines()
        .flatMap(parseLegoSet)
        .filter(predicate)
        .take(limit)
        .toList
    }.getOrElse(List())
  }

  def readLegoSetStream(filename: String, predicate: LegoSet => Boolean, limit: Int): IO[List[LegoSet]] = {
    /* import scala.concurrent.duration.* */

    Files[IO]
      .readAll(Path(filename))
      .through(text.utf8.decode)
      .through(text.lines)
      .map(parseLegoSet)
      /* .parEvalMapUnbounded { line => IO { parseLegoSet(line) } } */
      /* .metered(1.second) */
      /* .evalTap(IO.println) */
      .unNone
      .filter(predicate)
      .take(limit)
      .compile
      .toList
  }

  override def run: IO[Unit] = {
    val filename = "data/lego/sets.csv"
    readLegoSetStream(filename, _.year >= 2002, 3) flatMap IO.println
  }
}
