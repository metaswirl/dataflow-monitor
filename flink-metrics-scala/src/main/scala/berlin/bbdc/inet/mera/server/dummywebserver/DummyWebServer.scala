package berlin.bbdc.inet.mera.server.dummywebserver

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import berlin.bbdc.inet.mera.server.model.Model
import berlin.bbdc.inet.mera.server.webserver.WebService
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.ExecutionContextExecutor
import scala.util.Random


class DummyWebServer(m: Model, host: String, port: Int)
  extends WebService {
  private val LOG: Logger = LoggerFactory.getLogger(getClass)

  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher
  implicit val model: Model = m

  Http().bindAndHandle(route, host, port)

  /**
    * Obtains new values of a metric to be added to the history
    *
    * @return Map, where key is taskID and value a Tuple(timestamp, metric_value)
    */
  override def collectNewValuesOfMetric(id: String, resolution: Int): Map[String, (Long, Double)] = {
    //get task IDs
    val tasks = model.tasks.map(_.id)

    val map = Map.newBuilder[String, (Long, Double)]
    val r = Random

    //for each task generate new tuple and add to map
    tasks foreach { t => {
      val ts = System.currentTimeMillis()
      val value = r.nextDouble
      map += (t -> (ts, value))
      }
    }

    map.result
  }

}
