package berlin.bbdc.inet.mera.server.model

import berlin.bbdc.inet.mera.server.metrics.MetricNotFoundException
import org.slf4j.{Logger, LoggerFactory}

class ModelTraversal(val model: Model, val mfw : ModelFileWriter) extends Runnable {
  val LOG: Logger = LoggerFactory.getLogger("Model")
  private val queueAlmostEmpty : Double = 0.2
  private val queueAlmostFull : Double = 0.8
  private val queueFull : Double = 1.0

  def computeInfMetrics(task: Task): Boolean = {
    task.outDist = {
      var sum : Double = 0
      var outDistRaw : Map[Int, Double] = Map()
      for ((out, i) <- task.output.zipWithIndex) {
        val value = task.gauges(f"Network.Output.0.$i%d.buffersByChannel").getMean
        outDistRaw += out.number -> value
        sum += value
      }
      outDistRaw.map{case (k, v) => k -> v/sum}
    }

    task.inDist = {
      var sum : Double = 0
      var inDistRaw : Map[Int, Double] = Map()
      for (in <- task.input) {
        val key = if (in.parent.commType == CommType.Grouped) {
          f"Network.Output.0.${task.number}%d.buffersByChannel"
        } else {
          in.inDistCtr += 1
          f"Network.Output.0.${in.inDistCtr}%d.buffersByChannel"
        }
        val value = in.gauges(key).getMean
        inDistRaw += in.number -> value
        sum += value
      }
      inDistRaw.map{case (k, v) => k -> v/sum }
    }

    task.outQueueSaturation = task.gauges("buffers.outPoolUsage").getMean

    // when the input queue is colocated with the output queue, the input queue equals the output queue.
    task.inQueueSaturation = {
      val iQS = task.gauges("buffers.inPoolUsage").getMean
      val iQSlist = task.input.filter(_.host == task.host).map(_.gauges("buffers.outPoolUsage").getMean)
      (iQS::iQSlist).max
    }

    task.inRate = task.meters("numRecordsInPerSecond").getMean
    task.selectivity = task.counters("numRecordsOut").getMean/task.counters("numRecordsIn").getMean
    task.capacity = if (task.inQueueSaturation > queueAlmostFull) {
      task.inRate * (1 - 0.2 * (task.inQueueSaturation - queueAlmostFull)/(queueFull - queueAlmostFull)) // peanlize bottleneck
    } else if (task.inQueueSaturation > queueAlmostEmpty && task.outQueueSaturation > queueAlmostFull) {
      task.inRate // already processing at maximum input rate
    } else {
      Double.PositiveInfinity // value unknown
    }
    true
  }
  def computeTargets(task: Task) : Boolean = {
    // targetInputRate
    task.targetInRate = if (task.output.isEmpty) {
      task.capacity
    } else {
      task.targetOutRate = task.targetPartialOutRate.map(x => {
        x._2 * task.outDist(x._1)
      }).min
      Math.min(task.targetOutRate/task.selectivity, task.capacity)
    }

    // targetPartialOutRate of inputs
    for (i <- task.input) {
      i.targetPartialOutRate += task.number -> task.inDist(i.number) * task.targetInRate
    }
    true
  }
  def traverseModel() {
    LOG.info("Traversing model")
    model.tasks.foreach(_.inDistCtr = -1)
    try {
      model.tasks.foreach(computeInfMetrics)
    } catch {
      case ex: MetricNotFoundException => {
        LOG.error(ex.msg)
        return
      }
    }
    mfw.updateInferredMetrics(model)

    def traverseOp(op: Operator) : Unit = {
      op.tasks.foreach(computeTargets)
      if (op.predecessor.isEmpty) return
      traverseOp(op.predecessor.head)
    }
    traverseOp(model.sink)
    mfw.updateTargetMetrics(model)
  }

  override def run(): Unit = {
    traverseModel()
  }
}


