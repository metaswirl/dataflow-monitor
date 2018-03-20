package berlin.bbdc.inet.mera.server.model

import berlin.bbdc.inet.mera.server.metrics.MetricNotFoundException
import org.slf4j.{Logger, LoggerFactory}

class ModelTraversal(val model: Model, val mfw : ModelFileWriter) {
  // TODO: fix logging
  val LOG: Logger = LoggerFactory.getLogger("Model")
  private val queueAlmostEmpty : Double = 0.2
  private val queueAlmostFull : Double = 0.8
  private val queueFull : Double = 1.0

  def computeInfMetrics(task: Task): Boolean = {
    // TODO: move key checks and exceptions into the data structures
    // TODO: return an error string
    task.inRate = task.meters("numRecordsInPerSecond").getMean
    var sum : Double = 0
    var outDistRaw : Map[Int, Double] = Map()
    for ((out, i) <- task.output.zipWithIndex) {
      val key = f"Network.Output.0.$i%d.buffersByChannel"
      if (!task.gauges.contains(key)) {
        throw new MetricNotFoundException(f"Could not find $key for ${task.parent.id}-${task.subtask}")
      }
      val value = task.gauges(key).getMean
      outDistRaw += out.subtask -> value
      sum += value
    }
    for ((k, v) <- outDistRaw) task.outDist += k -> v/sum

    sum = 0
    var inDistRaw : Map[Int, Double] = Map()
    for ((in, i) <- task.input.zipWithIndex) {
      val key = if (in.parent.commType == CommType.Grouped) {
        f"Network.Output.0.${task.subtask}%d.buffersByChannel"
      } else {
        in.inDistCtr += 1
        f"Network.Output.0.${in.inDistCtr}%d.buffersByChannel"
      }
      if (!in.gauges.contains(key)) {
        throw new MetricNotFoundException(f"Could not find $key for ${in.parent.id}-${in.subtask}")
      }
      val value = in.gauges(key).getMean
      inDistRaw += in.subtask -> value
      sum += value
    }
    for ((k, v) <- inDistRaw) {
      task.inDist += k -> v/sum
    }

    if (!task.gauges.contains("buffers.inPoolUsage")) {
      throw MetricNotFoundException(f"Could not find buffers.inPoolUsage for ${task.parent.id}-${task.subtask}")
    } else if (!task.gauges.contains("buffers.outPoolUsage")) {
      throw MetricNotFoundException(f"Could not find buffers.outPoolUsage for ${task.parent.id}-${task.subtask}")
    }

    task.outQueueSaturation = task.gauges("buffers.outPoolUsage").getMean
    val iQS = task.gauges("buffers.inPoolUsage").getMean
    // when the input queue is colocated with the output queue, the input queue equals the output queue.
    try {
      val iQSlist = iQS :: task.input.filter(_.host == task.host)
        .map(_.gauges("buffers.outPoolUsage").getMean)
      task.inQueueSaturation = iQSlist.max
    } catch {
      case _:java.util.NoSuchElementException => throw MetricNotFoundException(f"Could not find buffers.outPoolUsage in some input of ${task.parent.id}-${task.subtask}")
    }

    // rate
    if (!task.counters.contains("numRecordsOut")) {
      throw MetricNotFoundException(f"Could not find numRecordsOut for ${task.parent.id}-${task.subtask}")
    }
    if (!task.counters.contains("numRecordsIn")) {
      throw MetricNotFoundException(f"Could not find numRecordsIn for ${task.parent.id}-${task.subtask}")
    }
    val tmpOut = task.counters("numRecordsOut")
    val tmpIn = task.counters("numRecordsIn")

    task.selectivity = tmpOut.getMean/tmpIn.getMean
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
//        if (!task.outDist.contains(x._1)) {
//          return false
//        }
        x._2 * task.outDist(x._1)
      }).min
      Math.min(task.targetOutRate/task.selectivity, task.capacity)
    }

    // targetPartialOutRate of inputs
    for (i <- task.input) {
//      if (!task.inDist.contains(i.subtask)) {
//        return false
//      }
      i.targetPartialOutRate += task.subtask -> task.inDist(i.subtask) * task.targetInRate
    }
    true
  }
  def traverseModel() {
    if (model.tasks.map(_.gauges.isEmpty).foldLeft(true)(_ && _)) {
      LOG.info("Waiting for metrics")
      return
    }
    model.tasks.foreach(_.inDistCtr = -1)
    LOG.info("Traversing model")
    try {
      model.tasks.foreach(computeInfMetrics)
    } catch {
      case ex: MetricNotFoundException => {
        LOG.error(ex.msg)
        return
      }
    }
    mfw.updateInferredMetrics(model)

    // TODO: this looks so un-idiomatic. But then again, I would like to keep using these pointers.
    var op = model.sink
    var condition = true
    while (condition) {
      op.tasks.foreach(computeTargets)
      if (op.predecessor.nonEmpty) {
        op = op.predecessor.head
      } else {
        condition = false
      }
    }
    mfw.updateTargetMetrics(model)
  }
}


