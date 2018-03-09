package berlin.bbdc.inet.metricServer

import java.io.File

import akka.actor.{Actor, ActorSystem, Props}
import berlin.bbdc.inet.message.Data
import com.typesafe.config.ConfigFactory

sealed trait Metric
case class Counter(key : String, count: Long) extends Metric
case class Meter(key : String, count : Long, rate : Double) extends Metric


class Receiver extends Actor {
  var counters = List[Counter]()
  var meters = List[Meter]()

  override def receive = {
    case d: Data => {
      println(d.timestamp)
      d.counters.foreach(println(_))
      d.meters.foreach(println(_))
      counters = counters ++ d.counters.map(t => Counter(t.key, t.count)).toList
      meters = meters ++ d.meters.map(t => Meter(t.key, t.count, t.rate)).toList
      println(counters)
      println(meters)
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
