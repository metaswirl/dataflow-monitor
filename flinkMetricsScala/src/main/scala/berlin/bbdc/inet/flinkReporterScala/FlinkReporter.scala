package berlin.bbdc.inet.flinkReporterScala

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
import akka.event.LoggingAdapter
import berlin.bbdc.inet.message.Data.{CounterItem, MeterItem}
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import berlin.bbdc.inet.message._

trait FlinkMetricManager extends MetricReporter {
  var counters = Map[String,Counter]()
//  val gauges = Map[String,Gauge]()
  var histograms = Map[String,Histogram]()
  var meters = Map[String,Meter]()

  val LOG : Logger = LoggerFactory.getLogger("FlinkReporter")

  override def notifyOfAddedMetric(metric: Metric, metricName: String, group: MetricGroup) = metric match {
    case x: Counter => counters += (metricName -> x)
    //case x: Gauge => gauges += gauges + (metricName -> x)
    case x: Histogram => histograms += (metricName -> x)
    case x: Meter => meters += (metricName -> x)
    case _ => LOG.warn("Could not add metric " + metricName)
  }

  override def notifyOfRemovedMetric(metric: Metric, metricName: String, group: MetricGroup) = metric match {
    case x: Counter => counters -= metricName
    //case x: Gauge => gauges -= metricName
    case x: Histogram => histograms -= metricName
    case x: Meter => meters -= metricName
    case _ => LOG.warn("Could not remove metric " + metricName)
  }

  override def close() = {
    LOG.warn("Tearing down reporter")
  }

  override def open(config: MetricConfig) = {
    LOG.warn("Initializing reporter")
  }
}

class FlinkMetricPusher extends Scheduled with FlinkMetricManager {
  val configFile = getClass.getClassLoader.getResource("akka-client.conf").getFile
  val config = ConfigFactory.parseFile(new File(configFile))
  val actorSystem = ActorSystem("AkkaMetric", config)
  val master = actorSystem.actorSelection("akka.tcp://AkkaMetric@127.0.0.1:2552/user/master")

  override def report() = {
    val now : Long = System.currentTimeMillis()

    val d = Data(now)
      .addAllCounters(counters.map(t => CounterItem(t._1, t._2.getCount)))
      .addAllMeters(meters.map(t => MeterItem(t._1, t._2.getCount, t._2.getRate())))
    master ! d
  }
}

