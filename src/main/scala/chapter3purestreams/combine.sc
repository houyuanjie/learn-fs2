import fs2.*

val s1 = Stream(1, 2, 3)
val s2 = Stream(4, 5, 6)
val nats = Stream.iterate(1) { _ + 1 }

(s1 ++ s2).toList
(s1 ++ nats).take(10).toList
(nats ++ s1).take(10).toList

val doubled = s1.map { _ * 2 }
doubled.toList

val evens = nats.map { _ * 2 }
evens.take(10).toList

s1.flatMap { i => Stream(i, i + 1) }.toList
nats.flatMap { i => Stream(i, i + 1) }.take(10).toList
s2.flatMap { i => nats.drop(i) }.take(10).toList // == nats.drop(4)

val odds = nats.filter { _ % 2 == 1 }
odds.take(5).toList

val x = for {
  n <- nats
  n1 <- s1
  n2 <- s2
} yield n + n1 + n2

x.take(10).toList

(s1 zip s2).toList
(nats zip evens).take(10).toList
(nats zip s1).toList
(s1 zip nats).toList

Stream.constant(2).zipWith(nats) { _ * _ }.take(10).toList

val length = s1.fold(0) { case (acc, _) => acc + 1 }.toList
val sum = s1.fold(0) { case (acc, e) => acc + e }.toList

// Exercise #1
val myOdds = nats.map { _ * 2 - 1 }
myOdds.take(10).toList

// Exercise #2
def repeat[A](s: => Stream[Pure, A]): Stream[Pure, A] =
  s ++ repeat(s)

repeat(s1).take(15).toList

// Exercise #3
def unNone[A](s: Stream[Pure, Option[A]]): Stream[Pure, A] =
  s.collect { case Some(value) => value }

import cats.syntax.option.*

unNone(Stream(1.some, none, 2.some)).toList
