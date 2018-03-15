package berlin.bbdc.inet.mera.metricServer

import java.io.File

import akka.actor.{Actor, ActorSystem, Props}
import berlin.bbdc.inet.mera.message.MetricUpdate
import com.typesafe.config.ConfigFactory


class Receiver extends Actor {
  var model : Model = new Model(11)

  override def receive = {
    case d: MetricUpdate => {
      print(".")
      model.update(d.timestamp, d.counters.map(t => (Key.buildKey(t.key), Counter(t.count))).toList ++
                   d.meters.map(t => (Key.buildKey(t.key), Meter(t.count, t.rate))).toList ++
                   d.hists.map(t => (Key.buildKey(t.key), Histogram(t.count, t.min, t.max, t.mean))).toList ++
                   d.gauges.map(t => (Key.buildKey(t.key), Gauge(t.value))).toList)
    }
    // TODO: return result
    case x: String if x == "graph" => println(model.printGraph())
    case x: String if x == "keys" => println(model.printKeys())
    case (x: String, op: String, st: Int) if x == "metrics" =>  println(model.printMetrics(op, st))
    case (x: String, op: String, st: Int) if x == "infMetrics" => println(model.printInfMetrics(op, st))
  }
}

object Server {
  def main(args: Array[String]): Unit = {
    val configFile = getClass.getClassLoader.getResource("meraAkka/metricServer.conf").getFile
    val config = ConfigFactory.parseFile(new File(configFile))
    val actorSystem = ActorSystem("AkkaMetric", config)
    val remote = actorSystem.actorOf(Props[Receiver], name="master")
  }
}
