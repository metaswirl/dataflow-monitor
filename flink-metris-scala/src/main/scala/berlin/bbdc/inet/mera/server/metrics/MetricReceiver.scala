package berlin.bbdc.inet.mera.server.metrics

import java.io.File

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import akka.actor.{Actor, ActorSystem, Props}
import berlin.bbdc.inet.mera.message.MetricUpdate
import berlin.bbdc.inet.mera.server.model.{Model, ModelFileWriter, Task}
import com.typesafe.config.ConfigFactory

class ModelUpdater(val model: Model) {
  // TODO: fix logging
  val LOG: Logger = LoggerFactory.getLogger("Model")

  def update(timestamp : Long, metrics: List[(MetricKey, MetricSummary.NumberMetric)]): Unit = metrics.foreach({
    // TODO: Schedule after first metric has arrived
    case (k: TaskManagerTaskMetricKey, m: MetricSummary.NumberMetric) =>
      // TODO: fix hack
      // apparently even though two operators are merged in the same task chain, they still get different metric readings.
      // This requires further inquiry into how the task chain operates. Is there a buffer between the operators?
      var t : Task = null
      try {
        t = model.operators(k.opId)(k.taskId)
      } catch {
        case ex: NoSuchElementException  => {
          LOG.error("Exception operator '" + k.opId + "' not found!")
          return
        }
        case ex: IndexOutOfBoundsException => {
          LOG.error("Exception task '" + k.opId + "-" + k.taskId + "' not found!")
          return
        }
      }

      m match {
        case x: Gauge =>
          val gs: GaugeSummary = t.gauges.getOrElse(k.metric, new GaugeSummary(model.n))
          gs.add(timestamp, x)
          t.gauges += (k.metric -> gs)
        case x: Counter =>
          val cs : CounterSummary = t.counters.getOrElse(k.metric, new CounterSummary(model.n))
          cs.add(timestamp, x)
          t.counters += (k.metric -> cs)
        case x: Meter =>
          val ms : MeterSummary = t.meters.getOrElse(k.metric, new MeterSummary(model.n))
          ms.add(timestamp, x)
          t.meters += (k.metric -> ms)
      }
    case (k: UnknownMetricKey, _) => LOG.warn("Could not parse key: " + k.rawKey)
    case (jk: JobManagerMetricKey, _) =>
    case (tsk: TaskManagerStatusMetricKey, _) =>
    case (k: MetricKey, m: MetricSummary.NumberMetric) => LOG.warn("Ignoring key: " + k + "-" + m)
  })
}

class MetricReceiver(model: Model, mfw : ModelFileWriter) extends Actor {
  val modelUpdater = new ModelUpdater(model)
  val LOG: Logger = LoggerFactory.getLogger("MetricReceiver")
  var first = true

  override def receive = {
    case d: MetricUpdate => {
      if (first) {
        first = false
        LOG.info("Started receiving metrics")
      }
      mfw.updateMetrics(d.timestamp, d.counters.map(t => (t.key, t.count.toDouble)) ++
        d.meters.map(t => (t.key, t.rate)) ++ d.gauges.map(t => (t.key, t.value)))
      modelUpdater.update(d.timestamp,
        d.counters.map(t => (MetricKey.buildKey(t.key), Counter(t.count))).toList ++
          d.meters.map(t => (MetricKey.buildKey(t.key), Meter(t.count, t.rate))).toList ++
          d.hists.map(t => (MetricKey.buildKey(t.key), Histogram(t.count, t.min, t.max, t.mean))).toList ++
          d.gauges.map(t => (MetricKey.buildKey(t.key), Gauge(t.value))).toList
      )
    }
  }
}

object MetricReceiver {
  def start(model : Model, mfw : ModelFileWriter): Unit = {
    val configFile = getClass.getClassLoader.getResource("meraAkka/metricServer.conf").getFile
    val config = ConfigFactory.parseFile(new File(configFile))
    val actorSystem = ActorSystem("AkkaMetric", config)
    val remote = actorSystem.actorOf(Props(new MetricReceiver(model, mfw)), name="master")
  }
}
