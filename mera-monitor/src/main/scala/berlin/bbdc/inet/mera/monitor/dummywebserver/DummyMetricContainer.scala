package berlin.bbdc.inet.mera.monitor.dummywebserver

import berlin.bbdc.inet.mera.monitor.model.Model
import berlin.bbdc.inet.mera.monitor.webserver.MetricContainer

import scala.util.Random

class DummyMetricContainer(model: Model) extends MetricContainer(model) {

  /**
    * Obtains new values of a metric to be added to the history
    *
    * @return Map, where key is taskID and value is a Tuple(timestamp, metric_value)
    */
  override def collectNewValueOfMetric(metricKey: (String, String), resolution: Int): (Long, Double) = {
    val r = Random
    (System.currentTimeMillis(), r.nextDouble)
  }
}
