package berlin.bbdc.inet.mera.server

import berlin.bbdc.inet.mera.server.metrics.MetricReceiver
import berlin.bbdc.inet.mera.server.model.{Model, ModelFileWriter}
import berlin.bbdc.inet.mera.server.topology.TopologyServer
import berlin.bbdc.inet.mera.server.webservice.WebService
import org.slf4j.{Logger, LoggerFactory}

object Starter extends App {
  private val LOG: Logger = LoggerFactory.getLogger(getClass)

  var folder: String = f"/tmp/mera_${System.currentTimeMillis()}"
  LOG.info("Writing info to " + folder)
  val topoServer = new TopologyServer()
  val model : Model = topoServer.createModelBuilder().createModel(1000)
  val mfw : ModelFileWriter = new ModelFileWriter(folder)
  mfw.writeGraph(model)
  val webService = new WebService(model, "localhost", 12345)
  MetricReceiver.start(model, mfw)
}
