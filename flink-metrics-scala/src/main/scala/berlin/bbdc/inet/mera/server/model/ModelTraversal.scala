package berlin.bbdc.inet.mera.server.model

import berlin.bbdc.inet.mera.server.metrics.MetricNotFoundException
import org.slf4j.{Logger, LoggerFactory}

class ModelTraversal(val model: Model, val mfw : ModelFileWriter) extends Runnable {
  val LOG: Logger = LoggerFactory.getLogger("Model")
  private val queueAlmostEmpty : Double = 0.2
  private val queueAlmostFull : Double = 0.8
  private val queueFull : Double = 1.0

  def computeInfMetrics(task: Task): Boolean = {
    def computeOutDist() {
      var sum : Double = 0
      var outDistRaw : Map[Int, Double] = Map()
      for ((out, i) <- task.output.zipWithIndex) {
        val value = task.getGaugeSummary(f"Network.Output.0.$i%d.buffersByChannel").getMean
        outDistRaw += out.target.number -> value
        sum += value
      }
      for (te <- task.output) {
        te.outF = outDistRaw(te.target.number)/sum
      }
    }
    def computeInDist(): Unit = {
      var sum : Double = 0
      var inDistRaw : Map[Int, Double] = Map()
      for (in <- task.input) {
        val key = if (in.source.parent.commType == CommType.POINTWISE) {
          f"Network.Output.0.${task.number}%d.buffersByChannel"
        } else {
          in.source.inDistCtr += 1
          f"Network.Output.0.${in.source.inDistCtr}%d.buffersByChannel"
        }
        val value = in.source.getGaugeSummary(key).getMean
        inDistRaw += in.source.number -> value
        sum += value
      }
      for (in <- task.input) {
        in.inF = inDistRaw(in.source.number)/sum
      }
    }

    computeOutDist()
    computeInDist()

    task.outQueueSaturation = task.getGaugeSummary("buffers.outPoolUsage").getMean

    // when the input queue is colocated with the output queue, the input queue equals the output queue.
    task.inQueueSaturation = {
      val iQS = task.getGaugeSummary("buffers.inPoolUsage").getMean
      val iQSlist = for ( in <- task.input if in.source.host == task.host )
        yield in.source.gauges("buffers.outPoolUsage").getMean
      (iQS::iQSlist).max
    }

    task.inRate = task.getMeterSummary("numRecordsInPerSecond").getMean
    task.selectivity = task.getCounterSummary("numRecordsOut").getMean/task.getCounterSummary("numRecordsIn").getMean
    task.capacity = if (task.inQueueSaturation > queueAlmostFull) {
      task.inRate * (1 - 0.2 * (task.inQueueSaturation - queueAlmostFull)/(queueFull - queueAlmostFull)) // peanlize bottleneck
    } else if (task.inQueueSaturation > queueAlmostEmpty && task.outQueueSaturation > queueAlmostFull) {
      task.inRate // already processing at maximum input rate
    } else {
      Double.PositiveInfinity // value unknown
    }
    true
  }
//  def computeTargets(task: Task) : Boolean = {
//    // targetInputRate
//    task.targetInRate = if (task.output.isEmpty) {
//      task.capacity
//    } else {
//      task.targetOutRate = task.targetPartialOutRate.map(x => {
//        x._2 * task.outDist(x._1)
//      }).min
//      Math.min(task.targetOutRate/task.selectivity, task.capacity)
//    }
//
//    // targetPartialOutRate of inputs
//    for (in <- task.input) {
//      in.source.targetPartialOutRate += task.number -> task.inDist(in.source.number) * task.targetInRate
//    }
//    true
//  }
  def traverseModel() {
    LOG.info("Traversing model")
    model.tasks.foreach(_.inDistCtr = -1)
    try {
      model.tasks.foreach(computeInfMetrics)
    } catch {
      case ex: MetricNotFoundException =>
        LOG.error(ex.msg)
        return
      case ex: Exception =>
        LOG.error(ex.getMessage)
        return
    }
    mfw.updateInferredMetrics(model)

//    def traverseOp(op: Operator) : Unit = {
//      op.tasks.foreach(computeTargets)
//      if (op.predecessor.isEmpty) return
//      traverseOp(op.predecessor.head)
//    }
//    traverseOp(model.sink)
//    mfw.updateTargetMetrics(model)
  }

  override def run(): Unit = {
    traverseModel()
  }
}


