package berlin.bbdc.inet.mera.monitor.model

import berlin.bbdc.inet.mera.monitor.akkaserver.LoadShedderManager
import berlin.bbdc.inet.mera.monitor.metrics.{MetricNames, MetricNotFoundException, SimpleMeter, SimpleMeterSummary}
import berlin.bbdc.inet.mera.monitor.optimizer.LoadShedderOptimizer
import org.slf4j.{Logger, LoggerFactory}
import com.typesafe.config.ConfigFactory

/** At the moment this class is an ugly child of mother graph traversal and father metric computation.
  *
  * We want to split this freak into both parents
  *
  * Or at least rename it
  *
  * @param model
  * @param mfw
  */
case class ModelTraversal(model: Model, mfw : ModelFileWriter) extends Runnable {
  val LOG: Logger = LoggerFactory.getLogger(getClass)
  private val queueAlmostEmpty : Double = ConfigFactory.load.getDouble("optimizer.queueAlmostEmpty")
  private val queueAlmostFull : Double = ConfigFactory.load.getDouble("optimizer.queueAlmostFull")
  private val penalty: Double = ConfigFactory.load.getDouble("optimizer.penalty")
  private val queueFull : Double = ConfigFactory.load.getDouble("optimizer.queueFull")
  private val beginTime = System.currentTimeMillis()
  private val initPhaseDuration = ConfigFactory.load.getDouble("optimizer.initPhaseDuration")
  private var active = ConfigFactory.load.getBoolean("optimizer.active")
  private val optimizer = new LoadShedderOptimizer(model)
  private var initPhase = true

  def computeOnOutDegree[A,B](task: Task, extract: (Task, TaskEdge, Int) => A,
                              combine: (Map[Int, A], Int) => B): Int => B = {
    val extracted = task.output.zipWithIndex.map{case (outTE : TaskEdge, nextTaskIndex : Int) => {
        outTE.target.number -> extract(task, outTE, nextTaskIndex)
      }}.toMap
    (taskNumber: Int) => combine( extracted, taskNumber)
  }
  def computeOnOutDegreeX[A](ts: Long, metricName: String, extract: (Task, TaskEdge, Int) => A,
                              combine: (Map[Int, A], Int) => Double): Unit = {
    model.tasks.values.foreach {
      case (task: Task) => {
        val f = computeOnOutDegree(task, extract, combine)
        task.output.foreach { case (te: TaskEdge) =>
          val sm = new SimpleMeter(f(te.target.number))
          te.metrics += metricName -> te.metrics.getOrElse(metricName, new SimpleMeterSummary(1)).add(ts, sm)
        }
      }
    }
  }

  // TODO: Another scala riddle: I cannot include the expression of `extracted` into `combine`. If I do, the code gets executed more than task.source.size times
  def computeOnInDegree[A,B](task: Task, extract: (Task, TaskEdge, Int) => A,
                             combine: (Map[Int, A], Int) => B): Int => B = {
    val extracted = task.input.zipWithIndex.map{case (inTE : TaskEdge, nextTaskIndex : Int) => {
      print(task.input.size)
      inTE.source.number -> extract(task, inTE, nextTaskIndex)
    }}.toMap
    (taskNumber: Int) => combine(extracted, taskNumber)
  }

  def computeOutDist(task: Task): Int => Double = computeOnOutDegree(
      task,
      (task: Task, _: TaskEdge, nextTaskIndex: Int) =>
        task.getMetricSummary(MetricNames.buffersByChannel(nextTaskIndex)).getMean,
      (result: Map[Int, Double], nextTaskIndex: Int) =>
        result(nextTaskIndex)/result.values.sum
    )

  /*
  def computeOutDist(task: Task): Int => Double = {
    val outDistRaw : Map[Int, Double] = task.output.zipWithIndex.map{case (out : TaskEdge, i : Int) => {
      out.target.number -> task.getMetricSummary(MetricNames.buffersByChannel(i)).getMean
    }}.toMap
    (taskNumber: Int) => outDistRaw(taskNumber)/outDistRaw.values.sum
  }
  */


  def computeInDist(task: Task): Int => Double = {
    var inDistCtr : Map[Int, Int] = task.input.map(x => x.source.number -> -1).toMap
    computeOnInDegree(
      task,
      (task: Task, inTE: TaskEdge, i: Int) => {
        val inSource = inTE.source
        val key = if (inSource.parent.commType == CommType.ALL_TO_ALL) {
          MetricNames.buffersByChannel(task.number)
        } else {
          inDistCtr += inSource.number -> (inDistCtr(inSource.number) + 1)
          MetricNames.buffersByChannel(inDistCtr(inSource.number))
        }
        inSource.getMetricSummary(key).getMean
      },
      (result: Map[Int, Double], nextTaskIndex: Int) =>
        result(nextTaskIndex)/result.values.sum
    )
  }

  /*
  def computeInDist(task: Task): Int => Double = {
    val inDistRaw : Map[Int, Double] = task.input.map({case (in : TaskEdge) =>
      val inSource = in.source
      val key = if (inSource.parent.commType == CommType.POINTWISE) {
        MetricNames.buffersByChannel(task.number)
      } else {
        inSource.inDistCtr += 1
        MetricNames.buffersByChannel(inSource.inDistCtr)
      }
      val value = inSource.getMetricSummary(key).getMean
      inSource.number -> value
    }).toMap
    (taskNumber: Int) => inDistRaw(taskNumber)/inDistRaw.values.sum
  }
  */

  def computeInfMetrics(task: Task): Boolean = {
    val compInDistPerTask = computeInDist(task)
    val compOutDistPerTask = computeOutDist(task)
    for (in <- task.input) {
      in.inF = compInDistPerTask(in.source.number)
    }
    for (out <- task.output) {
      out.outF = compOutDistPerTask(out.target.number)
    }

    task.outQueueSaturation = task.getMetricSummary(MetricNames.outPoolUsage).getMean

    task.inQueueSaturation = {
      val iQS = task.getMetricSummary(MetricNames.inPoolUsage).getMean
      // when the input queue is colocated with the output queue, the input queue equals the output queue.
      val iQSlist = for ( in <- task.input if in.source.host == task.host )
        yield in.source.getMetricSummary(MetricNames.outPoolUsage).getMean
      (iQS::iQSlist).max
    }

    task.inRate = task.getMetricSummary(MetricNames.numRecordsInPerSecond).getMean
    task.outRate = task.getMetricSummary(MetricNames.numRecordsOutPerSecond).getMean
    task.selectivity = {
      if (task.parent.predecessor.isEmpty || task.parent.successor.isEmpty) 1.0
      else task.getMetricSummary(MetricNames.numRecordsOut).getMean/task
               .getMetricSummary(MetricNames.numRecordsIn).getMean
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
    mfw.updateInferredMetrics(model)

    // solve LP after initialization period
    if ((!active) || (initPhase && System.currentTimeMillis() - beginTime < initPhaseDuration)) {
      return
    } else if (initPhase) {
      initPhase = false
      active = optimizer.canOptimize(model)
      if (!active) return
      mfw.writeStartOptimization()
      model.runtimeStatus.optimizerStarted = true
    }
    //TODO: this is just a test solution because optimizer is not ready yet
    LOG.debug("Send new values to all loadshedders")
    LoadShedderManager.sendTestValuesToAllLoadShedders()
    //LOG.debug("Send new values to all loadshedders")
    //LoadShedderManager.sendTestValuesToAllLoadShedders()
    optimizer.optimize()
  }

  def cancel(): Unit = {
  }

  override def run(): Unit = {
    traverseModel()
  }
}
