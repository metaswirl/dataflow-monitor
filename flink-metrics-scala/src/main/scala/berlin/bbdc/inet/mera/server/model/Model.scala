package berlin.bbdc.inet.mera.server.model

import java.io._

import berlin.bbdc.inet.mera.server.metrics._
import berlin.bbdc.inet.mera.server.model.CommType.CommType

/* TODO: Separate data from traversal
 */

class Task(val parent: Operator, val subtask: Int, val host: String) {

  val writer = new PrintWriter(new File(f"/tmp/job_${parent.id}%s-$subtask%d.log"))
  var input : List[Task] = List()
  var output : List[Task] = List()

  // TODO: combine into one data structure
  var gauges: Map[String, GaugeSummary] = Map()
  var counters: Map[String, CounterSummary] = Map()
  var meters: Map[String, MeterSummary] = Map()

  // TODO: group together
  var inDist: Map[Int, Double] = Map()
  var outDist: Map[Int, Double] = Map()
  var inQueueSaturation: Double = 0.0
  var outQueueSaturation: Double = 0.0
  var selectivity: Double = 0.0
  var inRate: Double = 0.0
  var capacity: Double = 0.0
  var targetPartialOutRate: Map[Int, Double] = Map()
  var targetInRate: Double = 0.0
  var targetOutRate: Double = 0.0

  var inDistCtr: Int = -1


  // TODO: Ask Carlo
  // def getMetric[M <: Metric](key : String) : Option[MetricSummary[M]] = {
  //   if (gauges.contains(key)) { return Some(gauges(key)) }
  //   else if (counters.contains(key)) { return Some(counters(key)) }
  //   else if (meters.contains(key)) { return Some(meters(key)) }
  // }
  def addOutput(n: Task): Unit = {
    n.input = { this :: n.input }
    output = n :: output
  }
  override def toString: String = {
    val p = input.map(x => {x.parent.id + "-" + x.subtask}).foldRight("")(_ + "," + _)
    val n = output.map(x => {x.parent.id + "-" + x.subtask}).foldRight("")(_ + "," + _)
    f"Task-$subtask%s (In: $p%s Out: $n%s)"
  }
}

object CommType extends Enumeration {
    type CommType = Value
    val Grouped, RecordByRecord = Value
}

class Operator(val id: String, val parallelism: Int, val commType : CommType) {
  //TODO: Fix for cluster
  val tasks: List[Task] = List.range(0, parallelism).map(new Task(this, _, "localhost"))
  var predecessor : List[Operator] = List()
  var successor : List[Operator] = List()

  def addSucc(op: Operator): Unit = {
    op.predecessor = { this :: op.predecessor }
    successor = op :: successor
    if (commType == CommType.Grouped) {
      for (t <- tasks) {
        for (t2 <- op.tasks)
          t.addOutput(t2)
      }
    } else {
      // TODO: This is not correct, I only assume this wiring!
      var n = tasks.length
      var m = op.tasks.length
      var count = 0
      if (n < m) {
        for (i <- 0 until n) {
          for (j <- 0 until Math.ceil(m * 1.0 / (n-i)).toInt) {
            tasks(i).addOutput(op.tasks(count))
            m -= 1
            count += 1
          }
        }
      } else {
        for (i <- 0 until m) {
          for (j <- 0 until Math.ceil(n * 1.0 / (m-i)).toInt) {
            tasks(count).addOutput(op.tasks(i))
            n -= 1
            count += 1
          }
        }
      }
    }
  }

  def apply(subtask : Int) : Task = tasks(subtask)
  override def toString: String = {
    val t = tasks.foldRight("")(_ + "\n\t\t" + _)
    val p = predecessor.map(_.id.toString).foldRight("")(_ + "," + _)
    val s = successor.map(_.id.toString).foldRight("")(_ + "," + _)
    f"OP('$id%s'\n\tDOP: $parallelism%d\n\tPred: $p%s \n\tSucc: $s%s \n\tTasks: $tasks%s)"
  }
}

class Model(val n :Int, val operators : Map[String, Operator]) {
  // Assuming single sink
  val sink: Operator = operators.values.filter(_.successor.isEmpty).head
  val src: Operator = operators.values.filter(_.predecessor.isEmpty).head
  val tasks: Iterable[Task] = operators.values.flatMap(_.tasks)
  // TODO: start when job starts, end when job ends.

  override def toString: String = {
    val op = operators.values.map("\n" + _.toString)
    f"Model(\n$op%s\n)"
  }
}

class ModelBuilder {
  var operators: List[Operator] = List()

  def addSuccessor(name: String, parallelism : Int, commType : CommType): Unit = {
    val op = new Operator(name, parallelism, commType)
    operators match {
      case h :: t => h.addSucc(op)
                     operators = op :: h :: t
      case Nil => operators = op :: Nil
    }
  }
  def createModel(n : Int): Model = {
    return new Model(n, operators.map(x => x.id -> x).toMap)
  }
}
