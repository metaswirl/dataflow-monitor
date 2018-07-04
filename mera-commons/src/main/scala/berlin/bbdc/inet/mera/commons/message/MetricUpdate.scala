package berlin.bbdc.inet.mera.commons.message

final case class MetricUpdate(timestamp: Long,
                              counters: List[CounterItem],
                              meters: List[MeterItem],
                              hists: List[HistItem],
                              gauges: List[GaugeItem])

final case class CounterItem(key: String, count: Long)

final case class MeterItem(key: String, count: Long, rate: Double)

final case class HistItem(key: String, count: Long, min: Long, max: Long, mean: Double)

final case class GaugeItem(key: String, value: Double)
