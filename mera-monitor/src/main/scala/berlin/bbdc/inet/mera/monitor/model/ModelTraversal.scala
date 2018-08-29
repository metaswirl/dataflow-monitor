package berlin.bbdc.inet.mera.monitor.model

import berlin.bbdc.inet.mera.monitor.akkaserver.LoadShedderManager
import berlin.bbdc.inet.mera.monitor.metrics.MetricNotFoundException
import org.slf4j.{Logger, LoggerFactory}
import com.typesafe.config.ConfigFactory

case class ModelTraversal(model: Model, mfw : ModelFileWriter) extends Runnable {
  val LOG: Logger = LoggerFactory.getLogger(getClass)
  private val queueAlmostEmpty : Double = ConfigFactory.load.getDouble("optimizer.queueAlmostEmpty")
  private val queueAlmostFull : Double = ConfigFactory.load.getDouble("optimizer.queueAlmostFull")
  private val penalty: Double = ConfigFactory.load.getDouble("optimizer.penalty")
  private val queueFull : Double = ConfigFactory.load.getDouble("optimizer.queueFull")
  private val beginTime = System.currentTimeMillis()
  private val initPhaseDuration = Math.max(ConfigFactory.load.getLong("optimizer.initPhaseDuration"), 30000L)
  private var active = ConfigFactory.load.getBoolean("optimizer.active")
  private val lPSolver = new LPSolver(model)
  private var initPhase = true

  def canOptimize(model: Model): Boolean = {
    if (!LoadShedderManager.loadsheddersExist()) {
      LOG.warn("Cannot optimize: No loadshedder present in current job")
      return false
    }
    // TODO: validate this on a machine where Gurobi is not installed
    try {
      import gurobi._
      new GRBModel(new GRBEnv()).update()
    } catch {
      case _: Throwable => LOG.warn("Cannot optimize: Gurobi is not installed")
                           return false
    }
    return true
  }

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
    mfw.updateInferredMetrics(model)

    // solve LP after initialization period
    if ((!active) || (initPhase && (System.currentTimeMillis() - beginTime) < initPhaseDuration)) {
      return
    } else if (initPhase) {
      initPhase = false
      active = canOptimize(model)
      if (!active) return
      mfw.writeStartOptimization()
      model.runtimeStatus.optimizerStarted = true
    }
    //TODO: this is just a test solution because optimizer is not ready yet
    LOG.debug("Send new values to all loadshedders")
    LoadShedderManager.sendTestValuesToAllLoadShedders()
    //    lPSolver.solveLP()
    lPSolver.solveLP()
  }

  def cancel(): Unit = {
  }

  override def run(): Unit = {
    traverseModel()
  }
}

class LPSolver(val model : Model, val warmStart: Boolean=true) {
  import gurobi._
  val LOG: Logger = LoggerFactory.getLogger("LPSolver")
  var grbModelOption: Option[GRBModel] = None
  var prevResults: Map[String, Int] = Map()


  def traverseOperators(grbModel: GRBModel, op: Operator): Unit = {
    def collect(te: TaskEdge): GRBLinExpr = {
      val expr : GRBLinExpr  = new GRBLinExpr()
      expr.addTerm(te.source.selectivity * te.outF, te.source.gurobiRate)
      expr
    }

    for (task <- op.tasks) {
      val cap = if (task.capacity == Double.PositiveInfinity) GRB.INFINITY else task.capacity
      task.gurobiRate = grbModel.addVar(0.0, task.capacity, 0.0, GRB.CONTINUOUS, "rate_" + task.id)
      if (op.predecessor.nonEmpty) {
        val expr = new GRBLinExpr()
        task.input.map(collect).foreach(expr.add)
        if (op.isLoadShedder) {
          val dropRate : GRBVar = grbModel.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS, "dropRate_" + task.id)
          expr.addTerm(-1, dropRate)
        }
        grbModel.addConstr(task.gurobiRate, GRB.EQUAL, expr, "flow_conservation_" + task.id)
      } else { // For the source!
        grbModel.addConstr(task.gurobiRate, GRB.EQUAL, task.outRate, "flow_conservation_" + task.id)
      }
    }
    op.successor.foreach(traverseOperators(grbModel, _))
  }

  def storeResults(grbModel: GRBModel): Unit = {
    grbModel.getVars.foreach(v => {
      prevResults += v.get(GRB.StringAttr.VarName) -> v.get(GRB.IntAttr.VBasis)
    })
    grbModel.getConstrs.foreach(c => {
      prevResults += c.get(GRB.StringAttr.ConstrName) -> c.get(GRB.IntAttr.CBasis)
    })
  }
  def loadResults(grbModel: GRBModel): Unit = {
    grbModel.getVars.foreach(v => {
      prevResults.get(v.get(GRB.StringAttr.VarName)).foreach(v.set(GRB.IntAttr.VBasis, _))
    })
    grbModel.getConstrs.foreach(c => {
      prevResults.get(c.get(GRB.StringAttr.ConstrName)).foreach(c.set(GRB.IntAttr.CBasis, _))
    })
  }
  def processResults(grbModel : GRBModel): Unit = {
    // TODO: Check first whether a solution was found
    var result = ""
    grbModel.getVars.foreach(x => {
      val name = x.get(GRB.StringAttr.VarName)
      val number = x.get(GRB.DoubleAttr.X)
      if (name.contains("dropRate")) {
        // TODO: Using this to control the job. Use Akka instead.
        val newName = name.replace("dropRate_", "")
        send(newName, number.toInt.toString)
      } else if (name.contains("rate")) {
        val newName = name.replace("rate_", "")
        LOG.info(s"$newName: $number; ${model.tasks(newName).capacity}")
        if (number > model.tasks(newName).capacity) {
          LOG.warn(s"$number > ${model.tasks(newName).capacity}")
        }
      }
      result = s"$result\n$name = $number"
    })

    // TODO: Hide this away, only use in debug
    val fname = s"/tmp/grb_${System.currentTimeMillis()}.lp"
    grbModel.write(fname)
    import java.nio.file.Files
    import java.nio.file.Paths
    import java.nio.file.StandardOpenOption
    Files.write(Paths.get(fname), result.getBytes, StandardOpenOption.APPEND)
  }

  // TODO: Obviously ugly
  def send(newName : String, msg: String): Unit = {
    if (newName == "loadshedder0.0") {
      sendToPort(22200, msg)
    } else if (newName == "loadshedder1.0") {
      sendToPort(22210, msg)
    } else if (newName == "loadshedder1.1") {
      sendToPort(22211, msg)
    } else if (newName == "loadshedder1.2") {
      sendToPort(22212, msg)
    } else if (newName == "loadshedder2.0") {
      sendToPort(22220, msg)
    } else if (newName == "loadshedder2.1") {
      sendToPort(22221, msg)
    }
  }

  def sendToPort(port: Int, msg: String): Unit = {
    import java.io._
    import java.net._
    val s = new Socket(InetAddress.getByName("localhost"), port)
    val out = new PrintStream(s.getOutputStream)
    out.print(msg + "\n")
    out.flush()
    s.close()
  }

  // TODO: Break this further up
  def solveLP(): Unit = {
    // TODO: Add config option
    try {
      grbModelOption = grbModelOption match {
        case Some(m : GRBModel) => m.reset()
                                   Some(m)
        case None => Some(new GRBModel(new GRBEnv("/tmp/lp.log")))
      }
      val grbModel = grbModelOption.get
      traverseOperators(grbModel, model.src)
      val expr = new GRBLinExpr()
      model.sink.tasks.foreach(t => expr.addTerm(1, t.gurobiRate))
      grbModel.setObjective(expr, GRB.MAXIMIZE)
      grbModel.update()
      if (warmStart) {
        loadResults(grbModel)
      }
      grbModel.optimize()
      if (warmStart) {
        storeResults(grbModel)
      }
      processResults(grbModel)
    } catch {
      case ex: Throwable => LOG.error(ex + " : " + ex.getMessage + "\n" + ex.getStackTrace.map(_.toString()).mkString("\n"))
    }
  }
}


