package experiment.vector;

import scala.util.Random
import clojure.lang.PersistentVector
import scala.collection.immutable.{Vector => ScalaVector}

object Benchmark {
  def main(args: Array[String]): Unit = {
    Random.setSeed(100)

    val results = for(i <- Range(0, 10);
        r = new Runner(10000000, 100000);
        b <- List[Benchmark[_]](BenchmarkClojure, BenchmarkVector, BenchmarkScala);
        s = r.run(b)
        if i > 3;   // warm up JVM
        m <- s
      ) yield m

    println()
    println(f"  ${ "" }%10s    ${ "mean" }%8s   ${ "std" }%8s   ${ "min" }%8s    ${ "max" }%8s")
    for((impl, results) <- results.groupBy(_.impl)) {
      println(impl)
      val all_metrics = results.groupBy(_.op)
      for(op <- all_metrics.keys.toArray.sorted;
          metrics = all_metrics(op)) {
        val ms = metrics.map(_.ms.toDouble).toArray
        val mean = ms.sum / ms.length
        val std = math.sqrt(ms.map(m => m * m).sum / ms.length - mean * mean)
        val min = ms.min.toLong
        val max = ms.max.toLong
        println(f"  $op%10s:   $mean%8.2f   $std%8.2f   $min%8d    $max%8d")
      }
    }
  }
}

case class Result(impl: String, op: String, ms: Long)

class Runner(N: Int, M: Int) {
  val r = Range(0, N)
  val seq = Random.shuffle(r.toIndexedSeq)
  val p = {
    val p = (for (
      _ <- Range(0, M);
      n = Random.nextInt(1000);
      s = Random.nextBoolean()
    ) yield n * (if (s) 1 else -1)).toArray
    var sum = 0
    for (i <- p.indices) {
      sum += p(i)
      if (sum < 0) {
        p(i) -= sum
        sum = 0
      }
    }
    p.filter(_ != 0)
  }
  val obj = "a"

  def run[T](b: Benchmark[T]): List[Result] = {
    print("."); System.out.flush()

    val result = List.newBuilder[Result]

    val t0 = System.currentTimeMillis()
    var v = b.empty()
    for (i <- r) {
      v = b.push_right(v, i)
    }
    val t1 = System.currentTimeMillis()
    require(b.size(v) == r.length)
    result += Result(b.name, "append", t1 - t0)

    var sum = 0
    for(i <- seq) {
      sum = -(sum + b.get(v, i).asInstanceOf[Int])
    }
    val t2 = System.currentTimeMillis()
    require(sum == seq.grouped(2).map(s => s(0) - s(1)).sum)
    result += Result(b.name, "get", t2 - t1)

    v = b.empty()
    for(i <- p) {
      if(i > 0) {
        for(_ <- Range(0, i)) {
          v = b.push_right(v, obj)
        }
      } else {
        for(_ <- Range(0, -i)) {
          v = b.pop_right(v)
        }
      }
    }
    val t3 = System.currentTimeMillis()
    require(b.size(v) == p.sum)
    result += Result(b.name, "stack", t3 - t2)

    try {
      v = b.empty()
      for (i <- p) {
        if (i > 0) {
          for (_ <- Range(0, i)) {
            v = b.push_right(v, obj)
          }
        } else {
          for (_ <- Range(0, -i)) {
            v = b.pop_left(v)
          }
        }
      }
      val t4 = System.currentTimeMillis()
      require(b.size(v) == p.sum)
      result += Result(b.name, "queue", t4 - t3)
    } catch {
      case _: UnsupportedOperationException =>
    }
    result.result()
  }
}

abstract class Benchmark[T] {
  def name: String
  def empty(): T
  def get(v: T, i: Int): Any
  def push_right(v: T, e: Any): T
  def pop_right(v: T): T
  def pop_left(v: T): T
  def size(v: T): Int
}

object BenchmarkVector extends Benchmark[Vector[Any]] {
  val name = "Vector"
  def empty() = Vector.empty.asInstanceOf[Vector[Any]]
  def get(v: Vector[Any], i: Int) = v.get(i)
  def push_right(v: Vector[Any], e: Any) = v.push_right(e)
  def pop_right(v: Vector[Any]) = v.pop_right()
  def pop_left(v: Vector[Any]) = v.pop_left()
  def size(v: Vector[Any]) = v.size()
}

object BenchmarkClojure extends Benchmark[PersistentVector] {
  val name = "Clojure"
  def empty() = PersistentVector.EMPTY
  def get(v: PersistentVector, i: Int) = v.get(i)
  def push_right(v: PersistentVector, e: Any) = v.cons(e)
  def pop_right(v: PersistentVector) = v.pop()
  def pop_left(v: PersistentVector) = throw new UnsupportedOperationException
  def size(v: PersistentVector) = v.count()
}

object BenchmarkScala extends Benchmark[ScalaVector[Any]] {
  val name = "Scala2"
  def empty() = ScalaVector.empty[Any]
  def get(v: ScalaVector[Any], i: Int) = v(i)
  def push_right(v: ScalaVector[Any], e: Any) = v :+ e
  def pop_right(v: ScalaVector[Any]) = v.dropRight(1)
  def pop_left(v: ScalaVector[Any]) = v.drop(1)
  def size(v: ScalaVector[Any]) = v.size
}