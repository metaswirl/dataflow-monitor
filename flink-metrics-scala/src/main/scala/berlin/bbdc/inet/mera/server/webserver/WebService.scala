package berlin.bbdc.inet.mera.server.webserver

import java.util.concurrent.{Executors, ScheduledFuture, TimeUnit}

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes.NotFound
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Route, StandardRoute}
import akka.stream.ActorMaterializer
import berlin.bbdc.inet.mera.common.JsonUtils
import berlin.bbdc.inet.mera.server.model.Model
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.concurrent.TrieMap
import scala.collection.immutable.{List, Map, Seq}
import scala.concurrent.ExecutionContextExecutor

trait WebService {

  implicit val system: ActorSystem
  implicit val materializer: ActorMaterializer
  implicit val executionContext: ExecutionContextExecutor
  implicit val model: Model

  private val LOG: Logger = LoggerFactory.getLogger(getClass)

  /**
    *
    * @param taskId ID of the task
    * @param values values of taskId of a metric in a Tuple(timestamp, value)
    */
  private case class MetricData(taskId: String, values: List[(Long, Double)])

  private case class TasksOfOperator(id: String, input: List[String], output: List[String])

  /**
    * Assuming no more than 20 metrics to measure
    */
  private val scheduler = Executors.newScheduledThreadPool(20)

  /**
    * Contains metrics exposed to UI
    * key   - metricId
    * value - list of MetricData
    */
  private val metricsBuffer = new TrieMap[String, List[MetricData]]

  /**
    * Contains all scheduled tasks
    */
  var metricsFutures: Map[String, ScheduledFuture[_]] = Map()

  val route: Route =
    // Returns list of operators
    path("data" / "operators") {
      get {
        completeJson(model.operators.keys)
      }
    } ~
    // Returns tasks for a given operator
      pathPrefix("data" / "tasksOfOperator") {
        path(Remaining) { id =>
          completeJson(getTasksOfOperators(id))
        }
      } ~
    // Returns history of a given metric
      pathPrefix("data" / "metric") {
        path(Remaining) { id =>
          getMetricById(id)
        }
      } ~
    // Returns all available metrics
      path("data" / "metrics") {
        get {
          completeJson(model.tasks.flatMap(_.metrics.keys).toVector.distinct)
        }
      } ~
    // Initializes a metric
      pathPrefix("data" / "initMetric") {
        path(Remaining) { id =>
          parameters('resolution.as[Int]) { resolution => {
            completeJson(initMetric(id, resolution))
          }
          }
        }
      } ~
    // Returns swagger json
      path("swagger") {
        get {
          getFromResource("static/swagger/swagger.json")
        }
      } ~
    // Returns home page
      (get & pathEndOrSingleSlash) {
        getFromResource("static/index.html")
      } ~ {
      getFromResourceDirectory("static")
    }

  private def getMetricById(id: String) = {
    if (!metricsBuffer.contains(id)) {
      complete(HttpResponse(NotFound, entity = "This metric has not been initialized!"))
    } else {
      completeJson(metricsBuffer(id))
    }
  }

  private def getTasksOfOperators(id: String): Seq[TasksOfOperator] = {
    model
      .operators(id.replace("%20", " "))
      .tasks
      .map(t => {
        TasksOfOperator(t.id, t.input.map(_.source), t.output.map(_.target))
      })
  }

  private def completeJson(obj: Any): StandardRoute =
    complete(HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, JsonUtils.toJson(obj))))

  private class PeriodicTask(id: String, resolution: Int) extends Runnable {
    override def run(): Unit = {
      //collect map of new values to be added to history
      val newValues: Map[String, (Long, Double)] = model.tasks.map(
        t => t.id -> t.getMetricSummary(id).getMeanBeforeLastSeconds(resolution)).toMap

      //get the current value lists
      val currentValuesList: List[MetricData] = metricsBuffer(id)

      //create new list for combined lists
      val newValuesList = List.newBuilder[MetricData]

      //for each task in newValues add the value with its timestamp to the history
      for (task <- newValues.keySet) {
        currentValuesList.find(_.taskId == task) match {
          case Some(m) => newValuesList += MetricData(task, m.values :+ newValues(task))
          case _ => newValuesList += MetricData(task, List(newValues(task)))
        }
      }

      //update the list in the history
      if (metricsBuffer.putIfAbsent(id, newValuesList.result).isDefined) {
        metricsBuffer.replace(id, newValuesList.result)
      }
    }
  }

  def initMetric(id: String, resolution: Int): Map[String, Any] = {
    //disable old task if exists
    disableFuture(id)

    //initialize the metric in the buffer
    metricsBuffer.putIfAbsent(id, List.empty[MetricData])

    //schedule the task
    val f = scheduler.scheduleAtFixedRate(new PeriodicTask(id, resolution), resolution, resolution, TimeUnit.SECONDS)

    //store the Future
    metricsFutures += (id -> f)

    //return message to client
    Map("id" -> id, "resolution" -> resolution)
  }

  def disableFuture(id: String): Unit = metricsFutures get id match {
    case Some(f) => f.cancel(false)
    case None =>
  }

}
