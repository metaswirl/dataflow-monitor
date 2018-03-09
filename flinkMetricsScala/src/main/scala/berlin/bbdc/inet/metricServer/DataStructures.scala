package berlin.bbdc.inet.metricServer
import java.sql.Timestamp

trait MetricHistory
// TODO: Simplify this!

class CounterHistory extends MetricHistory {
  var content : List[(Timestamp, Int)] = List()
  def add(ts : Timestamp, value : Int) = content = (ts, value) :: content
  def getFrom(ts: Timestamp) : List[(Timestamp, Int)] = {
    content.filter(_._1.after(ts))
  }
}

class RateHistory extends MetricHistory {
  var content : List[(Timestamp, Double)] = List()
  def add(ts : Timestamp, value : Double) = content = (ts, value) :: content
  def getFrom(ts: Timestamp) : List[(Timestamp, Double)] = {
    content.filter(_._1.after(ts))
  }
}

class Task(val id: String) {
  var input : List[Task] = List()
  var output : List[Task] = List()
  var metrics : Map[String, MetricHistory] = Map()
  def addOutput(n: Task) = output = n::output
  def addInput(n: Task) = input = n::input
  override def toString: String = {
    val p = input.map(_.id).foldRight("")(_ + "," + _)
    val n = output.map(_.id).foldRight("")(_ + "," + _)
    return "Node $id\n\tPrev: $p\n\tNext: $n\n"
  }
}

class Operator(val id: String) {
  val predecessor : List[Operator] = List()
  val successor : List[Operator] = List()
  val contained : List[Task] = List()
  override def toString: String = {
    val p = predecessor.map(_.id).foldRight("")(_ + "," + _)
    val n = successor.map(_.id).foldRight("")(_ + "," + _)
    val c = contained.map(_.id).foldRight("")(_ + "," + _)
    return "LogicalNode $id\n\tPrev: $p\n\tNext: $n\n\tContained: $c"
  }
}

class Host(val address: String) {
  var contained : Map[String, Task] = Map()
  def addTask(n : Task): Unit = contained += n.id -> n
}

class JobGraph {
  val tasks: Map[String, Task] = Map()
  val operators: Map[String, Operator] = Map()
  val sinks: List[Operator] = List()
}
