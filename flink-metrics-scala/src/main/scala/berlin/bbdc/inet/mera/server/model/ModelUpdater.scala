package berlin.bbdc.inet.mera.server.model

import berlin.bbdc.inet.mera.server.metrics._
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ModelUpdater(val model: Model) {
  val LOG: Logger = LoggerFactory.getLogger("Model")

  def update(timestamp : Long, metrics: List[(MetricKey, MetricSummary.NumberMetric)]): Unit = metrics.foreach({
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

