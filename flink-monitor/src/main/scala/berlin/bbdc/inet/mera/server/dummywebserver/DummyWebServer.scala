package berlin.bbdc.inet.mera.server.dummywebserver

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import berlin.bbdc.inet.mera.server.model.Model
import berlin.bbdc.inet.mera.server.webserver.{Loggable, MetricContainer, WebService}

import scala.concurrent.ExecutionContextExecutor


class DummyWebServer(m: Model, container: MetricContainer, host: String, port: Int)
  extends WebService with Loggable {

  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher
  implicit val metricContainer: MetricContainer = container

  private val myLoggedRoute = logRequestResult(Logging.InfoLevel, route)
  Http().bindAndHandle(myLoggedRoute, host, port)
}
