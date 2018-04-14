package berlin.bbdc.inet.mera.server

import berlin.bbdc.inet.mera.common.JsonUtils
import berlin.bbdc.inet.mera.server.model.Model
import berlin.bbdc.inet.mera.server.webserver.WebServer
import org.slf4j.{Logger, LoggerFactory}

import scala.io.Source

object WebServerApp extends App {
  private val LOG: Logger = LoggerFactory.getLogger(getClass)

  // default values
  var webServiceHost = "localhost"
  var webServicePort = 12345

  // sample program arguments: "--webHost localhost --webPort 12345"
  parseArguments
  LOG.info(s"WebService: $webServiceHost:$webServicePort")

  val source = Source.fromURL(getClass.getClassLoader.getResource("testData/model.json"))
  val model = JsonUtils.fromJson[Model](source.mkString)
  val webServer = new WebServer(model, webServiceHost, webServicePort)

  private def parseArguments = {
    args.sliding(2, 2).toList.collect {
      case Array("--webHost", argWebHost: String) => webServiceHost = argWebHost
      case Array("--webPort", argWebPort: String) => webServicePort = argWebPort.toInt
    }
  }
}
