package berlin.bbdc.inet.mera.server

import berlin.bbdc.inet.mera.server.metrics.MetricReceiver
import berlin.bbdc.inet.mera.server.model.ModelFileWriter
import berlin.bbdc.inet.mera.server.topology.TopologyServer
import berlin.bbdc.inet.mera.server.webserver.{MetricContainer, WebServer}
import com.typesafe.config.ConfigFactory
import org.slf4j.{Logger, LoggerFactory}

object Starter extends App {
  private val LOG: Logger = LoggerFactory.getLogger(getClass)

  val webServerHost = ConfigFactory.load.getString("webserver.host")
  val webServerPort = ConfigFactory.load.getInt("webserver.port")
  val flinkHost     = ConfigFactory.load.getString("flink.host")
  val flinkPort     = ConfigFactory.load.getInt("flink.port")

  LOG.info(s"WebServer: $webServerHost:$webServerPort, Flink: $flinkHost:$flinkPort")

  var folder: String = f"/tmp/mera_${System.currentTimeMillis()}"
  LOG.info("Writing info to " + folder)

  val topoServer = new TopologyServer(flinkHost, flinkPort)
  val models = topoServer.buildModels()
  val mfw: ModelFileWriter = new ModelFileWriter(folder, writeMetrics=true)
  val modelProxy = new MetricContainer(models.head._2)
  val webServer = new WebServer(modelProxy, webServerHost, webServerPort)
  MetricReceiver.start(models.head._2, mfw)
}
