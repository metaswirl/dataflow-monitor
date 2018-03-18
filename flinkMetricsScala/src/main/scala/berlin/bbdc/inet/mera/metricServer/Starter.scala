package berlin.bbdc.inet.mera.metricServer

object Starter {
  def main(args: Array[String]): Unit = {
    var model : Model = new Model(1000)
    MetricReceiver.start(model)
  }
}

