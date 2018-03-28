package berlin.bbdc.inet.mera.server

import berlin.bbdc.inet.mera.server.metrics.MetricReceiver
import berlin.bbdc.inet.mera.server.model.{Model, ModelFileWriter}
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
  args.sliding(2, 2).toList.collect {
    case Array("--webHost", argWH: String) => webServiceHost = argWH
    case Array("--webPort", argWP: String) => webServicePort = argWP.toInt
    case Array("--flinkHost", argFH: String) => flinkHost = argFH
    case Array("--flinkPort", argFP: String) => flinkPort = argFP.toInt
  }

  LOG.info(s"WebService: $webServiceHost:$webServicePort, Flink: $flinkHost:$flinkPort")

  var folder: String = f"/tmp/mera_${System.currentTimeMillis()}"
  LOG.info("Writing info to " + folder)
  val topoServer = new TopologyServer(flinkHost, flinkPort)
  val model: Model = topoServer.createModelBuilder().createModel(1000)
  val mfw: ModelFileWriter = new ModelFileWriter(folder)
  mfw.writeGraph(model)
  // this line actually builds the models dynamically
  val models = topoServer.buildModels()
//  models foreach { case (_, v) => mfw.writeGraph(v) }
//  models foreach { case (_, v) => println(v.toString) }
  val webService = new WebService(model, webServiceHost, webServicePort, topoServer)
  MetricReceiver.start(model, mfw)
}
