package berlin.bbdc.inet.mera.server.model

import berlin.bbdc.inet.mera.server.metrics._
import berlin.bbdc.inet.mera.server.model.CommType.CommType
import com.fasterxml.jackson.annotation.JsonIgnore

/* TODO: Separate data from traversal
 */

class Task(@JsonIgnore val parent: Operator, val number: Int, val host: String) {
  val id = s"${parent.id}.${number}"

  @JsonIgnore
  var input: List[Task] = List()

  @JsonIgnore
  var output: List[Task] = List()

  // TODO: combine into one data structure
  var gauges: Map[String, GaugeSummary] = Map()
  var counters: Map[String, CounterSummary] = Map()
  var meters: Map[String, MeterSummary] = Map()

  def getGaugeSummary(key: String): GaugeSummary = {
    if (!gauges.contains(key))
      throw MetricNotFoundException(s"Could not find $key for $id")
    gauges(key)
  }

  def getCounterSummary(key: String): CounterSummary = {
    if (!counters.contains(key))
      throw MetricNotFoundException(s"Could not find $key for $id")
    counters(key)
  }

  def getMeterSummary(key: String): MeterSummary = {
    if (!meters.contains(key))
      throw MetricNotFoundException(s"Could not find $key for $id")
    meters(key)
  }

  // TODO: Ask Carlo
  // def getMetric[M <: Metric](key : String) : Option[MetricSummary[M]] = {
  //   if (gauges.contains(key)) { return Some(gauges(key)) }
  //   else if (counters.contains(key)) { return Some(counters(key)) }
  //   else if (meters.contains(key)) { return Some(meters(key)) }
  // }

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

  def addOutput(n: Task): Unit = {
    n.input = {
      this :: n.input
    }
    output = n :: output
  }

  override def toString: String = {
    val p = input.map(_.id).foldRight("")(_ + "," + _)
    val n = output.map(_.id).foldRight("")(_ + "," + _)
    f"Task-$number%s (In: $p%s Out: $n%s)"
  }
}

object CommType extends Enumeration {
  type CommType = Value
  val Grouped, Ungrouped = Value
}

class Operator(val id: String, val parallelism: Int, val commType: CommType) {
  //TODO: Fix for cluster
  @JsonIgnore
  val tasks: List[Task] = List.range(0, parallelism).map(new Task(this, _, "localhost"))
  @JsonIgnore
  var predecessor: List[Operator] = List()
  @JsonIgnore
  var successor: List[Operator] = List()

  def addSucc(op: Operator): Unit = {
    op.predecessor = {
      this :: op.predecessor
    }
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
          for (j <- 0 until Math.ceil(m * 1.0 / (n - i)).toInt) {
            tasks(i).addOutput(op.tasks(count))
            m -= 1
            count += 1
          }
        }
      } else {
        for (i <- 0 until m) {
          for (j <- 0 until Math.ceil(n * 1.0 / (m - i)).toInt) {
            tasks(count).addOutput(op.tasks(i))
            n -= 1
            count += 1
          }
        }
      }
    }
  }

  def apply(subtask: Int): Task = tasks(subtask)

  override def toString: String = {
    val t = tasks.foldRight("")(_ + "\n\t\t" + _)
    val p = predecessor.map(_.id.toString).foldRight("")(_ + "," + _)
    val s = successor.map(_.id.toString).foldRight("")(_ + "," + _)
    f"OP('$id%s'\n\tDOP: $parallelism%d\n\tPred: $p%s \n\tSucc: $s%s \n\tTasks: $tasks%s)"
  }
}

//TODO: operators cannot be a map since the keys will overlap
class Model(val n: Int, val operators: Map[String, Operator]) {
  // Assuming single sink
  // TODO: start when job starts, end when job ends.
  // TODO: sink and src should be Iterable[Operator]
  val sink: Operator = operators.values.filter(_.successor.isEmpty).head
  val src: Operator = operators.values.filter(_.predecessor.isEmpty).head
  val tasks: Iterable[Task] = operators.values.flatMap(_.tasks)

  override def toString: String = {
    val op = operators.values.map("\n" + _.toString)
    f"Model(\n$op%s\n)"
  }
}

class ModelBuilder {
  var operators: List[Operator] = List()

  def addSuccessor(name: String, parallelism: Int, commType: CommType): Unit = {
    val op = new Operator(name, parallelism, commType)
    operators match {
      case h :: t => h.addSucc(op)
        operators = op :: h :: t
      case Nil => operators = op :: Nil
    }
  }

  def createModel(n: Int): Model = {
    return new Model(n, operators.map(x => x.id -> x).toMap)
  }
}
