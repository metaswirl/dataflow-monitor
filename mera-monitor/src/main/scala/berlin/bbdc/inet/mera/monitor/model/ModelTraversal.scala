package berlin.bbdc.inet.mera.monitor.model

import berlin.bbdc.inet.mera.monitor.akkaserver.LoadShedderManager
import berlin.bbdc.inet.mera.monitor.metrics.MetricNotFoundException
import berlin.bbdc.inet.mera.monitor.optimizer.LoadShedderOptimizer
import org.slf4j.{Logger, LoggerFactory}
import com.typesafe.config.ConfigFactory

case class ModelTraversal(model: Model, mfw : ModelFileWriter) extends Runnable {
  val LOG: Logger = LoggerFactory.getLogger(getClass)
  private val queueAlmostEmpty : Double = ConfigFactory.load.getDouble("optimizer.queueAlmostEmpty")
  private val queueAlmostFull : Double = ConfigFactory.load.getDouble("optimizer.queueAlmostFull")
  private val penalty: Double = ConfigFactory.load.getDouble("optimizer.penalty")
  private val queueFull : Double = ConfigFactory.load.getDouble("optimizer.queueFull")
  private val beginTime = System.currentTimeMillis()
  private val initPhaseDuration = ConfigFactory.load.getDouble("optimizer.initPhaseDuration")
  private var active = ConfigFactory.load.getBoolean("optimizer.active")
  private val lPSolver = new LoadShedderOptimizer(model, mfw)
  private var initPhase = true

  def computeOutDist(task: Task): Int => Double = {
    def computeOutDistPerTask(sum: Double, outDistRaw: Map[Int, Double])(taskNumber: Int) : Double = {
      outDistRaw(taskNumber)/sum
    }
    var sum : Double = 0
    var outDistRaw : Map[Int, Double] = Map()
    for ((out, i) <- task.output.zipWithIndex) {
      val value = task.getMetricSummary(f"Network.Output.0.$i%d.buffersByChannel").getMean
      outDistRaw += out.target.number -> value
      sum += value
    }
    computeOutDistPerTask(sum, outDistRaw)
  }
  def computeInDist(task: Task): Int => Double = {
    def computeInDistPerTask(sum: Double, inDistRaw: Map[Int, Double])(taskNumber: Int) : Double = {
      inDistRaw(taskNumber)/sum
    }
    var sum : Double = 0
    var inDistRaw : Map[Int, Double] = Map()
    for (in <- task.input) {
      val inSource = in.source
      val key = if (inSource.parent.commType == CommType.POINTWISE) {
        f"Network.Output.0.${task.number}%d.buffersByChannel"
      } else {
        inSource.inDistCtr += 1
        f"Network.Output.0.${inSource.inDistCtr}%d.buffersByChannel"
      }
      val value = inSource.getMetricSummary(key).getMean
      inDistRaw += inSource.number -> value
      sum += value
    }
    computeInDistPerTask(sum, inDistRaw)
  }

  def computeInfMetrics(task: Task): Boolean = {
    val compInDistPerTask = computeInDist(task)
    val compOutDistPerTask = computeOutDist(task)
    for (in <- task.input) {
      in.inF = compInDistPerTask(in.source.number)
    }
    for (out <- task.output) {
      out.outF = compOutDistPerTask(out.target.number)
    }

    task.outQueueSaturation = task.getMetricSummary("buffers.outPoolUsage").getMean

    task.inQueueSaturation = {
      val iQS = task.getMetricSummary("buffers.inPoolUsage").getMean
      // when the input queue is colocated with the output queue, the input queue equals the output queue.
      val iQSlist = for ( in <- task.input if in.source.host == task.host )
        yield in.source.metrics("buffers.outPoolUsage").getMean
      (iQS::iQSlist).max
    }

    task.inRate = task.getMetricSummary("numRecordsInPerSecond").getMean
    task.outRate = task.getMetricSummary("numRecordsOutPerSecond").getMean
    task.selectivity = {
      if (task.parent.predecessor.isEmpty || task.parent.successor.isEmpty) 1.0
      else task.getMetricSummary("numRecordsOut").getMean/task.getMetricSummary("numRecordsIn").getMean
    }
    task.capacity = if (task.inQueueSaturation > queueAlmostFull) {
      task.inRate * penalty
    } else if (task.inQueueSaturation > queueAlmostEmpty && task.outQueueSaturation < queueAlmostFull) {
      task.inRate // already processing at maximum input rate
    } else if (task.capacity != 0.0 && task.capacity != Double.PositiveInfinity) {
      Math.max(task.capacity, task.inRate)
    } else {
      Double.PositiveInfinity // value unknown
    }
    true
  }

  def traverseModel() {
    model.tasks.values.foreach(_.inDistCtr = -1)
    try {
      model.tasks.values.foreach(computeInfMetrics)
    } catch {
      case ex: MetricNotFoundException =>
        LOG.error(ex.getMessage)
        return
      case ex: Exception =>
        LOG.error(ex.getMessage)
        return
    }
    // solve LP after initialization period
    if ((!active) || (initPhase && System.currentTimeMillis() - beginTime < initPhaseDuration)) {
      return
    } else if (initPhase) {
      initPhase = false
      active = lPSolver.canOptimize(model)
      if (!active) return
      mfw.writeStartOptimization()
      model.runtimeStatus.optimizerStarted = true
    }
    //TODO: this is just a test solution because optimizer is not ready yet
    LOG.debug("Send new values to all loadshedders")
    LoadShedderManager.sendTestValuesToAllLoadShedders()
    //    lPSolver.solveLP()
    lPSolver.optimize()
  }

  def cancel(): Unit = {
  }

  override def run(): Unit = {
    traverseModel()
  }
}




