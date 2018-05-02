package berlin.bbdc.inet.mera.server.dummywebserver

import berlin.bbdc.inet.mera.common.JsonUtils
import berlin.bbdc.inet.mera.server.metrics.CounterSummary
import berlin.bbdc.inet.mera.server.model.{Model, ModelBuilder}
import berlin.bbdc.inet.mera.server.topology.{Job, TopologyServer}

import scala.io.Source

class DummyModelBuilder {

  def getModelFromJson(path: String): Model = {
    val model = buildModelFromJson(path)
    fillMetrics(model)
  }

  private def buildModelFromJson(path: String) = {
    val jobJson = Source.fromURL(getClass.getClassLoader.getResource(path))
    val job = JsonUtils.fromJson[Job](jobJson.mkString)
    val modelBuilder = new ModelBuilder
    //iterate over vertices and for each add a new operator to the model
    job.vertices foreach (v => modelBuilder.addSuccessor(v.name, v.parallelism, TopologyServer.findCommTypeById(v.id, job.plan), isLoadShedder = false))
    modelBuilder.createModel(1000)
  }


  def fillMetrics(model: Model): Model = {
    model.tasks.values.foreach(t => t.metrics +=
      ("numBytesInRemote"           -> new CounterSummary(1),
        "numRecordsInPerSecond"     -> new CounterSummary(1),
        "numBytesInLocal"           -> new CounterSummary(1),
        "numBytesInRemotePerSecond" -> new CounterSummary(1),
        "buffers.inPoolUsage"       -> new CounterSummary(1),
        "numBytesOut"               -> new CounterSummary(1),
        "numBytesOutPerSecond"      -> new CounterSummary(1),
        "numRecordsOut"             -> new CounterSummary(1),
        "currentLowWatermark"       -> new CounterSummary(1),
        "numRecordsIn"              -> new CounterSummary(1),
        "buffers.outPoolUsage"      -> new CounterSummary(1),
        "checkpointAlignmentTime"   -> new CounterSummary(1),
        "numRecordsOutPerSecond"    -> new CounterSummary(1),
        "numBytesInLocalPerSecond"  -> new CounterSummary(1),
        "buffers.outputQueueLength" -> new CounterSummary(1),
        "buffers.inputQueueLength"  -> new CounterSummary(1)
      ))
    model
  }
}
