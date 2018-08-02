package berlin.bbdc.inet.mera.monitor.dummywebserver

import berlin.bbdc.inet.mera.monitor.metrics.CounterSummary
import berlin.bbdc.inet.mera.monitor.model.Model
import berlin.bbdc.inet.mera.monitor.topology._

object DummyModelBuilder {
  def getModelFromJson(dirPath: String): Model = {
    val topoServer = new TopologyServer("", 0, new FlinkMockClient(dirPath))
    val models = topoServer.buildModels()
    fillMetrics(models.head._2)
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

