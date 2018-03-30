package berlin.bbdc.inet.mera.flinkPlugin

import java.io.{BufferedReader, File, FileReader, PrintWriter}

import reflect.runtime.universe._
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

trait FlinkMetricManager extends MetricReporter {
  var counters : Map[String,Counter] = Map()
  var gauges : Map[String,Gauge[Number]] = Map()
  var histograms : Map[String,Histogram] = Map()
  var meters : Map[String,Meter] = Map()
  var metricFilter : Option[String => Boolean] = None

  val LOG : Logger = LoggerFactory.getLogger("MeraFlinkPlugin")

  def setMetricFilter(f : String => Boolean) = { metricFilter = Some(f) }

  override def notifyOfAddedMetric(metric: Metric, metricName: String, group: MetricGroup): Unit = {
    val fullName : String = group.getMetricIdentifier(metricName)
    if (!metricFilter.map(_(fullName)).getOrElse(true)) {
      return
    }
    try {
      metric match {
        case x: Counter => counters += (fullName -> x)
          // TODO: throws a warning about type erasure, cannot remove as it then throws an error
          // Warning: non-variable type argument Number in type pattern org.apache.flink.metrics.Gauge[Number] is unchecked since it is eliminated by erasure:w
        case x: Gauge[Number] =>
            x.getValue match {
              case _: Number => gauges += (fullName -> x)
              case _ => LOG.warn("Did not register metric " + fullName)
            }
        case x: Histogram => histograms += (fullName -> x)
        case x: Meter => meters += (fullName -> x)
        case _ => LOG.warn("Could not add metric " + fullName + " type " + metric.asInstanceOf[AnyRef].getClass.getSimpleName + "-" + metric.getClass.getSimpleName + "-" + metric.getClass + "!")
      }
    } catch {
      case _: java.lang.ClassCastException => ( LOG.warn("Ignoring metric " + fullName))
    }
  }

  override def notifyOfRemovedMetric(metric: Metric, metricName: String, group: MetricGroup): Unit = {
    val fullName: String = group.getMetricIdentifier(metricName)
    if (metricFilter.map(_ (fullName)).getOrElse(true)) {
      return
    }
    metric match {
      case x: Counter => counters -= fullName
      case x: Gauge[_] => gauges -= fullName
      case x: Histogram => histograms -= fullName
      case x: Meter => meters -= fullName
      case _ => LOG.warn("Could not remove metric " + fullName)
    }
    LOG.info("Removed " + fullName)
  }
}

class FlinkMetricPusher() extends Scheduled with FlinkMetricManager {
  /* works in the test case, but not in deployment
     val configFile = getClass.getResource("/meraAkka/flinkPlugin.conf").getFile
     val config = ConfigFactory.parseFile(new File(configFile))

     y prints fine, but config2 is empty
     val x = getClass.getResourceAsStream("/meraAkka/flinkPlugin.conf")
     val y = scala.io.Source.fromInputStream( x )
     val config2 = ConfigFactory.parseString(y.getLines.mkString)

     Works but is annoying and requires files to be deployed to all hosts
     val config = ConfigFactory.parseFile(new File("/tmp/flinkPlugin.conf"))
  */
  val config = ConfigFactory.parseString("""
    akka {
      actor {
        provider = "akka.remote.RemoteActorRefProvider"
      }
      remote {
        enabled-transports = ["akka.remote.netty.tcp"]
        netty.tcp {
          hostname = "127.0.0.1"
          port = 0
        }
      }
    }""")
  val actorSystem = ActorSystem("AkkaMetric", config)
  val master = actorSystem.actorSelection("akka.tcp://AkkaMetric@127.0.0.1:2552/user/master")

  override def open(config: MetricConfig) = {
    LOG.info("Initializing reporter")
    this.setMetricFilter((x: String) => (!x.contains("jobmanager")) &&
      (!( x.contains(".taskmanager.") &&
        ( x.contains(".Status.") || x.contains(".JVM.") )
      ))
    )
  }

  override def close() = {
    LOG.info("Tearing down reporter")
  }

  def report() = {
    // TODO: What happens when MetricServer is not up? Are messages cached or not dropped?
    var ls : List[GaugeItem] = List()
    for (g <- gauges) {
      try {
        val gi = GaugeItem(g._1, g._2.getValue().doubleValue())
        ls = gi :: ls
      } catch {
        case _: ClassCastException => LOG.error(g._1 + " produced an error with the value " + g._2.getValue.toString)
      }
    }
    val d = MetricUpdate(System.currentTimeMillis())
      .addAllCounters(counters.map(t => CounterItem(t._1, t._2.getCount)))
      .addAllMeters(meters.map(t => MeterItem(t._1, t._2.getCount, t._2.getRate())))
      .addAllHists(histograms.map(t => HistItem(t._1, t._2.getCount, t._2.getStatistics.getMin, t._2.getStatistics.getMax, t._2.getStatistics.getMean)))
      .addAllGauges(ls)

    master ! d
  }
}

