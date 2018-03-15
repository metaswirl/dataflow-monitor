package berlin.bbdc.inet.mera.metricServer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.{ExecutorService, Executors, ScheduledExecutorService, TimeUnit}

import berlin.bbdc.inet.mera.metricServer.CommType.CommType

trait MetricHistory
// TODO: Simplify this!

abstract class Key
case class UnknownKey(metric: String) extends Key
case class JobManagerKey(host: String, metric: String) extends Key
abstract class TaskManagerKey(host: String, tmId: String) extends Key
abstract class TaskManagerStatusKey(host: String, tmId: String) extends TaskManagerKey(host, tmId)
case class TaskManagerJvmKey(host: String, tmId: String, metric: String) extends TaskManagerStatusKey(host, tmId)
case class TaskManagerNetworkKey(host: String, tmId: String, metric: String) extends TaskManagerStatusKey(host, tmId)
case class TaskManagerTaskKey(host: String, tmId: String, jobId: String, opId: String, taskId: Int, metric: String) extends TaskManagerKey(host, tmId)


object Key {
  var allKeys : Set[String] = Set()
  def buildKey(rawKey: String) : Key = {
    allKeys += rawKey
    val splitKey : Array[String] = rawKey.split('.')
    if (splitKey.length > 1 && splitKey(1) == "jobmanager") {
      val host = splitKey(0)
      return JobManagerKey(host, splitKey.drop(2).mkString("."))
    } else if (splitKey.length > 1 && splitKey(1) == "taskmanager") {
      val host = splitKey(0)
      if (splitKey.length > 2 && splitKey(3) == "Status") {
        if (splitKey.length > 4 && splitKey(4) == "JVM") {
          return TaskManagerJvmKey(host, splitKey(2), splitKey.drop(4).mkString("."))
        } else if (splitKey.length > 4 && splitKey(4) == "Network") {
          return TaskManagerNetworkKey(host, splitKey(2), splitKey.drop(4).mkString("."))
        }
      } else if (splitKey.length > 6) {
        return TaskManagerTaskKey(host, splitKey(2), splitKey(3), splitKey(4), splitKey(5).toInt, splitKey.drop(6).mkString("."))
      }
    }
    return UnknownKey(rawKey)
  }

}

abstract class Metric
case class Counter(val count: Long) extends Metric
case class Meter(val count : Long, rate : Double) extends Metric
case class Histogram(val count : Long, min : Long, max : Long, mean : Double) extends Metric
case class Gauge(val value: Double) extends Metric

abstract class MetricSummary[T <: Metric](val n: Int) {
  var history: List[(Long, T)] = List()
  var number: Int = 0

  def add(ts: Long, newVal: T): Unit = {
    history = (ts, newVal) :: history
    if (history.length > n) history = history.dropRight(history.length - n)
  }

  def getMean(): Double = ???

  def getRates(): List[Double] = ???

  def getRateMean() = getRates().sum * 1000 / (n - 1)

  override def toString: String = {
    val m = getMean
    val rm = getRateMean
    val cn = this.getClass.getSimpleName
    return f"$cn%s($n%s, $m%s, $rm%s, $history%s)"
  }
}
class GaugeSummary(override val n: Int) extends MetricSummary[Gauge](n) {
  override def getMean(): Double = if (history.length > 0) history.map(_._2.value).sum / (1.0 * n) else 0.0
  override def getRates() = {
    var res : List[Double] = List()
    for ((first, second) <- (history,  history.drop(1)).zipped) {
      res = (first._2.value - second._2.value) * 1.0 / (first._1 - second._1) :: res
    }
    res.reverse
  }
}
class CounterSummary(override val n: Int) extends MetricSummary[Counter](n) {
  override def getMean(): Double = if (history.length > 0) history.map(_._2.count).sum / (1.0 * n) else 0.0
  override def getRates() = {
    var res : List[Double] = List()
    for ((first, second) <- (history,  history.drop(1)).zipped) {
      res = (first._2.count - second._2.count) * 1.0 / (first._1 - second._1) :: res
    }
    res.reverse
  }
}
class MeterSummary(override val n: Int) extends MetricSummary[Meter](n) {
  override def getMean(): Double = if (history.length > 0) history.map(_._2.rate).sum / (1.0 * n) else 0.0
  override def getRates() = { history.map(_._2.rate) }
}

class Task(val parent: Operator, val subtask: Int) {
  private val queueAlmostEmpty : Double = 0.2
  private val queueAlmostFull : Double = 0.8
  private val queueFull : Double = 1.0
  var input : List[Task] = List()
  var output : List[Task] = List()
  var gauges: Map[String, GaugeSummary] = Map()
  var counters: Map[String, CounterSummary] = Map()
  var meters: Map[String, MeterSummary] = Map()
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
  // TODO: Ask Carlo
  // def getMetric[M <: Metric](key : String) : Option[MetricSummary[M]] = {
  //   if (gauges.contains(key)) { return Some(gauges(key)) }
  //   else if (counters.contains(key)) { return Some(counters(key)) }
  //   else if (meters.contains(key)) { return Some(meters(key)) }
  // }
  def addOutput(n: Task) = {
    n.input = { this :: n.input }
    output = n :: output
  }
  def computeInfMetrics(): Boolean = {
    // TODO: test
    var sum : Double = 0
    var outDistRaw : Map[Int, Double] = Map()
    for ((out, i) <- output.zipWithIndex) {
      val key = f"Network.Output.0.$i%d.buffersByChannel"
      if (!gauges.contains(key)) {
        return false
      }
      val value = gauges(key).getMean
      outDistRaw += out.subtask -> value
      sum += value
    }
    for ((k, v) <- outDistRaw) outDist += k -> v/sum

    sum = 0
    var inDistRaw : Map[Int, Double] = Map()
    for ((in, i) <- input.zipWithIndex) {
      val key = f"Network.Output.0.$i%d.buffersByChannel"
      if (!in.gauges.contains(key)) {
        return false
      }
      val value = in.gauges(key).getMean
      inDistRaw += in.subtask -> value
      sum += value
    }
    for ((k, v) <- inDistRaw) inDist += k -> v/sum

    if (!gauges.contains("buffers.inPoolUsage") || !gauges.contains("buffers.outPoolUsage")) {
      return false
    }
    inQueueSaturation = gauges("buffers.inPoolUsage").getMean
    outQueueSaturation = gauges("buffers.outPoolUsage").getMean

    // rate
    if (!counters.contains("numRecordsOut")) {
      return false
    }
    if (!counters.contains("numRecordsIn")) {
      return false
    }
    val tmpOut = counters("numRecordsOut")
    val tmpIn = counters("numRecordsIn")
    selectivity = tmpOut.getMean()/tmpIn.getMean()
    inRate = tmpIn.getRateMean
    capacity = if (inQueueSaturation > queueAlmostFull) {
        inRate * (1 - 0.2 * (inQueueSaturation - queueAlmostFull)/(queueFull - queueAlmostFull)) // peanlize bottleneck
      } else if (inQueueSaturation > queueAlmostEmpty && outQueueSaturation > queueAlmostFull) {
        inRate // already processing at maximum input rate
      } else {
        Double.PositiveInfinity // value unknown
      }
    true
  }
  def computeTargets() = {
    // TODO: test
    // targetInputRate
    targetInRate = if (output.isEmpty) {
        capacity
      } else {
        targetOutRate = targetPartialOutRate.map(x => x._2 * outDist(x._1)).min
        Math.min(targetOutRate/selectivity, capacity)
      }

    // targetPartialOutRate of inputs
    for (i <- input) {
      i.targetPartialOutRate += subtask -> inDist(i.subtask) * targetInRate
    }
  }
  override def toString: String = {
    val p = input.map(x => {x.parent.id + "-" + x.subtask}).foldRight("")(_ + "," + _)
    val n = output.map(x => {x.parent.id + "-" + x.subtask}).foldRight("")(_ + "," + _)
    return f"Task$subtask%s (In: $p%s Out: $n%s)"
  }
}

object CommType extends Enumeration {
    type CommType = Value
    val AllToAll, OneToOne = Value
}

class Operator(val id: String, val parallelism: Int, commType : CommType) {
  val tasks: List[Task] = List.range(0, parallelism).map(new Task(this, _))
  var predecessor : List[Operator] = List()
  var successor : List[Operator] = List()
  def addSucc(op: Operator) = {
    op.predecessor = { this :: op.predecessor }
    successor = op :: successor
    if (commType == CommType.AllToAll) {
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
        for (i <- (0 until n)) {
          for (j <- (0 until Math.ceil(m * 1.0 / (n-i)).toInt)) {
            tasks(i).addOutput(op.tasks(count))
            m -= 1
            count += 1
          }
        }
      } else {
        for (i <- (0 until m)) {
          for (j <- (0 until Math.ceil(n * 1.0 / (m-i)).toInt)) {
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
    return f"OP('$id%s'\n\tDOP: $parallelism%d\n\tPred: $p%s \n\tSucc: $s%s \n\tTasks: $tasks%s)"
  }
}

class Host(val address: String, val taskManagerID: String) {
  var contained : Map[String, Task] = Map()
}

class Model(val n :Int) {
  // TODO: fix logging
  val LOG : Logger = LoggerFactory.getLogger("Model")
  val operators: Map[String, Operator] = setup()
  // Assuming single sink
  val sink: Operator = operators.values.filter(_.successor.isEmpty).head
  val tasks = operators.values.map(_.tasks).flatten
  // TODO: start when job starts, end when job ends.
  val schd : ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
  schd.scheduleAtFixedRate(new Runnable { override def run() = {traverseModel() } }, 10, 1, TimeUnit.SECONDS)

  def setup(): Map[String, Operator] = {
    // TODO: generate automatically
    val src = new Operator("Source: Socket Stream", 1, CommType.OneToOne)
    val flatMapMap = new Operator("Flat Map -> Map", 3, CommType.AllToAll)
    val aggregation = new Operator("aggregation -> bottleneck", 3, CommType.OneToOne)
    val sink = new Operator("Sink: Unnamed", 2, CommType.OneToOne)
    src.addSucc(flatMapMap)
    flatMapMap.addSucc(aggregation)
    aggregation.addSucc(sink)
    Map(src.id -> src, flatMapMap.id -> flatMapMap, aggregation.id -> aggregation, sink.id -> sink)
  }
  def traverseModel() {
    println("traversing START")
    if (!tasks.map(_.computeInfMetrics()).reduce(_ && _)) { println("Could not compute inferred metrics") }
    println("Computed inferred metrics")
    var op = sink
    var condition = true
    while(condition)  {
      op.tasks.foreach(_.computeTargets())
      if (!op.predecessor.isEmpty) {
        op = op.predecessor.head
      } else {
        condition = false
      }
    }
    // TODO: ask Carlo
    /*
    val mmm : Map[Int, String] = Map(1->"a", 2->"b")
    mmm.map(x => (x._1, x._2)) // Works!
    mmm.map((a, b) => (a, b)) // Doesn't... But why?
    mmm.map( {case (a,b ) => (a,b)}) // Does work. Is this always necessary?

    // It's not just maps
    val xxx : List[(Int, Int)] = List((1,2))
    xxx.map((a,b) => (a,b))
    xxx.map({ case (a,b) => (a,b) })
    */

    //println(operators.map(x => (x._2.id, x._2.tasks)).flatMap((a : String, b : List[Task]) => { b.map(x => (a, x._2.subtask, x._2.targetRate)) } )
    println(printTargets())
    println("traversing END")
  }

  override def toString: String = {
    val op = operators.values.map("\n" + _.toString)
    return f"Model(\n$op%s\n)"
  }

  def update(timestamp : Long, metrics: List[(Key, Metric)]): Unit = metrics.foreach(_ match {
    // TODO: Schedule after first metric has arrived
    case (k: TaskManagerTaskKey, m: Metric) => {
      // TODO: fix hack
      // apparently even though two operators are merged in the same task chain, they still get different metric readings.
      // This requires further inquiry into how the task chain operates. Is there a buffer between the operators?
      val t : Task = if (k.opId == "Flat Map" || k.opId == "Map") {
          operators("Flat Map -> Map")(k.taskId)
        } else if (k.opId == "aggregation" || k.opId == "bottleneck") {
          operators("aggregation -> bottleneck")(k.taskId)
        } else {
          operators(k.opId)(k.taskId)
        }

      m match {
        case x: Gauge => {
          val gs: GaugeSummary = t.gauges.getOrElse(k.metric, new GaugeSummary(n))
          gs.add(timestamp, x)
          t.gauges += (k.metric -> gs)
        }
        case x: Counter => {
          val cs : CounterSummary = t.counters.getOrElse(k.metric, new CounterSummary(n))
          cs.add(timestamp, x)
          t.counters += (k.metric -> cs)
        }
        case x: Meter => {
          val ms : MeterSummary = t.meters.getOrElse(k.metric, new MeterSummary(n))
          ms.add(timestamp, x)
          t.meters += (k.metric -> ms)
        }
      }
    }
    case (k: UnknownKey, _) => { LOG.warn("Could not parse key: " + k.metric); println("Could not parse key: " + k.metric) }
    case (jk: JobManagerKey, _) => { }
    case (tsk: TaskManagerStatusKey, _) => { }
    case (k: Key, m: Metric) => { LOG.warn("Ignoring key: " + k + "-" + m); println("Ignoring key: " + k + "-" + m) }
  })
  def printGraph(): String = {
    this.toString
  }
  def printKeys(): String = {
    Key.allKeys.map(_ + "\n").toString
  }
  def printMetrics(opId: String, taskId: Int): String = {
    val t = operators(opId)(taskId)
    return t.counters.map(x => x + "\n") +"\n" +
           t.gauges.map(x => x + "\n") + "\n" +
           t.meters.map(x => x + "\n") + "\n"
  }
  def printInfMetrics(opId: String, taskId: Int): String = {
    val t = operators(opId)(taskId)
    return "inDist:\t " + t.inDist.map(x => x._1 + "->" + x._2) + "\n" +
           "outDist:\t" + t.outDist + "\n" +
           "inRate:\t" + t.inRate + "\n" +
           "selectivity: \t" + t.selectivity + "\n"
  }
  def printTargets(): String = {
    return operators.values.map(x => (x.id, x.tasks)).flatMap({ case (a,b) => { b.map(x => f"$a%s, sub:${x.subtask}%d, sel:${x.selectivity}%f, tIn:${x.targetInRate}%f, tOut:${x.targetOutRate}%f, cap:${x.capacity}%f, tPout:${x.targetPartialOutRate}%s-${x.input.size}%d tInDist:${x.inDist} tOutDist:${x.outDist}")}}).mkString("\n")
  }
}
