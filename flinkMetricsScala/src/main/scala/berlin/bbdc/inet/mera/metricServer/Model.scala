package berlin.bbdc.inet.mera.metricServer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.{Executors, ScheduledExecutorService, TimeUnit}
import java.io._

import berlin.bbdc.inet.mera.metricServer.CommType.CommType

/* TODO: Separate data from traversal
 */

class Task(val parent: Operator, val subtask: Int, val host: String) {
  private val queueAlmostEmpty : Double = 0.2
  private val queueAlmostFull : Double = 0.8
  private val queueFull : Double = 1.0

  val writer = new PrintWriter(new File(f"/tmp/job_${parent.id}%s-$subtask%d.log"))
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
  def computeInfMetrics(): Boolean = {
    // TODO: test
    // TODO: check for key accesses where the key does not exist
    // TODO: return an error string
    var sum : Double = 0
    var outDistRaw : Map[Int, Double] = Map()
    for ((out, i) <- output.zipWithIndex) {
      val key = f"Network.Output.0.$i%d.buffersByChannel"
      if (!gauges.contains(key)) {
        println("-1-" + this.parent.id + "-" + this.subtask)
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
      val key = if (in.parent.commType == CommType.AllToAll) {
         f"Network.Output.0.$subtask%d.buffersByChannel"
      } else {
        in.inDistCtr += 1
        f"Network.Output.0.${in.inDistCtr}%d.buffersByChannel"
      }
      if (!in.gauges.contains(key)) {
        println("-2- " + this.parent.id + "-" + this.subtask + " from input " + i)
        println(key)
        println(in.gauges.keys)
        return false
      }
      val value = in.gauges(key).getMean
      inDistRaw += in.subtask -> value
      sum += value
    }
    for ((k, v) <- inDistRaw) {
      inDist += k -> v/sum
    }

    if (host == host) {

    } else if (!gauges.contains("buffers.inPoolUsage") || !gauges.contains("buffers.outPoolUsage")) {
      println(this.parent.id + "-" + this.subtask + "-3-")
      return false
    }
    outQueueSaturation = gauges("buffers.outPoolUsage").getMean
    val iQS = gauges("buffers.inPoolUsage").getMean
    // when the input queue is colocated with the output queue, the input queue equals the output queue.
    val iQSlist = iQS::input.filter(_.host == host)
                            .map(_.gauges("buffers.outPoolUsage").getMean)
    inQueueSaturation = iQSlist.max

    // rate
    if (!counters.contains("numRecordsOut")) {
      println(this.parent.id + "-" + this.subtask + "-4-")
      return false
    }
    if (!counters.contains("numRecordsIn")) {
      println(this.parent.id + "-" + this.subtask + "-4-")
      return false
    }
    val tmpOut = counters("numRecordsOut")
    val tmpIn = counters("numRecordsIn")
    selectivity = tmpOut.getMean/tmpIn.getMean
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
  def computeTargets() : Boolean = {
    // TODO: test
    // targetInputRate
    targetInRate = if (output.isEmpty) {
        capacity
      } else {
        targetOutRate = targetPartialOutRate.map(x => {
          if (!outDist.contains(x._1)) {
            return false
          }
          x._2 * outDist(x._1)
        }).min
        Math.min(targetOutRate/selectivity, capacity)
      }

    // targetPartialOutRate of inputs
    for (i <- input) {
      // why is inDist not set?
      if (!inDist.contains(i.subtask)) {
        //println("XXX", i, i.subtask, inDist)
        return false
      }
      i.targetPartialOutRate += subtask -> inDist(i.subtask) * targetInRate
    }
    true
  }
  override def toString: String = {
    val p = input.map(x => {x.parent.id + "-" + x.subtask}).foldRight("")(_ + "," + _)
    val n = output.map(x => {x.parent.id + "-" + x.subtask}).foldRight("")(_ + "," + _)
    f"Task-$subtask%s (In: $p%s Out: $n%s)"
  }
}

object CommType extends Enumeration {
    type CommType = Value
    val AllToAll, OneToOne = Value
}

class Operator(val id: String, val parallelism: Int, val commType : CommType) {
  val tasks: List[Task] = List.range(0, parallelism).map(new Task(this, _, "localhost")) //TODO: Fix for cluster
  var predecessor : List[Operator] = List()
  var successor : List[Operator] = List()
  def addSucc(op: Operator): Unit = {
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

class Host(val address: String, val taskManagerID: String) {
  var contained : Map[String, Task] = Map()
}

class Model(val n :Int) {
  // TODO: fix logging
  val LOG : Logger = LoggerFactory.getLogger("Model")
  val operators: Map[String, Operator] = setup()
  // Assuming single sink
  val sink: Operator = operators.values.filter(_.successor.isEmpty).head
  val src: Operator = operators.values.filter(_.predecessor.isEmpty).head
  val tasks: Iterable[Task] = operators.values.flatMap(_.tasks)
  // TODO: start when job starts, end when job ends.
  val schd : ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
  schd.scheduleAtFixedRate(new Runnable { override def run() = {traverseModel() } }, 10, 5, TimeUnit.SECONDS)

  def setup(): Map[String, Operator] = {
    // TODO: generate automatically
    val src = new Operator("Source: Socket Stream", 1, CommType.OneToOne)
    val filter = new Operator("Filter", 2, CommType.OneToOne)
    val flatMap = new Operator("Flat Map", 3, CommType.OneToOne)
    val map = new Operator("Map", 2, CommType.AllToAll)
    val aggregation = new Operator("aggregation", 2, CommType.OneToOne)
    val bottleneck = new Operator("bottleneck", 1, CommType.OneToOne)
    val sink = new Operator("Sink: Unnamed", 2, CommType.OneToOne)
    src.addSucc(flatMap)
    flatMap.addSucc(filter)
    filter.addSucc(map)
    map.addSucc(aggregation)
    aggregation.addSucc(bottleneck)
    bottleneck.addSucc(sink)
    Map(src.id -> src, flatMap.id -> flatMap, filter.id -> filter, map.id -> map, aggregation.id -> aggregation, bottleneck.id -> bottleneck, sink.id -> sink)
    // TODO: ask Carlo - how do you map from a list to a Map?
    //Map(List(src, flatMap, map, aggregation, bottleneck, sink).map(x => (x.id -> x)))
  }
  def traverseModel() {
    println("traversing START")
    tasks.foreach(_.inDistCtr = -1)
    if (!tasks.map(_.computeInfMetrics()).reduce(_ && _)) { println("Could not compute inferred metrics"); return}
    println("Computed inferred metrics")
    var op = sink
    var condition = true
    while(condition)  {
      println(op.id)
      op.tasks.foreach(_.computeTargets())
      if (op.predecessor.nonEmpty) {
        op = op.predecessor.head
      } else {
        condition = false
      }
    }
    //println(operators.map(x => (x._2.id, x._2.tasks)).flatMap((a : String, b : List[Task]) => { b.map(x => (a, x._2.subtask, x._2.targetRate)) } )
    writeTargets()
    println("traversing END")
  }

  override def toString: String = {
    val op = operators.values.map("\n" + _.toString)
    f"Model(\n$op%s\n)"
  }

  def update(timestamp : Long, metrics: List[(MetricKey, MetricSummary.NumberMetric)]): Unit = metrics.foreach({
    // TODO: Schedule after first metric has arrived
    case (k: TaskManagerTaskMetricKey, m: MetricSummary.NumberMetric) =>
      // TODO: fix hack
      // apparently even though two operators are merged in the same task chain, they still get different metric readings.
      // This requires further inquiry into how the task chain operates. Is there a buffer between the operators?
      var t : Task = null
      try {
        t = operators(k.opId)(k.taskId)
      } catch {
        case ex: NoSuchElementException  => {
          println("Exception operator '" + k.opId + "' not found!")
          return
        }
        case ex: IndexOutOfBoundsException => {
          println("Exception task '" + k.opId + "-" + k.taskId + "' not found!")
          return
        }
      }

      m match {
        case x: Gauge =>
          val gs: GaugeSummary = t.gauges.getOrElse(k.metric, new GaugeSummary(n))
          gs.add(timestamp, x)
          t.gauges += (k.metric -> gs)
        case x: Counter =>
          val cs : CounterSummary = t.counters.getOrElse(k.metric, new CounterSummary(n))
          cs.add(timestamp, x)
          t.counters += (k.metric -> cs)
        case x: Meter =>
          val ms : MeterSummary = t.meters.getOrElse(k.metric, new MeterSummary(n))
          ms.add(timestamp, x)
          t.meters += (k.metric -> ms)
      }
    case (k: UnknownMetricKey, _) => LOG.warn("Could not parse key: " + k.metric); println("Could not parse key: " + k.metric)
    case (jk: JobManagerMetricKey, _) =>
    case (tsk: TaskManagerStatusMetricKey, _) =>
    case (k: MetricKey, m: MetricSummary.NumberMetric) => LOG.warn("Ignoring key: " + k + "-" + m); println("Ignoring key: " + k + "-" + m)
  })
  def printGraph(): String = {
    this.toString
  }
  def printKeys(): String = {
    MetricKey.allKeys.map(_ + "\n").toString
  }
  def printMetrics(opId: String, taskId: Int): String = {
    val t = operators(opId)(taskId)
    t.counters.map(x => x + "\n") +"\n" +
    t.gauges.map(x => x + "\n") + "\n" +
    t.meters.map(x => x + "\n") + "\n"
  }
  def printInfMetrics(opId: String, taskId: Int): String = {
    val t = operators(opId)(taskId)
    "inDist:\t " + t.inDist.map(x => x._1 + "->" + x._2) + "\n" +
    "outDist:\t" + t.outDist + "\n" +
    "inRate:\t" + t.inRate + "\n" +
    "selectivity: \t" + t.selectivity + "\n"
  }
//  def printTargets(): String = {
//    return operators.values.map(x => (x.id, x.tasks)).flatMap({ case (a,b) => { b.map(x => f"$a%s, sub:${x.subtask}%d, inRate:${x.inRate}%f sel:${x.selectivity}%f, tIn:${x.targetInRate}%f, tOut:${x.targetOutRate}%f, cap:${x.capacity}%f, tPout:${x.targetPartialOutRate}%s-${x.input.size}%d tInDist:${x.inDist} tOutDist:${x.outDist}")}}).mkString("\n")
//  }
  def writeTargets(): Unit = {
    operators.values.map(x => (x.id, x.tasks)).foreach({ case (id : String, ts: List[Task]) =>
      ts.foreach(t => {
        val inRate = t.meters("numRecordsInPerSecond").getMean
        print("x")
        t.writer.write(f"${t.inQueueSaturation}%f->${t.outQueueSaturation}%f $id%s-${t.subtask}%d (inRate:$inRate%f sel:${t.selectivity}%f, tIn:${t.targetInRate}%f, tOut:${t.targetOutRate}%f, cap:${t.capacity}%f, tPout:${t.targetPartialOutRate}%s-${t.input.size}%d tInDist:${t.inDist}%s tOutDist:${t.outDist}%s\n")
        t.writer.flush()
      })})
  }
}
