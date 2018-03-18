package berlin.bbdc.inet.mera.metricServer

abstract class Metric[+N] {
  def value: N
}
case class Counter(count: Long) extends Metric[Long] {
  def value: Long = count
}
case class Meter(count: Long, rate: Double) extends Metric[Double] {
  def value: Double = rate
}
case class Histogram(count: Long, min: Long, max: Long, mean: Double) extends Metric[Double] {
  def value: Double = mean
}
case class Gauge(value: Double) extends Metric[Double]

// TODO: Make MetricSummary covariant
abstract class MetricSummary[T](val n: Int) {
  var history: List[(Long, T)] = List()
  var number: Int = 0

  def add(ts: Long, newVal: T): Unit = {
    history = (ts, newVal) :: history
    if (history.length > n) history = history.dropRight(history.length - n)
  }

  def getMean: Double

  def getRates: List[Double]

  def getRateMean: Double = getRates.sum * 1000 / (n - 1)

  override def toString: String = {
    val m = getMean
    val rm = getRateMean
    val cn = this.getClass.getSimpleName
    val head = history.map(_._2).head
    val last = history.map(_._2).last
    f"$cn%s($n%s, $m%s, $rm%s, ${head}%s ${last}%s)"
  }
}
object MetricSummary {
  type NumberMetric = Metric[_ >: Double with Int with Long <: AnyVal]
}
class GaugeSummary(override val n: Int) extends MetricSummary[Gauge](n) {
  override def getMean: Double = if (history.length > 0) history.map(_._2.value).sum / (1.0 * n) else 0.0

  def getRates: List[Double] = {
    var res: List[Double] = List()
    for ((first, second) <- (history, history.drop(1)).zipped) {
      res = (first._2.value - second._2.value) * 1.0 / (first._1 - second._1) :: res
    }
    res.reverse
  }
}
class CounterSummary(override val n: Int) extends MetricSummary[Counter](n) {
  def getMean: Double = if (history.length > 0) history.map(_._2.count).sum / (1.0 * n) else 0.0

  def getRates: List[Double] = {
    var res: List[Double] = List()
    for ((first, second) <- (history, history.drop(1)).zipped) {
      res = (first._2.count - second._2.count) * 1.0 / (first._1 - second._1) :: res
    }
    res.reverse
  }
}
class MeterSummary(override val n: Int) extends MetricSummary[Meter](n) {
  def getMean: Double = if (history.length > 0) history.map(_._2.rate).sum / (1.0 * n) else 0.0

  def getRates: List[Double] = {
    history.map(_._2.rate)
  }
}

sealed trait MetricKey
case class UnknownMetricKey(metric: String) extends MetricKey
case class JobManagerMetricKey(host: String, metric: String) extends MetricKey
abstract class TaskManagerMetricKey(host: String, tmId: String) extends MetricKey
abstract class TaskManagerStatusMetricKey(host: String, tmId: String) extends TaskManagerMetricKey(host, tmId)
case class TaskManagerJvmMetricKey(host: String, tmId: String, metric: String) extends TaskManagerStatusMetricKey(host, tmId)
case class TaskManagerNetworkMetricKey(host: String, tmId: String, metric: String) extends TaskManagerStatusMetricKey(host, tmId)
case class TaskManagerTaskMetricKey(host: String, tmId: String, jobId: String, opId: String, taskId: Int, metric: String) extends TaskManagerMetricKey(host, tmId)

object MetricKey {
  var allKeys: Set[String] = Set()

  def buildKey(rawKey: String): MetricKey = {
    allKeys += rawKey
    val splitKey: Array[String] = rawKey.split('.')
    if (splitKey.length > 1 && splitKey(1) == "jobmanager") {
      val host = splitKey(0)
      return JobManagerMetricKey(host, splitKey.drop(2).mkString("."))
    } else if (splitKey.length > 1 && splitKey(1) == "taskmanager") {
      val host = splitKey(0)
      if (splitKey.length > 2 && splitKey(3) == "Status") {
        if (splitKey.length > 4 && splitKey(4) == "JVM") {
          return TaskManagerJvmMetricKey(host, splitKey(2), splitKey.drop(4).mkString("."))
        } else if (splitKey.length > 4 && splitKey(4) == "Network") {
          return TaskManagerNetworkMetricKey(host, splitKey(2), splitKey.drop(4).mkString("."))
        }
      } else if (splitKey.length > 6) {
        return TaskManagerTaskMetricKey(host, splitKey(2), splitKey(3), splitKey(4), splitKey(5).toInt, splitKey.drop(6).mkString("."))
      }
    }
    UnknownMetricKey(rawKey)
  }
}

