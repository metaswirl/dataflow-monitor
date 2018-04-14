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

import scala.collection.immutable.{List, Map, Seq}
import scala.concurrent.ExecutionContextExecutor

trait WebService {

  implicit val system: ActorSystem
  implicit val materializer: ActorMaterializer
  implicit val executionContext: ExecutionContextExecutor
  implicit val model: Model

  /*
  Contains metrics exposed to UI
  key - metricId
  value - list of tuples containing taskId and value of the metric
 */
  var metricsBuffer: Map[String, List[(String, Double)]] = Map()

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
      pathEndOrSingleSlash {
        get {
          getFromResource("static/index.html")
        }
      }

  private def completeJson(obj: Any): StandardRoute = complete(HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, JsonUtils.toJson(obj))))

  private def initMetric(id: String, resolution: Int): Boolean = {
    //disable old task if exists
    disableFuture(id)

    val scheduler = Executors.newScheduledThreadPool(1)
    val task = new Runnable {
      override def run(): Unit = {
        //collect list of all tasks and metric values
        val list: List[(String, Double)] = model.tasks.toList.map(
          t => (t.id, t.getMetricSummary(id).getMeanLastSeconds(resolution)))
        //store the list
        metricsBuffer += (id -> list)
      }
    }
    //schedule the task
    val f = scheduler.scheduleAtFixedRate(task, resolution, resolution, TimeUnit.SECONDS)
    //store the Future
    metricsFutures += (id -> f)
    true
  }

  private def disableFuture(id: String): Unit = metricsFutures get id match {
    case Some(f) => f.cancel(false)
    case None =>
  }

}

