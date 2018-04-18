package berlin.bbdc.inet.mera.server.webserver

import java.util.concurrent.{Executors, ScheduledFuture, TimeUnit}

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Route, StandardRoute}
import akka.stream.ActorMaterializer
import berlin.bbdc.inet.mera.common.JsonUtils
import berlin.bbdc.inet.mera.server.metrics.MetricSummary
import berlin.bbdc.inet.mera.server.model.Model

import scala.collection.concurrent.TrieMap
import scala.collection.immutable.{List, Map, Seq}
import scala.concurrent.ExecutionContextExecutor

trait WebService {

  implicit val system: ActorSystem
  implicit val materializer: ActorMaterializer
  implicit val executionContext: ExecutionContextExecutor
  implicit val model: Model

  /*
  * Contains metrics exposed to UI
  * key   - metricId
  * value - map:
  *             - key   - taskId
  *             - value - history of values
 */
  val metricsBuffer = new TrieMap[String, Map[String, List[Double]]]

  /*
    Contains all scheduled tasks
   */
  var metricsFutures: Map[String, ScheduledFuture[_]] = Map()

  val route: Route =
    path("data" / "operators") {
      get {
        completeJson(model.operators.keys)
      }
    } ~
      pathPrefix("data" / "metric") {
        path(Remaining) { id =>
          val list: Seq[(String, MetricSummary[_])] = model.tasks.toVector
            .filter(_.metrics.contains(id))
            .map(t => Tuple2[String, MetricSummary[_]](t.id, t.getMetricSummary(id)))
          completeJson(list)
        }
      } ~
      pathPrefix("data" / "tasksOfOperator") {
        path(Remaining) { id =>
          completeJson(model.operators(id.replace("%20", " ")).tasks)
        }
      } ~
      path("data" / "metrics") {
        get {
          completeJson(metricsBuffer.keys.toVector)
        }
      } ~
      pathPrefix("data" / "initMetric") {
        path(Remaining) { id =>
          parameters('resolution.as[Int]) { resolution => {
            val result = initMetric(id, resolution)
            completeJson(Map("metric" -> id, "resolution" -> resolution, "result" -> result))
          }
          }
        }
      } ~
      (get & pathEndOrSingleSlash) {
        getFromResource("static/index.html")
      } ~ {
        getFromResourceDirectory("static")
    }

  private def completeJson(obj: Any): StandardRoute = complete(HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, JsonUtils.toJson(obj))))

  def initMetric(id: String, resolution: Int): Boolean = {
    //disable old task if exists
    disableFuture(id)

    val scheduler = Executors.newScheduledThreadPool(1)
    val task = new Runnable {
      override def run(): Unit = {
        //collect list of all tasks and metric values
        val list: List[(String, Double)] = model.tasks.toList.map(
          t => (t.id, t.getMetricSummary(id).getMeanBeforeLastSeconds(resolution)))
        //get the current value lists
        val map = metricsBuffer.get(id)
        val mapBuilder = Map.newBuilder[String, List[Double]]
        //for each taskId get the list and append the new element
        for (taskTuple <- list) {
          val valueList = map.get(taskTuple._1)
          mapBuilder += (taskTuple._1 -> (valueList :+ taskTuple._2))
        }
        //update the lists in the map
        if(metricsBuffer.putIfAbsent(id, mapBuilder.result).isDefined) {
          metricsBuffer.replace(id, mapBuilder.result)
        }
      }
    }
    //schedule the task
    val f = scheduler.scheduleAtFixedRate(task, resolution, resolution, TimeUnit.SECONDS)
    //store the Future
    metricsFutures += (id -> f)
    true
  }

  def disableFuture(id: String): Unit = metricsFutures get id match {
    case Some(f) => f.cancel(false)
    case None =>
  }

}

