package berlin.bbdc.inet.mera.server

import java.util.concurrent.{Executors, ScheduledExecutorService, TimeUnit}

import berlin.bbdc.inet.mera.server.metrics.MetricReceiver
import berlin.bbdc.inet.mera.server.model.{Model, ModelBuilder, ModelFileWriter, ModelTraversal}
import berlin.bbdc.inet.mera.server.topology.TopologyServer
import berlin.bbdc.inet.mera.server.webservice.WebService

object Starter {
  def main(args: Array[String]): Unit = {
    val topoServer = new TopologyServer()
    val model : Model = topoServer.createModelBuilder().createModel(1000)
    val mfw : ModelFileWriter = new ModelFileWriter(f"/tmp/mera_${System.currentTimeMillis()}")
    val modelTraversal = new ModelTraversal(model, mfw)
    val webService = new WebService(model)
    val schd : ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    mfw.writeGraph(model)
    MetricReceiver.start(model, mfw)
    val modelUpdate = schd.scheduleAtFixedRate(new Runnable { override def run() = { modelTraversal.traverseModel() } }, 10, 5, TimeUnit.SECONDS)
  }
}

