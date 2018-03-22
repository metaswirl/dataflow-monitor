package berlin.bbdc.inet.mera.server.webservice

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import berlin.bbdc.inet.mera.server.model.Model

import scala.concurrent.Future

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

class WebService(model : Model) {

  // Sample server from Akka's website
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val serverSource = Http().bind(interface = "localhost", port = 12345)

  val requestHandler: HttpRequest => HttpResponse = {
    case HttpRequest(GET, Uri.Path("/"), _, _, _) =>
      HttpResponse(entity = HttpEntity(
        ContentTypes.`text/html(UTF-8)`,
        "<html><body>Hello world!</body></html>"))

    case HttpRequest(GET, Uri.Path("/ping"), _, _, _) =>
      HttpResponse(entity = "PONG!")

    case HttpRequest(GET, Uri.Path("/crash"), _, _, _) =>
      sys.error("BOOM!")

    case r: HttpRequest =>
      r.discardEntityBytes() // important to drain incoming HTTP Entity stream
      HttpResponse(404, entity = "Unknown resource!")
  }

  val bindingFuture: Future[Http.ServerBinding] =
    serverSource.to(Sink.foreach { connection =>
      println("Accepted new connection from " + connection.remoteAddress)

      connection handleWithSyncHandler requestHandler
      // this is equivalent to
      // connection handleWith { Flow[HttpRequest] map requestHandler }
    }).run()

}
