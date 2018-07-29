package berlin.bbdc.inet.mera.monitor

import berlin.bbdc.inet.mera.monitor.dummywebserver.{DummyMetricContainer, DummyModelBuilder}
import berlin.bbdc.inet.mera.monitor.webserver.WebServer
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

  val webServer = new WebServer(metricContainer, webServerHost, webServerPort)
}
