package berlin.bbdc.inet.metricServer

import java.io.File

import akka.actor.{Actor, ActorSystem, Props}
import berlin.bbdc.inet.message.Data
import com.typesafe.config.ConfigFactory

sealed trait Metric
case class Counter(key : String, count: Long) extends Metric
case class Meter(key : String, count : Long, rate : Double) extends Metric
case class Histogram(key : String, count : Long, min : Long, max : Long, mean : Double) extends Metric
case class Gauge(key : String, value: Long) extends Metric

class Receiver extends Actor {
  var counters = List[Counter]()
  var meters = List[Meter]()
  var hists = List[Histogram]()
  var gauges = List[Gauge]()

  override def receive = {
    case d: Data => {
      println(d.timestamp)
      d.counters.foreach(println(_))
      d.meters.foreach(println(_))
      counters = counters ++ d.counters.map(t => Counter(t.key, t.count)).toList
      meters = meters ++ d.meters.map(t => Meter(t.key, t.count, t.rate)).toList
      gauges = gauges ++ d.gauges.map(t => Gauge(t.key, t.value)).toList
      hists = hists ++ d.hists.map(t => Histogram(t.key, t.count, t.min, t.max, t.mean)).toList
      println(counters)
      println(meters)
      println(gauges)
      println(hists)
    }
  }
}

object Server {
  def main(args: Array[String]): Unit = {
    val configFile = getClass.getClassLoader.getResource("akka-server.conf").getFile
    val config = ConfigFactory.parseFile(new File(configFile))
    val actorSystem = ActorSystem("AkkaMetric", config)
    val remote = actorSystem.actorOf(Props[Receiver], name="master")
  }
}
