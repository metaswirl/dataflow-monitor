package berlin.bbdc.inet.mera.server.metrics

case class MetricNotFoundException(key: String, id: String) extends Exception(s"Could not find $key for $id")

trait Metric[+N] {
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

abstract class MetricSummary[T <: Metric[_]](val n: Int) {
  var history: List[(Long, T)] = List()

  def add(ts: Long, newVal: T): Unit = {
    history = (ts, newVal) :: history
    if (history.length > n) history = history.dropRight(history.length - n)
  }

  def calculateMean(l: List[(Long, T)]): Double

  def getMean: Double = calculateMean(history)

  //like getMean but for the last seconds with one second delay
  def getMeanBeforeLastSeconds(seconds: Int): (Long, Double) = {
    val delay = 1 // metric collection delay in seconds
    val now = System.currentTimeMillis()
    val recentHistory = history.filter(x =>
      (x._1 >= now - (seconds + delay) * 1000) && (x._1 <= now - delay * 1000))
    val value = calculateMean(recentHistory)
    (now, value)
  }


  def getRates: List[Double]

  def getRateMean: Double = getRates.sum / history.length

  override def toString: String = {
    val m = getMean
    val rm = getRateMean
    val cn = this.getClass.getSimpleName
    val head = history.map(_._2).head
    val last = history.map(_._2).last
    f"$cn%s($n%s, $m%s, $rm%s, $head%s $last%s)"
  }
}

object MetricSummary {
  type NumberMetric = Metric[_ >: Double with Int with Long <: AnyVal]
}

class GaugeSummary(override val n: Int) extends MetricSummary[Gauge](n) {

  override def calculateMean(l: List[(Long, Gauge)]): Double = if (l.nonEmpty) l.map(_._2.value).sum / (1.0 * n) else 0.0

  override def getRates: List[Double] = {
    var res: List[Double] = List()
    for ((first, second) <- (history, history.drop(1)).zipped) {
      res = (first._2.value - second._2.value) * 1.0 / (first._1 - second._1) :: res
    }
    res.reverse
  }
}

class CounterSummary(override val n: Int) extends MetricSummary[Counter](n) {

  override def calculateMean(l: List[(Long, Counter)]): Double = if (l.nonEmpty) l.map(_._2.count).sum / (1.0 * n) else 0.0

  override def getRates: List[Double] = {
    var res: List[Double] = List()
    for ((first, second) <- (history, history.drop(1)).zipped) {
      res = (first._2.count - second._2.count) * 1.0 / (first._1 - second._1) :: res
    }
    res.reverse
  }

}

class MeterSummary(override val n: Int) extends MetricSummary[Meter](n) {

  override def calculateMean(l: List[(Long, Meter)]): Double = if (l.nonEmpty) l.map(_._2.rate).sum / (1.0 * n) else 0.0

  override def getRates: List[Double] = {
    history.map(_._2.rate)
  }
}

abstract class MetricKey(val rawKey: String)

case class UnknownMetricKey(override val rawKey: String) extends MetricKey(rawKey)

case class JobManagerMetricKey(override val rawKey: String, host: String, metric: String) extends MetricKey(rawKey)

abstract class TaskManagerMetricKey(rawKey: String, host: String, tmId: String) extends MetricKey(rawKey)

abstract class TaskManagerStatusMetricKey(rawKey: String, host: String, tmId: String) extends TaskManagerMetricKey(rawKey, host, tmId)

case class TaskManagerJvmMetricKey(override val rawKey: String, host: String, tmId: String, metric: String) extends TaskManagerStatusMetricKey(rawKey, host, tmId)

case class TaskManagerNetworkMetricKey(override val rawKey: String, host: String, tmId: String, metric: String) extends TaskManagerStatusMetricKey(rawKey, host, tmId)

case class TaskManagerTaskMetricKey(override val rawKey: String, host: String, tmId: String, jobId: String, opId: String, taskId: Int, metric: String) extends TaskManagerMetricKey(rawKey, host, tmId)

object MetricKey {
  var allKeys: Set[String] = Set()

  def buildKey(rawKey: String): MetricKey = {
    allKeys += rawKey
    val splitKey: Array[String] = rawKey.split('.')
    if (splitKey.length > 1 && splitKey(1) == "jobmanager") {
      val host = splitKey(0)
      return JobManagerMetricKey(rawKey, host, splitKey.drop(2).mkString("."))
    } else if (splitKey.length > 1 && splitKey(1) == "taskmanager") {
      val host = splitKey(0)
      if (splitKey.length > 2 && splitKey(3) == "Status") {
        if (splitKey.length > 4 && splitKey(4) == "JVM") {
          return TaskManagerJvmMetricKey(rawKey, host, splitKey(2), splitKey.drop(4).mkString("."))
        } else if (splitKey.length > 4 && splitKey(4) == "Network") {
          return TaskManagerNetworkMetricKey(rawKey, host, splitKey(2), splitKey.drop(4).mkString("."))
        }
      } else if (splitKey.length > 6) {
        return TaskManagerTaskMetricKey(rawKey, host, splitKey(2), splitKey(3), splitKey(4), splitKey(5).toInt, splitKey.drop(6).mkString("."))
      }
    }
    UnknownMetricKey(rawKey)
  }
}

