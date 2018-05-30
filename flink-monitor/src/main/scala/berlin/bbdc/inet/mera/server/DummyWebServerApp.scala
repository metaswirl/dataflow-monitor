package berlin.bbdc.inet.mera.server

import berlin.bbdc.inet.mera.server.dummywebserver.{DummyMetricContainer, DummyModelBuilder, DummyWebServer}
import com.typesafe.config.ConfigFactory
import org.slf4j.{Logger, LoggerFactory}

object DummyWebServerApp extends App {
  private val LOG: Logger = LoggerFactory.getLogger(getClass)

  val webServerHost = ConfigFactory.load.getString("webserver.host")
  val webServerPort = ConfigFactory.load.getInt("webserver.port")
  LOG.info(s"DummyWebServer: $webServerHost:$webServerPort")

  val builder = new DummyModelBuilder
  val model = builder.getModelFromJson("testData/twitter_model.json")
  val metricContainer = new DummyMetricContainer(model)

  val webServer = new DummyWebServer(model, metricContainer, webServerHost, webServerPort)
}
