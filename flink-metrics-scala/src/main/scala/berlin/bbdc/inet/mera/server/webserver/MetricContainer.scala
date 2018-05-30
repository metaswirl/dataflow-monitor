package berlin.bbdc.inet.mera.server.webserver

import java.io
import java.util.concurrent.{Executors, ScheduledFuture, TimeUnit}

import berlin.bbdc.inet.mera.server.model.Model
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.concurrent.TrieMap
import scala.collection.immutable.{List, Map, Seq}

class MetricContainer(model: Model) {

  private val LOG: Logger = LoggerFactory.getLogger(getClass)

  val cores: Int = Runtime.getRuntime.availableProcessors()
  LOG.debug(s"Metric executor started with ${cores + 1} cores")
  private val scheduler = Executors.newScheduledThreadPool(cores + 1)

  /**
    * MetricValue_1 - timestamp
    * MetricValue_2 - value of the metric at given timestamp
    */
  type MetricValue = (Long, Double)

  /**
    * Unique MetricKey is combined of
    * _1 - TaskId
    * _2 - MetricId
    */
  type MetricKey = (String, String)

  /**
    * Contains metrics exposed to UI
    * key   - metricKey
    * value - list of MetricData
    */
  private val metricsBuffer = new TrieMap[MetricKey, List[MetricValue]]

  /**
    * Contains all scheduled tasks
    * key   - metricKey
    * value - ScheduledFuture
    */
  private var metricsFutures: Map[MetricKey, ScheduledFuture[_]] = Map()

  lazy val topology: List[OperatorTopology] = {
    def getTasksOfOperator(id: String): Seq[TasksOfOperator] = model
      .operators(id)
      .tasks
      .map(t => {
        TasksOfOperator(t.id, t.input.map(_.source.id), t.output.map(_.target.id))
      })

    model.operators.map(x => OperatorTopology(x._1, getTasksOfOperator(x._1))).toList
  }

  /**
    * Obtains list of all available metrics
    */
  def metricsList: Vector[String] = model.tasks.values.flatMap(_.metrics.keys).toVector.distinct

  def getInitMetric: Set[MetricKey] = metricsFutures.keySet

  def postInitMetric(message: TaskInitMessage): io.Serializable = {
    if (metricsList.contains(message.metricId)) {
      initMetrics(message.taskIds, message.metricId, message.resolution)
      message
    } else {
      s"Metric ${message.metricId} cannot be initialized!"
    }
  }

  private def initMetrics(taskIds: List[String], metricId: String, resolution: Int): Unit = {
    //disable old tasks if exist
    disableFutures(taskIds, metricId)

    //if resolution is less than 1 - free the memory and return
    if (resolution < 1) taskIds foreach { id => metricsBuffer.remove((id, metricId)) }
    //if resolution is greater than 0 - schedule collecting metrics
    else {
      taskIds foreach { id =>

        val metricKey = (id, metricId)

        LOG.debug(s"Init collecting metric $metricKey")
        //initialize the metric in the buffer
        metricsBuffer.putIfAbsent(metricKey, List.empty[MetricValue])

        //schedule the task
        val f = scheduler.scheduleAtFixedRate(periodicTask(metricKey, resolution), resolution, resolution, TimeUnit.SECONDS)

        //store the Future
        metricsFutures += (metricKey -> f)
      }
    }
  }

  def disableFutures(taskIds: List[String], metricId: String): Unit = taskIds foreach { id =>
    metricsFutures get(id, metricId) match {
      case Some(f) =>
        LOG.debug(s"Cancel collecting metric ($id,$metricId)")
        f.cancel(false)
      case _ =>
    }
  }

  def periodicTask(metricKey: MetricKey, resolution: Int): Runnable = {
    new Runnable {
      override def run(): Unit = {
        //collect a new value to be added to the history
        val newValue: MetricValue = collectNewValueOfMetric(metricKey, resolution)

        //get the current value list
        val currentValuesList: List[MetricValue] = metricsBuffer(metricKey)

        //create new list with combined lists
        val updatedValuesList: List[MetricValue] = newValue :: currentValuesList

        //update the list in the history
        if (metricsBuffer.putIfAbsent(metricKey, updatedValuesList).isDefined) {
          metricsBuffer.replace(metricKey, updatedValuesList)
        }
      }
    }
  }

  /**
    * Obtains new values of a metric to be added to the history
    *
    * @return Map, where key is taskID and value is a Tuple(timestamp, metric_value)
    */
  def collectNewValueOfMetric(metricKey: MetricKey, resolution: Int): MetricValue =
    model
      .tasks(metricKey._1)
      .getMetricSummary(metricKey._2)
      .getMeanBeforeLastSeconds(resolution)

  def getMetricSince(metricKey: MetricKey, since: Long): List[MetricValue] = metricsBuffer(metricKey) filter {
    _._1 > since
  }
}

case class TasksOfOperator(id: String, input: List[String], output: List[String])

case class OperatorTopology(name: String, tasks: Seq[TasksOfOperator])
