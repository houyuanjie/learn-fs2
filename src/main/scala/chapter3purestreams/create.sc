import fs2.*

val s: Stream[Pure, Int] = Stream(1, 2, 3)
val s2: Stream[Pure, Int] = Stream.empty
val s3: Stream[Pure, Int] = Stream.emit(42)
val s4: Stream[Pure, Int] = Stream.emits(List(1, 2, 3))
s4.toList
s4.toVector

val s5 = Stream.iterate(1) { _ + 1 }
// s5.toList
s5.take(5).toList

val s6 = Stream.unfold(1) {
  case 5 => None
  case s => Some((s.toString, s + 1))
}
s6.take(10).toList

val s7 = Stream.range(1, 15)
s7.toList

val s8 = Stream.constant(42)
s8.take(15).toList

// Exercise #1
val lettersIter: Stream[Pure, Char] =
  Stream
    .iterate('a') { c => (c + 1).toChar }
    .take(26)
lettersIter.toList

// Exercise #2
val lettersUnfold: Stream[Pure, Char] =
  Stream.unfold('a') { letter =>
    if letter <= 'z' then Some((letter, (letter + 1).toChar))
    else None
  }
lettersUnfold.toList

// Exercise #3
def myIterate[A](initial: A)(next: A => A): Stream[Pure, A] = {
  Stream.unfold(initial) { current =>
    Some(current, next(current))
  }
}

val lettersList: List[Char] = lettersIter.toList
val myIterList: List[Char] = myIterate('a') { c => (c + 1).toChar }.take(26).toList
assert(lettersList == myIterList)
