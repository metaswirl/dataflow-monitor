package berlin.bbdc.inet.mera.server.webserver


import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import berlin.bbdc.inet.mera.server.model.Model

import scala.collection.immutable.Map
import scala.concurrent.ExecutionContextExecutor


class WebServer(m: Model, host: String, port: Int)
  extends WebService {

  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher
  implicit val model: Model = m

  Http().bindAndHandle(route, host, port)

  override def collectNewValuesOfMetric(id: String, resolution: Int): Map[String, (Long, Double)] = {
    model.tasks.map(
      t => t.id -> t.getMetricSummary(id).getMeanBeforeLastSeconds(resolution)).toMap
  }
}
