package berlin.bbdc.inet.mera.server.metrics

import java.util.concurrent.{Executors, ScheduledExecutorService, ScheduledFuture, TimeUnit}

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import berlin.bbdc.inet.mera.message.MetricUpdate
import berlin.bbdc.inet.mera.server.model._
import com.typesafe.config.ConfigFactory
import org.slf4j.{Logger, LoggerFactory}

class MetricReceiver(model: Model, mfw : ModelFileWriter) extends Actor {
  mfw.writeGraph(model)
  val modelUpdater = new ModelUpdater(model)
  val LOG: Logger = LoggerFactory.getLogger("MetricReceiver")
  var first = true
  val modelTraversal = new ModelTraversal(model, mfw)

  var traversalFuture : ScheduledFuture[_] = _

  override def receive: PartialFunction[Any, Unit] = {
    case d: MetricUpdate =>
      mfw.updateMetrics(d.timestamp, d.counters.map(t => (t.key, t.count.toDouble)) ++
        d.meters.map(t => (t.key, t.rate)) ++ d.gauges.map(t => (t.key, t.value)))
      modelUpdater.update(d.timestamp,
        d.counters.map(t => (MetricKey.buildKey(t.key), Counter(t.count))).toList ++
          d.meters.map(t => (MetricKey.buildKey(t.key), Meter(t.count, t.rate))).toList ++
          d.hists.map(t => (MetricKey.buildKey(t.key), Histogram(t.count, t.min, t.max, t.mean))).toList ++
          d.gauges.map(t => (MetricKey.buildKey(t.key), Gauge(t.value))).toList
      )
      if (first) {
        first = false
        LOG.info("Started receiving metrics. Starting model traversal.")
        // Start tra
        val schd : ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
        traversalFuture = schd.scheduleAtFixedRate(modelTraversal, 5, 3, TimeUnit.SECONDS)
      }
  }

  override def postStop(): Unit = {
    modelTraversal.cancel()
    mfw.close()
    traversalFuture.cancel(true)
    super.postStop()
  }
}

object MetricReceiver {
  def start(model : Model, mfw : ModelFileWriter): Unit = {
    val config = ConfigFactory.load("meraAkka/metricServer.conf")
    val actorSystem = ActorSystem("AkkaMetric", config)
    val remote: ActorRef = actorSystem.actorOf(Props(new MetricReceiver(model, mfw)), name="master")
  }
}

