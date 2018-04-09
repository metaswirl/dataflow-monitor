package berlin.bbdc.inet.mera.server.model

import berlin.bbdc.inet.mera.server.metrics.{CounterSummary, GaugeSummary, MeterSummary}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

class ModelTest extends Specification with Mockito {

  "Task" should {
    "create map correctly" in {
      val operator = mock[Operator]
      val counterSummary = mock[CounterSummary]
      val gaugeSummary = mock[GaugeSummary]
      val meterSummary = mock[MeterSummary]
      operator.id returns "op_id"

      val task = new Task(operator, 100, "localhost")
      task.metrics += ("counter" -> counterSummary)
      task.metrics += ("gauge" -> gaugeSummary)
      task.metrics += ("meter" -> meterSummary)

      task.metrics("counter").isInstanceOf[CounterSummary] mustEqual true
      task.metrics("gauge").isInstanceOf[GaugeSummary] mustEqual true
      task.metrics("meter").isInstanceOf[MeterSummary] mustEqual true

    }
  }
}
