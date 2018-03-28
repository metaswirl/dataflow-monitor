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
    new Model(n, operators.map(x => x.id -> x).toMap, taskEdges)
  }

  def connectOperator(op1: Operator, op2: Operator): Unit = {
    if (op1.commType == CommType.HASH || op1.commType == CommType.RANGE) {
      for (t <- op1.tasks) {
        for (t2 <- op2.tasks) {
          val te = new TaskEdge(t, t2)
          t.addOutput(te)
          t2.addInput(te)
          taskEdges :+= te
        }
      }
    } else {
      // TODO: This is not correct, I only assume this wiring!
      var n = op1.tasks.length
      var m = op2.tasks.length
      var count = 0
      if (n < m) {
        for (i <- 0 until n) {
          for (j <- 0 until Math.ceil(m * 1.0 / (n - i)).toInt) {
            val te = new TaskEdge(op1.tasks(i), op2.tasks(count))
            op1.tasks(i).addOutput(te)
            op2.tasks(count).addInput(te)
            taskEdges :+= te
            m -= 1
            count += 1
          }
        }
      } else {
        for (i <- 0 until m) {
          for (j <- 0 until Math.ceil(n * 1.0 / (m - i)).toInt) {
            val te = new TaskEdge(op1.tasks(count), op2.tasks(i))
            op1.tasks(count).addOutput(te)
            op2.tasks(i).addInput(te)
            taskEdges :+= te
            n -= 1
            count += 1
          }
        }
      }
    }
  }
}

