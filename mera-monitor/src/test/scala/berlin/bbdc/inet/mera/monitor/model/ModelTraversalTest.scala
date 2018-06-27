package berlin.bbdc.inet.mera.monitor.model

import berlin.bbdc.inet.mera.monitor.metrics.{Meter, MeterSummary, MetricNames}
import org.specs2.mock.Mockito
import org.specs2.mutable.{Before, Specification}

class ModelTraversalTest extends Specification with Mockito {

  // build model
  val modelBuilder = new ModelBuilder()
  modelBuilder.addSuccessor("dummyOpLeft", 3, CommType.ALL_TO_ALL, false)
  modelBuilder.addSuccessor("dummyOp", 1, CommType.ALL_TO_ALL, false)
  modelBuilder.addSuccessor("dummyOpRight", 2, CommType.ALL_TO_ALL, false)
  val model = modelBuilder.createModel(1)
  val modelTraversal = new ModelTraversal(model, mock[ModelFileWriter])

  "ModelTraversal" should {
    "computeOutDist" in {
      // extract task
      val op = model.operators.tail.head._2
      val task : Task = op.tasks.head

      // add metrics
      val ms1 = new MeterSummary(1)
      ms1.add(0L, new Meter(1, 5))
      val ms2 = new MeterSummary(1)
      ms2.add(0L, new Meter(1, 5))
      task.metrics += MetricNames.buffersByChannel(0) -> ms1
      task.metrics += MetricNames.buffersByChannel(1) -> ms2

      // compute result
      val result = modelTraversal.computeOutDist(task)
      result(0) must_== 0.5
      result(1) must_== 0.5
    }
    "computeInDist" in {
      // add metrics to first operator
      val ms1 = new MeterSummary(1)
      ms1.add(0L, new Meter(1, 50))
      val ms2 = new MeterSummary(1)
      ms2.add(0L, new Meter(1, 25))
      val ms3 = new MeterSummary(1)
      ms3.add(0L, new Meter(1, 25))

      val firstOp = model.operators.toList.reverse.head._2
      firstOp.tasks(0).metrics += MetricNames.buffersByChannel(0) -> ms1
      firstOp.tasks(1).metrics += MetricNames.buffersByChannel(0) -> ms2
      firstOp.tasks(2).metrics += MetricNames.buffersByChannel(0) -> ms3

      // extract task
      val op = model.operators.tail.head._2
      val task : Task = op.tasks.head

      println("A")
      task.input.foreach(println(_))
      println("B")

      // compute result
      val result = modelTraversal.computeInDist(task)
      result(0) must_== 0.5
      result(1) must_== 0.25
      result(2) must_== 0.25
    }
  }
}

