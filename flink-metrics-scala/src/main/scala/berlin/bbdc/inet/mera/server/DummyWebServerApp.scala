package berlin.bbdc.inet.mera.server

import berlin.bbdc.inet.mera.common.JsonUtils
import berlin.bbdc.inet.mera.server.metrics.CounterSummary
import berlin.bbdc.inet.mera.server.model.CommType.CommType
import berlin.bbdc.inet.mera.server.model.{CommType, Model, ModelBuilder}
import berlin.bbdc.inet.mera.server.topology.{Job, Plan}
import berlin.bbdc.inet.mera.server.webserver.DummyWebServer
import org.slf4j.{Logger, LoggerFactory}

import scala.io.Source

object DummyWebServerApp extends App {
  private val LOG: Logger = LoggerFactory.getLogger(getClass)

  // default values
  var webServerHost = "localhost"
  var webServerPort = 12345

  LOG.info(s"DummyWebServer: $webServerHost:$webServerPort")


  val model = DummyWebServerUtils.getModelFromJson("testData/twitter_model.json")
  DummyWebServerUtils.fillMetrics(model)
  val webServer = new DummyWebServer(model, webServerHost, webServerPort)
}

object DummyWebServerUtils {

  def getModelFromJson(path: String): Model = {
    val jobJson = Source.fromURL(getClass.getClassLoader.getResource(path))
    val job = JsonUtils.fromJson[Job](jobJson.mkString)
    val modelBuilder = new ModelBuilder
    //iterate over vertices and for each add a new operator to the model
    job.vertices foreach (v => modelBuilder.addSuccessor(v.name, v.parallelism, DummyWebServerUtils.findCommTypeById(v.id, job.plan)))
    modelBuilder.createModel(1000)
  }

  def findCommTypeById(id: String, plan: Plan): CommType = {
    plan.nodes.foreach(n => {
      if (n.inputs != null) {
        n.inputs.foreach(i => {
          if (i.id.equals(id)) {
            i.shipStrategy match {
              case "HASH" | "RANGE" =>
                return CommType.POINTWISE
              case "REBALANCE" | "FORWARD" =>
                return CommType.ALL_TO_ALL
              case t =>
                throw new IllegalStateException("Unknown CommType " + t)
            }
          }
        })
      }
    })
    CommType.UNCONNECTED
  }

  def fillMetrics(model: Model): Unit = {
    model.tasks.foreach(t => t.metrics +=
      ("numRecordsOut" -> new CounterSummary(1), "numRecordsIn" -> new CounterSummary(1)))
  }
}
