package berlin.bbdc.inet.flinkPlugin

import org.apache.flink.metrics._
import scala.io.Source
import scala.collection.mutable.Set

class MockGauge[T](var value : T) extends Gauge[T] {
  override def getValue = value
  def setValue(newValue : T) = { value = newValue }
}
class MockHistStats(min: Long, max: Long, mean: Double) extends HistogramStatistics {
  def getValues: Array[Long] = ???

  def getMax: Long = max

  def getStdDev: Double = ???

  def size(): Int = ???

  def getMin: Long = min

  def getQuantile(quantile: Double): Double = ???

  def getMean: Double = mean
}
class MockHistogram(count: Long, stats: MockHistStats) extends Histogram {
  def getStatistics = stats

  def update(value: Long) = ???

  def getCount: Long = count
}
class MockMeter extends Meter {
  def getRate: Double = ???

  def markEvent() = ???

  def markEvent(n: Long) = ???

  def getCount: Long = ???
}

class MockCounter(var count : Long = 0) extends Counter {
  def dec(): Unit = {
    count = count - 1
  }

  def dec(n: Long): Unit = {
    count = count - n
  }

  def getCount : Long = count

  def inc(): Unit = {
    count = count + 1
  }

  def inc(n: Long) : Unit = {
    count = count + n
  }
}

class MockMetricGroup extends MetricGroup {
  def gauge[T, G <: Gauge[T]](name: Int, gauge: G) = ???

  def gauge[T, G <: Gauge[T]](name: String, gauge: G) = ???

  def meter[M <: Meter](name: String, meter: M) = ???

  def meter[M <: Meter](name: Int, meter: M) = ???

  def getMetricIdentifier(metricName: String) = metricName

  def getMetricIdentifier(metricName: String, filter: CharacterFilter) = ???

  def counter(name: Int) = ???

  def counter(name: String) = ???

  def counter[C <: Counter](name: Int, counter: C) = ???

  def counter[C <: Counter](name: String, counter: C) = ???

  def getAllVariables = ???

  def histogram[H <: Histogram](name: String, histogram: H) = ???

  def histogram[H <: Histogram](name: Int, histogram: H) = ???

  def getScopeComponents = ???

  def addGroup(name: Int) = ???

  def addGroup(name: String) = ???
}

object TestFlinkReporter extends App {
  val a = """{"type":"counter", "key":"fubar", "value":1}"""
  println("Client")
  val x = new FlinkMetricPusher
  // TODO: This does not handle updates well

  var set : Set[String] = Set()
  var gauges : Map[String, MockGauge[Long]] = Map()
  var count = 0
  for (line <- Source.fromFile(getClass.getClassLoader.getResource("testData/testMetrics.txt").getFile).getLines) {
    val l = line.stripLineEnd
    if (l.length > 1) {
      val lParts: Array[String] = line.split(",")
      val key: String = lParts(0)
      val value: Long = lParts(1).toLong
      if (gauges.contains(key)) {
          gauges(key).setValue(value)
      } else {
        val gauge = new MockGauge[Long](value)
        gauges += key -> gauge
        x.notifyOfAddedMetric(gauge, key, new MockMetricGroup)
      }
      // only report if all keys have been seen exactly once
      if (set.contains(key)) {
        count += 1
        x.report()
        set = Set()
      }
      set += key
    }
  }
  x.report()
}
object TestFlinkReporter2 extends App {
  val x = new FlinkMetricPusher
  val gauge = new MockGauge[String]("{LatencySourceDescriptor{vertexID=1, subtaskIndex=-1}={p99=139852.0, p50=48365.0, min=902.0, max=139852.0, p95=129115.75, mean=52406.70212765957}}")
  x.notifyOfAddedMetric(gauge, "latency", new MockMetricGroup)

}
