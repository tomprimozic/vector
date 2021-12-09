package experiment.vector

import scala.jdk.CollectionConverters._


class TestVector extends munit.FunSuite {
  class VectorWrapper(var v: Vector[Any], val q: java.util.Deque[Any]) {
    def append(xs: scala.collection.IterableOnce[_]): this.type = {
      val ys = xs.iterator.toArray
      val w = v
      val before = v.toList.asScala
      ys.foreach { y => v = v.push_right(y); q.addLast(y) }
      val after = w.toList.asScala
      assertEquals(before, after)
      assertEquals(v.toList.asScala, q.asScala.toBuffer)
      this
    }
    def prepend(xs: scala.collection.IterableOnce[_]): this.type = {
      val ys = xs.iterator.toArray
      val w = v
      val before = v.toList.asScala
      ys.foreach { y => v = v.push_left(y); q.addFirst(y) }
      val after = w.toList.asScala
      assertEquals(before, after)
      assertEquals(v.toList.asScala, q.asScala.toBuffer)
      this
    }
    def dropRight(n: Int): this.type = {
      val w = v
      val before = v.toList.asScala
      for(_ <- Range(0, n)) {
        val e = q.removeLast()
        assertEquals(v.get(v.size() - 1), e)
        v = v.pop_right()
      }
      val after = w.toList.asScala
      assertEquals(before, after)
      assertEquals(v.toList.asScala, q.asScala.toBuffer)
      this
    }
    def dropLeft(n: Int): this.type = {
      val w = v
      val before = v.toList.asScala
      for(_ <- Range(0, n)) {
        val e = q.removeFirst()
        assertEquals(v.get(0), e)
        v = v.pop_left()
      }
      val after = w.toList.asScala
      assertEquals(before, after)
      assertEquals(v.toList.asScala, q.asScala.toBuffer)
      this
    }

    def append(xs: Int*): this.type = this.append(xs.toSeq)
    def prepend(xs: Int*): this.type = this.prepend(xs.toSeq)
  }

  def check(name: String, f: VectorWrapper => Unit): Unit = {
    test(name) {
      f(new VectorWrapper(Vector.empty.asInstanceOf[Vector[Any]], new java.util.ArrayDeque[Any]()))
    }
  }

  check("append", _.append(0).append(Range(1, 10)).append(Range(11, 33)).append(Range(34, 112)))
  check("prepend", _.prepend(0).prepend(Range(1, 10)).prepend(Range(11, 33)).prepend(Range(34, 112)))

  check("append + dropRight", _.append(Range(0, 96)).dropRight(30))
  check("append + dropLeft", _.prepend(Range(0, 96)).dropLeft(30))

  check("prepend + append + dropRight", _.prepend(Range(0, 60)).append(-1).dropRight(5).dropRight(3).dropRight(1).dropRight(20))
  check("append + prepend + dropLeft", _.append(Range(0, 60)).prepend(-1).dropLeft(5).dropLeft(3).dropLeft(1).dropLeft(20))

  check("prepend + dropRight", _.prepend(Range(0, 60)).dropRight(9))
  check("append + dropLeft", _.append(Range(0, 60)).dropLeft(9))

  check("prepend + dropRight all", _.prepend(Range(0, 20)).dropRight(20))
  check("append + dropLeft all", _.append(Range(0, 20)).dropLeft(20))

  check("prepend + dropLeft", _.prepend(Range(0, 96)).dropLeft(30))
  check("append + dropRight", _.append(Range(0, 96)).dropRight(30))

  check("append + dropRight + dropRight", _.append(Range(0, 800)).dropRight(368).dropRight(432))
  check("append + dropLeft + dropLeft", _.append(Range(0, 800)).dropLeft(368).dropLeft(432))
  check("append + dropRight + dropLeft", _.append(Range(0, 800)).dropRight(368).dropLeft(432))
  check("append + dropLeft + dropRight", _.append(Range(0, 800)).dropLeft(368).dropRight(432))
  check("prepend + dropRight + dropRight", _.prepend(Range(0, 800)).dropRight(368).dropRight(432))
  check("prepend + dropLeft + dropLeft", _.prepend(Range(0, 800)).dropLeft(368).dropLeft(432))
  check("prepend + dropRight + dropLeft", _.prepend(Range(0, 800)).dropRight(368).dropLeft(432))
  check("prepend + dropLeft + dropRight", _.prepend(Range(0, 800)).dropLeft(368).dropRight(432))

  check("append(484) + dropRight", _.append(Range(0, 484)).dropRight(484))
  check("prepend(484) + dropLeft", _.prepend(Range(0, 484)).dropLeft(484))
  check("append(484) + dropLeft", _.append(Range(0, 484)).dropLeft(484))
  check("prepend(484) + dropRight", _.prepend(Range(0, 484)).dropRight(484))

  check("append(9) + dropRight", _.append(Range(0, 9)).dropRight(1).dropRight(3).dropRight(1).dropRight(1).dropRight(2).dropRight(1))
  check("append(9) + dropLeft", _.append(Range(0, 9)).dropLeft(1).dropLeft(3).dropLeft(1).dropLeft(1).dropLeft(2).dropLeft(1))
  check("prepend(9) + dropRight", _.prepend(Range(0, 9)).dropRight(1).dropRight(3).dropRight(1).dropRight(1).dropRight(2).dropRight(1))
  check("prepend(9) + dropLeft", _.prepend(Range(0, 9)).dropLeft(1).dropLeft(3).dropLeft(1).dropLeft(1).dropLeft(2).dropLeft(1))

  test("empty") {
    intercept[IllegalStateException] {
      Vector.empty.pop_left()
    }
    intercept[IllegalStateException] {
      Vector.empty.pop_right()
    }
  }
}
