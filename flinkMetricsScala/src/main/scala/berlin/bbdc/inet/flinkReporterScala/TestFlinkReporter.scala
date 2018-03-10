package berlin.bbdc.inet.flinkReporterScala

import org.apache.flink.metrics._

class MockGauge[T](value : T) extends Gauge[T] {
  override def getValue = value
}
class MockHistStats(min: Long, max: Long, mean: Double) extends HistogramStatistics {
  override def getValues: Array[Long] = ???

  override def getMax: Long = max

  override def getStdDev: Double = ???

  override def size(): Int = ???

  override def getMin: Long = min

  override def getQuantile(quantile: Double): Double = ???

  override def getMean: Double = mean
}
class MockHistogram(count: Long, stats: MockHistStats) extends Histogram {
  override def getStatistics = stats

  override def update(value: Long) = ???

  override def getCount = count
}
class MockMeter extends Meter {
  override def getRate = ???

  override def markEvent() = ???

  override def markEvent(n: Long) = ???

  override def getCount = ???
}

class MockCounter(var count : Long = 0) extends Counter {
  override def dec() = {
    count = count - 1
  }

  override def dec(n: Long) = {
    count = count - n
  }

  override def getCount = count

  override def inc() = {
    count = count + 1
  }

  override def inc(n: Long) = {
    count = count + n
  }
}

class MockMetricGroup extends MetricGroup {
  override def gauge[T, G <: Gauge[T]](name: Int, gauge: G) = ???

  override def gauge[T, G <: Gauge[T]](name: String, gauge: G) = ???

  override def meter[M <: Meter](name: String, meter: M) = ???

  override def meter[M <: Meter](name: Int, meter: M) = ???

  override def getMetricIdentifier(metricName: String) = metricName

  override def getMetricIdentifier(metricName: String, filter: CharacterFilter) = ???

  override def counter(name: Int) = ???

  override def counter(name: String) = ???

  override def counter[C <: Counter](name: Int, counter: C) = ???

  override def counter[C <: Counter](name: String, counter: C) = ???

  override def getAllVariables = ???

  override def histogram[H <: Histogram](name: String, histogram: H) = ???

  override def histogram[H <: Histogram](name: Int, histogram: H) = ???

  override def getScopeComponents = ???

  override def addGroup(name: Int) = ???

  override def addGroup(name: String) = ???
}

object TestFlinkReporter {
  def main(args: Array[String]): Unit = {
    val a = """{"type":"counter", "key":"fubar", "value":1}"""
    println("Client")
    val x = new FlinkMetricPusher
    x.notifyOfAddedMetric(new MockCounter(), "fubar", new MockMetricGroup)
    x.notifyOfAddedMetric(new MockCounter(10), "fubarx", new MockMetricGroup)
    x.notifyOfAddedMetric(new MockGauge[Long](10000L), "buddy-long", new MockMetricGroup)
    x.notifyOfAddedMetric(new MockGauge[Int](1010), "buddy-int", new MockMetricGroup)
    x.notifyOfAddedMetric(new MockGauge[Short](13), "buddy-short", new MockMetricGroup)
    x.notifyOfAddedMetric(new MockHistogram(10L, new MockHistStats(0L, 101L, 100.0)), "friend", new MockMetricGroup)
    x.report()
    x.notifyOfAddedMetric(new MockCounter(12), "asd", new MockMetricGroup)
    x.report()
  }
}
