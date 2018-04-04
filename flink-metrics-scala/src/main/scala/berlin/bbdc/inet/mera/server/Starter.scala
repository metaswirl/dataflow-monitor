package berlin.bbdc.inet.mera.server

import berlin.bbdc.inet.mera.server.metrics.MetricReceiver
import berlin.bbdc.inet.mera.server.model.ModelFileWriter
import berlin.bbdc.inet.mera.server.topology.TopologyServer
import berlin.bbdc.inet.mera.server.webservice.WebService
import org.slf4j.{Logger, LoggerFactory}

object Starter extends App {
  private val LOG: Logger = LoggerFactory.getLogger(getClass)

  // default values
  var webServiceHost = "localhost"
  var webServicePort = 12345
  var flinkHost = "localhost"
  var flinkPort = 8081

  // sample program arguments: "--webHost localhost --webPort 12345 --flinkHost localhost --flinkPort 8081"
  parseArguments
  LOG.info(s"WebService: $webServiceHost:$webServicePort, Flink: $flinkHost:$flinkPort")

  var folder: String = f"/tmp/mera_${System.currentTimeMillis()}"
  LOG.info("Writing info to " + folder)

  val topoServer = new TopologyServer(flinkHost, flinkPort)
  //FIXME: models could become a Set instead of Map. That would require expanding the Model class
  val models = topoServer.buildModels()
//  models.values foreach(m => println(m.toString))
  //Currently work on a single model - extend to multiple in the future
  val mfw: ModelFileWriter = new ModelFileWriter(folder)
  val webService = new WebService(models.head._2, webServiceHost, webServicePort)
  MetricReceiver.start(models.head._2, mfw)

  private def parseArguments = {
    args.sliding(2, 2).toList.collect {
      case Array("--webHost", argWH: String) => webServiceHost = argWH
      case Array("--webPort", argWP: String) => webServicePort = argWP.toInt
      case Array("--flinkHost", argFH: String) => flinkHost = argFH
      case Array("--flinkPort", argFP: String) => flinkPort = argFP.toInt
    }
  }
}
