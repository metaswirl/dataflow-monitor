package berlin.bbdc.inet.mera.monitor.optimizer

import berlin.bbdc.inet.mera.monitor.akkaserver.LoadShedderManager
import berlin.bbdc.inet.mera.monitor.model.{Model, ModelFileWriter, Operator, TaskEdge}
import org.slf4j.{Logger, LoggerFactory}

class LoadShedderOptimizer(val model : Model, val mfw: ModelFileWriter, warmStart: Boolean=true) extends AbstractOptimizer  {
  import gurobi._
  val LOG: Logger = LoggerFactory.getLogger("LPSolver")
  var grbModelOption: Option[GRBModel] = None
  var prevResults: Map[String, Int] = Map()

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
      case e: Throwable => LOG.error("Cannot optimize: Gurobi is not installed", e)
                           return false
    }
    return true
  }

  private def traverseOperators(grbModel: GRBModel, op: Operator): Unit = {
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

  private def storeResults(grbModel: GRBModel): Unit = {
    grbModel.getVars.foreach(v => {
      prevResults += v.get(GRB.StringAttr.VarName) -> v.get(GRB.IntAttr.VBasis)
    })
    grbModel.getConstrs.foreach(c => {
      prevResults += c.get(GRB.StringAttr.ConstrName) -> c.get(GRB.IntAttr.CBasis)
    })
  }
  private def loadResults(grbModel: GRBModel): Unit = {
    grbModel.getVars.foreach(v => {
      prevResults.get(v.get(GRB.StringAttr.VarName)).foreach(v.set(GRB.IntAttr.VBasis, _))
    })
    grbModel.getConstrs.foreach(c => {
      prevResults.get(c.get(GRB.StringAttr.ConstrName)).foreach(c.set(GRB.IntAttr.CBasis, _))
    })
  }
  private def processResults(grbModel : GRBModel): Unit = {
    // TODO: Check first whether a solution was found
    // TODO: Sending the same drop rate twice could be avoided
    var result = ""
    grbModel.getVars.foreach(x => {
      val name = x.get(GRB.StringAttr.VarName)
      val number = x.get(GRB.DoubleAttr.X)
      if (name.contains("dropRate")) {
        val newName = name.replace("dropRate_", "")
        model.tasks(newName).dropRate = number
        LoadShedderManager.sendNewValue(newName, Math.ceil(number).toInt)
      } else if (name.contains("rate")) {
        val newName = name.replace("rate_", "")
        if (number > model.tasks(newName).capacity) {
          LOG.warn(s"rate is above capacity! $number > ${model.tasks(newName).capacity}")
        }
      }
      result = s"$result\n$name = $number"
    })
    mfw.updateInferredMetrics(model)

    // TODO: Hide this away, only use in debug
    // val fname = s"/tmp/grb_${System.currentTimeMillis()}.lp"
    // grbModel.write(fname)
    // import java.nio.file.{Files, Paths, StandardOpenOption}
    // Files.write(Paths.get(fname), result.getBytes, StandardOpenOption.APPEND)
  }

  // TODO: Break this further up
  def optimize(): Unit = {
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
