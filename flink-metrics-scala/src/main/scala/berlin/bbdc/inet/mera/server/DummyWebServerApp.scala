package berlin.bbdc.inet.mera.server

import berlin.bbdc.inet.mera.server.dummywebserver.{DummyModelBuilder, DummyWebServer}
import com.typesafe.config.ConfigFactory
import org.slf4j.{Logger, LoggerFactory}

object DummyWebServerApp extends App {
  private val LOG: Logger = LoggerFactory.getLogger(getClass)

  var webServerHost = ConfigFactory.load.getString("webserver.host")
  var webServerPort = ConfigFactory.load.getInt("webserver.port")

  LOG.info(s"DummyWebServer: $webServerHost:$webServerPort")


  val builder = new DummyModelBuilder

  val model = builder.getModelFromJson("testData/twitter_model.json")
  val webServer = new DummyWebServer(model, webServerHost, webServerPort)
}
