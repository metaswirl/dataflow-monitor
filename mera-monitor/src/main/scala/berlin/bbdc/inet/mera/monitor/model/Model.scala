package berlin.bbdc.inet.mera.monitor.model

import berlin.bbdc.inet.mera.monitor.metrics.{MetricNotFoundException, MetricSummary}
import berlin.bbdc.inet.mera.monitor.model.CommType.CommType
import gurobi.GRBVar

import scala.collection.immutable.ListMap

/* TODO: Separate data from traversal
 */

class Task(val parent: Operator, val number: Int, val host: Option[String]) {
  val id = s"${parent.id}.$number"

  var input: List[TaskEdge] = List()

  var output: List[TaskEdge] = List()

  var metrics: Map[String, MetricSummary[_]] = Map()

  def getMetricSummary(key: String): MetricSummary[_] = {
    metrics.getOrElse(key, throw MetricNotFoundException(key, id))
  }
  // TODO: feels like this is out of place
  var gurobiRate : GRBVar = _
  var inQueueSaturation: Double = _
  var outQueueSaturation: Double = _
  var selectivity: Double = _
  var inRate: Double = _
  var outRate: Double = _
  var capacity: Double = 0.0
  var targetPartialOutRate: Map[Int, Double] = Map()
  var targetInRate: Double = _
  var targetOutRate: Double = _
  var inDistCtr: Int = -1

  def addOutput(te: TaskEdge): Unit = {
    output :+= te
  }
  def addInput(te: TaskEdge): Unit = {
    input :+= te
  }

  override def toString: String = {
    val p = input.map(_.source.id).foldRight("")(_ + "," + _)
    val n = output.map(_.target.id).foldRight("")(_ + "," + _)
    f"Task-$number%s (In: $p%s Out: $n%s)"
  }
}

class TaskEdge(val source: Task, val target: Task) {
  var inF: Double = _
  var outF: Double = _
}

object CommType extends Enumeration {
  type CommType = Value
  val POINTWISE, ALL_TO_ALL, UNCONNECTED = Value
}

class Operator(val id: String, val parallelism: Int, val commType : CommType, val isLoadShedder : Boolean,
               taskToHostMapping : Option[Map[Int, String]]=None) {
  //TODO: Fix for cluster
  val tasks: List[Task] = List.range(0, parallelism).map((subTask : Int) =>
    new Task(this, subTask, taskToHostMapping.map(_(subTask)))
  )

  var predecessor: List[Operator] = List()

  var successor: List[Operator] = List()

  def addSucc(op: Operator): Unit = {
    op.predecessor = { this :: op.predecessor }
    successor = op :: successor
  }

  def apply(subtask: Int): Task = tasks(subtask)

  override def toString: String = {
    val t = tasks.foldRight("")(_ + "\n\t\t" + _)
    val p = predecessor.map(_.id.toString).foldRight("")(_ + "," + _)
    val s = successor.map(_.id.toString).foldRight("")(_ + "," + _)
    f"OP('$id%s'\n\tDOP: $parallelism%d\n\tPred: $p%s \n\tSucc: $s%s \n\tTasks: $tasks%s)"
  }
}

case class RuntimeStatus(val running : Boolean = true, var receivedFirstMetrics : Boolean = false, var optimizerStarted : Boolean = false)

//TODO: operators cannot be a map since the keys will overlap
case class Model(n :Int, operators : ListMap[String, Operator], taskEdges : List[TaskEdge]) {
  // Assuming single sink
  // TODO: start when job starts, end when job ends.
  // TODO: sink and src should be Iterable[Operator]
  val sink: Operator = operators.values.filter(_.successor.isEmpty).head
  val src: Operator = operators.values.filter(_.predecessor.isEmpty).head
  val tasks: Map[String,Task] = operators.values.flatMap(_.tasks).map(x => {x.id -> x}).toMap
  val runtimeStatus = new RuntimeStatus

  override def toString: String = {
    val op = operators.values.map("\n" + _.toString)
    f"Model(\n$op%s\n)"
  }
}
