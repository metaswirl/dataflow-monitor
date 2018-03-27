package berlin.bbdc.inet.mera.server

import java.util.concurrent.{Executors, ScheduledExecutorService, TimeUnit}

import berlin.bbdc.inet.mera.server.metrics.MetricReceiver
import berlin.bbdc.inet.mera.server.model.{Model, ModelBuilder, ModelFileWriter, ModelTraversal}
import berlin.bbdc.inet.mera.server.topology.TopologyServer
import berlin.bbdc.inet.mera.server.webservice.WebService
import org.slf4j.{Logger, LoggerFactory}

object Starter extends App {
  val LOG: Logger = LoggerFactory.getLogger("Starter")

  var folder: String = f"/tmp/mera_${System.currentTimeMillis()}"
  LOG.info("Writing info to " + folder)
  val mfw : ModelFileWriter = new ModelFileWriter(folder)
  val topoServer = new TopologyServer()
  val model : Model = topoServer.createModelBuilder().createModel(1000)
  mfw.writeGraph(model)
  val webService = new WebService(model)
  MetricReceiver.start(model, mfw)
}

