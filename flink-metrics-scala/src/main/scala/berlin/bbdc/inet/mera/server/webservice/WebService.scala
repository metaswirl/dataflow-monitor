package berlin.bbdc.inet.mera.server.webservice


import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives
import akka.stream.ActorMaterializer
import berlin.bbdc.inet.mera.server.model.Model
import org.slf4j.{Logger, LoggerFactory}
import spray.json._


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
trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  //add formats for your classes here

  //  implicit val personFormat = jsonFormat2(class)
}

class WebService(model: Model, host: String, port: Integer) extends Directives with JsonSupport {

  private val LOG: Logger = LoggerFactory.getLogger(getClass)

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val route = {
    path("data" / "operators") {
      get {
        complete(model.operators.keys)
      }
    } ~
      pathPrefix("data" / "metric") {
        path(IntNumber) { num =>
          complete(s"Return metric $num")
        }
      } ~
      pathPrefix("data" / "tasksOfOperator") {
        path(Remaining) { id =>
          complete("Return tasks of operator " + id)
        }
      } ~
      path("data" / "metrics") {
        get {
          complete("Return metrics")
        }
      } ~
      pathPrefix("data" / "initMetric") {
        path(Remaining) { id =>
          parameters('resolution.as[Int]) { resolution =>
            complete(s"Init metric $id, resolution $resolution seconds")
          }
        }
      } ~
      pathEndOrSingleSlash {
        get {
          getFromResource("static/index.html")
        }
      }

  }

  val bindingFuture = Http().bindAndHandle(route, this.host, this.port)

}
