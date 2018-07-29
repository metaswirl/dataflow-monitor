package berlin.bbdc.inet.mera.monitor

import berlin.bbdc.inet.mera.monitor.akkaserver.AkkaMessenger
import berlin.bbdc.inet.mera.monitor.topology.TopologyServer
import berlin.bbdc.inet.mera.monitor.webserver.{MetricContainer, WebServer}
import com.typesafe.config.ConfigFactory
import org.slf4j.{Logger, LoggerFactory}

object Starter extends App {
  private val LOG: Logger = LoggerFactory.getLogger(getClass)

  val webServerHost = ConfigFactory.load.getString("webserver.host")
  val webServerPort = ConfigFactory.load.getInt("webserver.port")
  val flinkHost     = ConfigFactory.load.getString("flink.host")
  val flinkPort     = ConfigFactory.load.getInt("flink.port")
  LOG.info(s"WebServer: $webServerHost:$webServerPort, Flink: $flinkHost:$flinkPort")

  val topoServer = new TopologyServer(flinkHost, flinkPort)
  val models = topoServer.buildModels()
  val modelProxy = new MetricContainer(models.head._2)
  val webServer = new WebServer(modelProxy, webServerHost, webServerPort)
  AkkaMessenger.start(models.head._2)
}
