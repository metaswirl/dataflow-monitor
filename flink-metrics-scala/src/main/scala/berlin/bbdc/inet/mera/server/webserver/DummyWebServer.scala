package berlin.bbdc.inet.mera.server.webserver

import java.util.concurrent.{Executors, TimeUnit}

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import berlin.bbdc.inet.mera.server.model.Model
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.ExecutionContextExecutor


class DummyWebServer(m: Model, host: String, port: Int)
  extends WebService {
  private val LOG: Logger = LoggerFactory.getLogger(getClass)

  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher
  implicit val model: Model = m

  Http().bindAndHandle(route, host, port)

  override def initMetric(id: String, resolution: Int): Map[String,Any] = {
    disableFuture(id)

    val scheduler = Executors.newScheduledThreadPool(1)
    val task = new Runnable {
      override def run(): Unit = {
        LOG.info(s"Periodic task for $id")
      }
    }
    val f = scheduler.scheduleAtFixedRate(task, resolution, resolution, TimeUnit.SECONDS)
    metricsFutures += (id -> f)
    Map("id" -> id, "resolution" -> resolution)
  }
}
