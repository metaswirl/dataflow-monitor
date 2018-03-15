package berlin.bbdc.inet.mera.flinkPlugin

import java.io.File

import akka.actor.{Actor, ActorContext, ActorSystem, Props}
import com.typesafe.config.ConfigFactory
import org.apache.flink.metrics.Meter
import org.apache.flink.metrics.Counter
import org.apache.flink.metrics.Histogram
import org.apache.flink.metrics.Gauge
import org.apache.flink.metrics.MetricGroup
import org.apache.flink.metrics.Metric
import org.apache.flink.metrics.MetricConfig
import org.apache.flink.metrics.reporter.MetricReporter
import org.apache.flink.metrics.reporter.Scheduled
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import berlin.bbdc.inet.mera.message.MetricUpdate._
import berlin.bbdc.inet.mera.message.MetricUpdate
import java.lang.ClassCastException

trait FlinkMetricManager extends MetricReporter {
  var counters = Map[String,Counter]()
  var gauges = Map[String,Gauge[Number]]()
  var histograms = Map[String,Histogram]()
  var meters = Map[String,Meter]()

  val LOG : Logger = LoggerFactory.getLogger("MeraFlinkPlugin")

  override def notifyOfAddedMetric(metric: Metric, metricName: String, group: MetricGroup) = {
    val fullName : String = group.getMetricIdentifier(metricName)
    metric match {
      case x: Counter => counters += (fullName -> x)
        // TODO: apparently types are erased below. Works, but I am not sure how well
        // ask Carlo
        // Warning: non-variable type argument Number in type pattern org.apache.flink.metrics.Gauge[Number] is unchecked since it is eliminated by erasure:w
      case x: Gauge[Number] => gauges += (fullName -> x)
      case x: Histogram => histograms += (fullName -> x)
      case x: Meter => meters += (fullName -> x)
      case _ => LOG.warn("Could not add metric " + fullName + " type " + metric.asInstanceOf[AnyRef].getClass.getSimpleName + "-" + metric.getClass.getSimpleName + "-" + metric.getClass + "!")
    }
  }

  override def notifyOfRemovedMetric(metric: Metric, metricName: String, group: MetricGroup) = metric match {
    case x: Counter => counters -= metricName
    case x: Gauge[Number] => gauges -= metricName
    case x: Histogram => histograms -= metricName
    case x: Meter => meters -= metricName
    case _ => LOG.warn("Could not remove metric " + metricName)
  }

  override def close() = {
    LOG.info("Tearing down reporter")
  }

  override def open(config: MetricConfig) = {
    LOG.info("Initializing reporter")
  }
}

class FlinkMetricPusher extends Scheduled with FlinkMetricManager {
  // val configFile = getClass.getClassLoader.getResource("meraAkka/flinkPlugin.conf").getFile
  // val config = ConfigFactory.parseFile(new File(configFile))
  // TODO: Flink cannot find the config file in the resources :(.
  val config = ConfigFactory.parseFile(new File("/tmp/flinkPlugin.conf"))
  val actorSystem = ActorSystem("AkkaMetric", config)
  val master = actorSystem.actorSelection("akka.tcp://AkkaMetric@127.0.0.1:2552/user/master")

  override def report() = {
    // TODO: What happens when MetricServer is not up?
    val now : Long = System.currentTimeMillis()

    var ls : List[GaugeItem] = List()
    for (g <- gauges) {
      try {
        val gi = GaugeItem(g._1, g._2.getValue().longValue)
        ls = gi :: ls
      } catch {
        case e: ClassCastException => LOG.error(g._1 + " produced an error with the value " + g._2.getValue.toString)
      }
    }
    val d = MetricUpdate(now)
      .addAllCounters(counters.map(t => CounterItem(t._1, t._2.getCount)))
      .addAllMeters(meters.map(t => MeterItem(t._1, t._2.getCount, t._2.getRate())))
      .addAllHists(histograms.map(t => HistItem(t._1, t._2.getCount, t._2.getStatistics.getMin, t._2.getStatistics.getMax, t._2.getStatistics.getMean)))
      .addAllGauges(ls)

    master ! d
  }
  def printRemote(): Unit = {
    master ! "print"
  }
}

