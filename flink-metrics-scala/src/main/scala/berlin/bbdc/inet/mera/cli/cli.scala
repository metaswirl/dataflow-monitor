package berlin.bbdc.inet.mera.cli

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
import java.util.concurrent.TimeUnit

class CLI {
  val configFile = getClass.getClassLoader.getResource("meraAkka/flinkPlugin.conf").getFile
  val config = ConfigFactory.parseFile(new File(configFile))
  val actorSystem = ActorSystem("AkkaMetric", config)
  val master = actorSystem.actorSelection("akka.tcp://AkkaMetric@127.0.0.1:2552/user/master")

  def printGraph(): Unit = {
    master ! "graph"
  }
  def printMetrics(op: String, task: Int): Unit = {
    println((op, task))
    master ! ("metrics", op, task)
  }
  def printInfMetrics(op: String, task: Int): Unit = {
    println((op, task))
    master ! ("infMetrics", op, task)
  }
  def printKeys() = {
    master ! "keys"
  }
  def shutdown = actorSystem.shutdown
}

object TriggerPrintGraph {
  def main(args: Array[String]): Unit = {
    val cli = new CLI()
    cli.printGraph()
    Thread.sleep(3000)
    cli.shutdown
  }
}
object TriggerPrintMetrics {
  def main(args: Array[String]): Unit = {
    val cli = new CLI()
    cli.printMetrics(args.slice(1,args.length).mkString(" "), args(0).toInt)
    Thread.sleep(3000)
    cli.shutdown

  }
}
object TriggerPrintInfMetrics {
  def main(args: Array[String]): Unit = {
    val cli = new CLI()
    cli.printInfMetrics(args.slice(1,args.length).mkString(" "), args(0).toInt)
    Thread.sleep(3000)
    cli.shutdown
  }
}
object TriggerPrintKeys {
  def main(args: Array[String]): Unit = {
    val cli = new CLI()
    cli.printKeys()
    Thread.sleep(3000)
    cli.shutdown
  }
}
