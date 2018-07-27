package berlin.bbdc.inet.mera.monitor.webserver


import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer

import scala.concurrent.ExecutionContextExecutor


class WebServer(proxy: MetricContainer, host: String, port: Int)
  extends WebService with Loggable with CorsSupport {

  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher
  implicit val metricContainer: MetricContainer = proxy

  private val myLoggedRoute = logRequestResult(Logging.InfoLevel, route)
  Http().bindAndHandle(corsHandler(myLoggedRoute), host, port)
}
