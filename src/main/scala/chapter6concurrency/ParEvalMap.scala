package chapter6concurrency

import cats.effect.*
import fs2.*

import scala.concurrent.duration.*

object ParEvalMap extends IOApp.Simple {
  override def run: IO[Unit] = {

    sealed trait JobState
    case object Created extends JobState
    case object Processed extends JobState

    case class Job(id: Long, state: JobState)

    def processJob(job: Job): IO[Job] = {
      for {
        _ <- IO.println(s"Processing job [id=${job.id}]")
        _ <- IO.sleep(1.second)
        doneJob <- IO.pure(job.copy(state = Processed))
      } yield doneJob
    }

    val jobs = Stream.unfold(1) { id => Some((Job(id, Created), id + 1)) }.covary[IO]
    jobs
      // .evalMap(processJob)
      // .parEvalMapUnbounded(processJob)
      // .parEvalMap(5)(processJob)
      // .parEvalMapUnordered(5)(processJob)
      .parEvalMapUnorderedUnbounded(processJob)
      .interruptAfter(5.seconds)
      .compile
      .toList
      .flatMap(IO.println)

    // Exercise
    case class Event(jobId: Long, seqNo: Long)

    def processJobs(job: Job): IO[List[Event]] = {
      for {
        _ <- IO.println(s"Processing job [id=${job.id}]")
        _ <- IO.sleep(1.second)
        event <- IO(List.range(1, 10).map { seqNo => Event(job.id, seqNo) })
      } yield event
    }

    extension [A](s: Stream[IO, A]) {
      def parEvalMapSeq[B](maxConcurrent: Int)(f: A => IO[List[B]]): Stream[IO, B] = {
        s.parEvalMap(maxConcurrent)(f).flatMap(Stream.emits)
      }

      def parEvalMapSeqUnbounded[B](f: A => IO[List[B]]): Stream[IO, B] = {
        parEvalMapSeq(Int.MaxValue)(f)
      }
    }

    jobs
      // .parEvalMapSeq(5)(processJobs)
      .parEvalMapSeqUnbounded(processJobs)
      .interruptAfter(3.seconds)
      .compile
      .toList
      .flatMap(IO.println)
  }
}
