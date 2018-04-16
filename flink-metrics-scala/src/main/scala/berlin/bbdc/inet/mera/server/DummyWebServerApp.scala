package berlin.bbdc.inet.mera.server

import berlin.bbdc.inet.mera.common.JsonUtils
import berlin.bbdc.inet.mera.server.model.Model
import berlin.bbdc.inet.mera.server.webserver.DummyWebServer
import org.slf4j.{Logger, LoggerFactory}

import scala.io.Source

object DummyWebServerApp extends App {
  private val LOG: Logger = LoggerFactory.getLogger(getClass)

  // default values
  var webServiceHost = "localhost"
  var webServicePort = 12345

  LOG.info(s"WebService: $webServiceHost:$webServicePort")

  val source = Source.fromURL(getClass.getClassLoader.getResource("testData/model.json"))
  val model = JsonUtils.fromJson[Model](source.mkString)
  val webServer = new DummyWebServer(model, webServiceHost, webServicePort)
}
