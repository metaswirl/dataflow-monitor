package berlin.bbdc.inet.mera.monitor.model

import berlin.bbdc.inet.mera.monitor.model.CommType.CommType

import scala.collection.immutable.ListMap

/** Interface to construct a model
  */
class ModelBuilder {
  var operators: List[Operator] = List()
  var taskEdges: List[TaskEdge] = List()

  def addSuccessor(name: String, parallelism: Int, commType: CommType, isLoadShedder: Boolean,
                   taskToHostMapping : Option[Map[Int, String]]=None): Unit = {
    val op = new Operator(name, parallelism, commType, isLoadShedder, taskToHostMapping)
    operators.headOption.map({ (src : Operator) =>
      connectOperator(src, op)
      src.addSucc(op)
    })
    operators = op :: operators
  }

  def createModel(metricWindowSize: Int): Model = {
    new Model(metricWindowSize, ListMap(operators.map(x => x.id -> x): _*), taskEdges)
  }

  private def connectGrouped(sourceOp: Operator, targetOp: Operator): List[TaskEdge] =
    sourceOp.tasks.flatMap(t => targetOp.tasks.map( t2 => {
        val te = new TaskEdge(t, t2)
        t.addOutput(te)
        t2.addInput(te)
        te
    }))

  private def connectUngrouped(sourceOp: Operator, targetOp: Operator): List[TaskEdge] =
    if (sourceOp.tasks.length == targetOp.tasks.length) {
      (0 until sourceOp.tasks.length).map(index => {
        val te = new TaskEdge(sourceOp.tasks(index), targetOp.tasks(index))
        sourceOp.tasks(index).addOutput(te)
        targetOp.tasks(index).addInput(te)
        te
      }).toList
    } else {
      connectGrouped(sourceOp, targetOp)
    }

  private def connectOperator(sourceOp: Operator, targetOp: Operator): List[TaskEdge] =
    if (sourceOp.commType == CommType.POINTWISE) {
      connectGrouped(sourceOp, targetOp)
    } else {
      connectUngrouped(sourceOp, targetOp)
    }
}

// Reference: connectPointwise in ExecutionVertex in Flink code
// TODO: Is this still up-to-date? Or should I simply learn the topology from the metrics?
//       But then again, I can only learn them from my custom metrics.
/*else if (sourceNum < targetNum) {
  // earlier source tasks get connected with more target tasks
  val factor : Double = targetNum * 1.0 / sourceNum
  for (targetIndex <- 0 until targetNum) {
    val sourceIndex = (targetIndex / factor).toInt
    val te = new TaskEdge(sourceOp.tasks(sourceIndex).id, targetOp.tasks(targetIndex).id)
    sourceOp.tasks(sourceIndex).addOutput(te)
    targetOp.tasks(targetIndex).addInput(te)
    taskEdges :+= te
  }
} else {
  val factor : Double = sourceNum * 1.0 / targetNum

  for (targetIndex <- 0 until targetNum) {
    val start : Int = (targetIndex * factor).toInt
    val end : Int = ((targetIndex + 1) * factor).toInt
    for (sourceIndex <- start until end) {
      val te = new TaskEdge(sourceOp.tasks(sourceIndex).id, targetOp.tasks(targetIndex).id)
      sourceOp.tasks(sourceIndex).addOutput(te)
      targetOp.tasks(targetIndex).addInput(te)
      taskEdges :+= te
    }
  }
}
*/
