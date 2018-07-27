package berlin.bbdc.inet.mera.monitor.metrics

import org.specs2.mutable.Specification


class MetricTest extends Specification {

  "CounterSummary" should {
    val summary = new CounterSummary(5)

    "store the right number of records in its history by removing the old ones" in {
      for (i <- 0 to 10)
        summary.add(i, Counter(i))

      for (i <- 0 to 4) {
        val (ts: Long, counter: Counter) = summary.history(i)
        ts mustEqual 10-i
        counter.value mustEqual 10-i
      }

      summary.history.length mustEqual 5
    }
  }

}
