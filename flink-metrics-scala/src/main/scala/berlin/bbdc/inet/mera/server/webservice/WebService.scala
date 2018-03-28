package berlin.bbdc.inet.mera.server.webservice


import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse}
import akka.http.scaladsl.server.{Directives, StandardRoute}
import akka.stream.ActorMaterializer
import berlin.bbdc.inet.mera.common.JsonUtils
import berlin.bbdc.inet.mera.server.model.Model
import berlin.bbdc.inet.mera.server.topology.TopologyServer
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.{ExecutionContextExecutor, Future}


/*Calls to webserver defined in the following

     URL
      GET /data/operators
     Returns
      ["Source", "Map", ..., "Sink"]
     Description
      Operators should be sorted.

     URL
      GET /data/tasksOfOperator/{OperatorID}
     Returns
      [ {"id":"Map.0", "input":["Source.0", "Source.1"], "output":["FlatMap.0"]},
        {"id":"Map.1" ...},
        ...
      ]

     URL
      GET /data/metrics
     Returns
      [ "metric1", "metric2", ... ]


     URL
      GET /data/initMetric/{MetricID}&resolution=[in seconds]
     Description
      - Create buffer to store values of metric in the given resolution. Average to reach resolution.
      - No resolution below a single second

     URL
      GET /data/metric/{MetricID}
     Returns
      { "Map.0": "values":[ (time1, value1), (time2, value2), ... ], "late":[ (timeX, valueX), ... ],
        "Map.1": ...,
         ...
      }
     Description
      - If not initialized (above), return error.
      - Time is formatted as seconds since epoch

     URL
      GET /static/index.html
      GET /static/stuff.js
      GET /static/etc
     Description
      return static content
   */
class WebService(model: Model, host: String, port: Integer, topoServer: TopologyServer) extends Directives {

  private val LOG: Logger = LoggerFactory.getLogger(getClass)

  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  val route = {
    path("data" / "operators") {
      get {
        completeJson(model.operators.keys)
      }
    } ~
      pathPrefix("data" / "metric") {
        path(IntNumber) { num =>
          complete(s"Return metric $num")
        }
      } ~
      pathPrefix("data" / "tasksOfOperator") {
        path(Remaining) { id =>
          completeJson(model.operators(id).tasks)

        }
      } ~
      path("data" / "metrics") {
        get {
          complete("Return metrics")
        }
      } ~
      pathPrefix("data" / "initMetric") {
        path(Remaining) { id =>
          parameters('resolution.as[Int]) { resolution => {
            topoServer.scheduleFlinkPeriodicRequest(resolution)
            complete(s"Init metric $id, resolution $resolution seconds")
          }
          }
        }
      } ~
      path("jobList") {
        completeJson(topoServer.getJobsMap)
      } ~
      pathEndOrSingleSlash {
        get {
          getFromResource("static/index.html")
        }
      }

  }

  val bindingFuture: Future[Http.ServerBinding] = Http().bindAndHandle(route, this.host, this.port)

  def completeJson(obj: Any): StandardRoute = complete(HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, JsonUtils.toJson(obj))))
}
