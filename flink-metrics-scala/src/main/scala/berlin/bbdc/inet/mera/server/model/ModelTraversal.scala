package berlin.bbdc.inet.mera.server.model

import berlin.bbdc.inet.mera.server.metrics.MetricNotFoundException
import org.slf4j.{Logger, LoggerFactory}
import gurobi._
import java.net._
import java.io._
import java.nio.file.Files

import scala.io._

class ModelTraversal(val model: Model, val mfw : ModelFileWriter) extends Runnable {
  val LOG: Logger = LoggerFactory.getLogger("Model")
  private val queueAlmostEmpty : Double = 0.2
  private val queueAlmostFull : Double = 0.8
  private val penalty: Double = 0.9
  private val queueFull : Double = 1.0
  private val beginTime = System.currentTimeMillis()
  private var initPhase = true
  private var initPhaseDuration = 120 * 1000

  def computeInfMetrics(task: Task): Boolean = {
    def computeOutDist() {
      var sum : Double = 0
      var outDistRaw : Map[Int, Double] = Map()
      for ((out, i) <- task.output.zipWithIndex) {
        val value = task.getMetricSummary(f"Network.Output.0.$i%d.buffersByChannel").getMean
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
        val value = in.source.getMetricSummary(key).getMean
        inDistRaw += in.source.number -> value
        sum += value
      }
      for (in <- task.input) {
        in.inF = inDistRaw(in.source.number)/sum
      }
    }

    computeOutDist()
    computeInDist()

    task.outQueueSaturation = task.getMetricSummary("buffers.outPoolUsage").getMean

    // when the input queue is colocated with the output queue, the input queue equals the output queue.
    task.inQueueSaturation = {
      val iQS = task.getMetricSummary("buffers.inPoolUsage").getMean
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
    if (initPhase) {
      if (System.currentTimeMillis() - beginTime > initPhaseDuration) {
        initPhase = false
        mfw.writeStartOptimization()
      } else {
        return
      }
    }
    solveLP()
  }

//    def traverseOp(op: Operator) : Unit = {
//      op.tasks.foreach(computeTargets)
//      if (op.predecessor.isEmpty) return
//      traverseOp(op.predecessor.head)
//    }
//    traverseOp(model.sink)
//    mfw.updateTargetMetrics(model)

  def solveLP(): Unit = {
    val env = new GRBEnv("/tmp/lp.log")
    val grbModel = new GRBModel(env)

    def predecessors[T](task: Task)(fun: TaskEdge => T): List[T] = {
      task.input.map(fun(_))
    }
    def collect(te: TaskEdge): GRBLinExpr = {
      val expr : GRBLinExpr  = new GRBLinExpr()
      //println(te.source.id, te.target.id, te.source.selectivity, te.outF)
      expr.addTerm(te.source.selectivity * te.outF, te.source.gurobiRate)
      expr
    }
    def traverse_operators(op: Operator): Unit = {
      for (task <- op.tasks) {
        val cap = if (task.capacity == Double.PositiveInfinity) GRB.INFINITY else task.capacity
        task.gurobiRate = grbModel.addVar(0.0, task.capacity, 0.0, GRB.CONTINUOUS, "rate_" + task.id)
        if (op.predecessor.nonEmpty) {
          val expr = new GRBLinExpr()
          predecessors(task)(collect).foreach(expr.add(_))
          if (op.isLoadShedder) {
            val dropRate : GRBVar = grbModel.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS, "dropRate_" + task.id)
            expr.addTerm(-1, dropRate)
          }
          grbModel.addConstr(task.gurobiRate, GRB.EQUAL, expr, "flow_conservation_" + task.id)
        } else { // For the source!
          grbModel.addConstr(task.gurobiRate, GRB.EQUAL, task.outRate, "flow_conservation_" + task.id)
        }
      }
      op.successor.foreach(traverse_operators(_))
    }
    try {
      traverse_operators(model.src)
    } catch {
      case ex: Throwable => println(ex, ex.getMessage)
    }
    val expr = new GRBLinExpr()
    model.sink.tasks.foreach(t => expr.addTerm(1, t.gurobiRate))
    grbModel.setObjective(expr, GRB.MAXIMIZE)
    try {
      grbModel.optimize()
      // TODO: Check first whether a solution was found
      var result = ""

      grbModel.getVars().foreach(x => {
        val name = x.get(GRB.StringAttr.VarName)
        val number = x.get(GRB.DoubleAttr.X)
        if (name.contains("dropRate")) {
          val newName = name.replace("dropRate_", "")
          //println(newName, number)
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
      val fname = s"/tmp/grb_${System.currentTimeMillis()}.lp"
      grbModel.write(fname)
      import java.nio.file.Paths
      import java.nio.file.StandardOpenOption
      Files.write(Paths.get(fname), result.getBytes, StandardOpenOption.APPEND)
    } catch {
      case ex: Throwable => LOG.error(ex + ":" + ex.getMessage)
    }
  }

  def cancel(): Unit = {
  }

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
    val s = new Socket(InetAddress.getByName("localhost"), port)
    val out = new PrintStream(s.getOutputStream())
    out.print(msg + "\n")
    out.flush()
    s.close()
  }
  override def run(): Unit = {
    traverseModel()
  }
}


