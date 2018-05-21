package berlin.bbdc.inet.mera.server.webserver

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Route, StandardRoute}
import akka.stream.ActorMaterializer
import berlin.bbdc.inet.mera.common.JsonUtils
import com.typesafe.config.ConfigFactory

import scala.collection.immutable.List
import scala.concurrent.ExecutionContextExecutor

trait WebService {

  implicit val system: ActorSystem
  implicit val materializer: ActorMaterializer
  implicit val executionContext: ExecutionContextExecutor
  implicit val metricContainer: MetricContainer

  private val STATIC_PATH = ConfigFactory.load.getString("static.path")

  val route: Route =
  //Return the topology of the model
    path("data" / "topology") {
      get {
        completeJson(metricContainer.topology)
      }
    } ~
      // Returns all available metrics
      path("data" / "metrics") {
        get {
          completeJson(metricContainer.metricsList)
        }
      } ~
      path("data" / "metrics" / "tasks" / "init") {
        get {
          completeJson(metricContainer.getInitMetric)
        } ~
          post {
            entity(as[String]) { json =>
              val message = JsonUtils.fromJson[TaskInitMessage](json)
              completeJson(metricContainer.postInitMetric(message))
            }
          }
      } ~
      // Returns history of a given metric
      pathPrefix("data" / "metrics" / "task") {
        parameters('metricId.as[String], 'since.as[Long], 'taskId.as[String]) { (metricId, since, taskId) =>
          completeJson(metricContainer.getMetricSince((taskId, metricId), since))
        }
      } ~
      // Returns swagger json
      path("swagger") {
        get {
          getFromResource(STATIC_PATH + "/swagger/swagger.json")
        }
      } ~
      // Returns home page
      (get & pathEndOrSingleSlash) {
        getFromResource(STATIC_PATH + "/index.html")
      } ~ {
      getFromResourceDirectory(STATIC_PATH)
    }

  private def completeJson(obj: Any): StandardRoute =
    complete(HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, JsonUtils.toJson(obj))))

}

case class TaskInitMessage(taskIds: List[String], metricId: String, resolution: Int)
