package berlin.bbdc.inet.mera.server.model

import berlin.bbdc.inet.mera.server.model.CommType.CommType

class ModelBuilder {
  var operators: List[Operator] = List()
  var taskEdges: List[TaskEdge] = List()

  def addSuccessor(name: String, parallelism: Int, commType: CommType): Unit = {
    val op = new Operator(name, parallelism, commType)
    operators match {
      case h :: t =>
        connectOperator(h, op)
        h.addSucc(op)
        operators = op :: h :: t
      case Nil => operators = op :: Nil
    }
  }

  def createModel(n: Int): Model = {
    return new Model(n, operators.map(x => x.id -> x).toMap, taskEdges)
  }

  def connectGrouped(sourceOp: Operator, targetOp: Operator) = {
    for (t <- sourceOp.tasks) {
      for (t2 <- targetOp.tasks) {
        val te = new TaskEdge(t, t2)
        t.addOutput(te)
        t2.addInput(te)
        taskEdges :+= te
      }
    }
  }

  def connectUngrouped(sourceOp: Operator, targetOp: Operator): Unit = {
    // Reference: connectPointwise in ExecutionVertex in Flink code
    val sourceNum = sourceOp.tasks.length
    val targetNum = targetOp.tasks.length

    if (sourceNum < targetNum) {
      // earlier source tasks get connected with more target tasks
      val factor : Double = targetNum * 1.0 / sourceNum
      for (targetIndex <- 0 until targetNum) {
        val sourceIndex = (targetIndex / factor).toInt
        val te = new TaskEdge(sourceOp.tasks(sourceIndex), targetOp.tasks(targetIndex))
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
          val te = new TaskEdge(sourceOp.tasks(sourceIndex), targetOp.tasks(targetIndex))
          sourceOp.tasks(sourceIndex).addOutput(te)
          targetOp.tasks(targetIndex).addInput(te)
          taskEdges :+= te
        }
      }
    }
  }

  def connectOperator(sourceOp: Operator, targetOp: Operator): Unit = {
    if (sourceOp.commType == CommType.Grouped) {
      connectGrouped(sourceOp, targetOp)
    } else {
      connectUngrouped(sourceOp, targetOp)
    }
  }
}

