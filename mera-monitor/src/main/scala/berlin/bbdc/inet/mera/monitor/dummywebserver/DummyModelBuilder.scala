package berlin.bbdc.inet.mera.monitor.dummywebserver

import berlin.bbdc.inet.mera.commons.tools.JsonUtils
import berlin.bbdc.inet.mera.monitor.metrics.CounterSummary
import berlin.bbdc.inet.mera.monitor.model.Model
import berlin.bbdc.inet.mera.monitor.model.ModelBuilder
import berlin.bbdc.inet.mera.monitor.topology._
import scala.reflect.ClassTag

import scala.io.Source

object DummyModelBuilder {
  def getModelFromJson(dirPath: String): Model = {
    val topoServer = new TopologyServer("", 0, new MockFlinkClient(dirPath))
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

private class MockFlinkClient(dirPath: String) extends AbstractFlinkClient {
  private val BASE_URL = dirPath + "/twitter"
  private val BASE_ENDING = ".json"
  private val JOBS_URL = BASE_URL + "_jobs" + BASE_ENDING
  private def JOB_URL(jobId: String): String = BASE_URL + "_job_" + jobId + BASE_ENDING
  private def VERTEX_URL(jobId: String, vertexId: String): String =
    BASE_URL + "_job_" + jobId + "_vertex_" + vertexId + BASE_ENDING

  override def getJobs(): AllJobs = getContent[AllJobs](JOBS_URL)

  override def getJob(jobId: String): Job = getContent[Job](JOB_URL(jobId))

  override def getFullVertex(jobId: String, vertexId: String): FullVertex =
    getContent[FullVertex](VERTEX_URL(jobId, vertexId))

  override def establishConnection(): Unit = {}

  private def getContent[T:ClassTag](path: String): T =
    JsonUtils.fromJson[T](Source.fromURL(getClass.getClassLoader.getResource(path)).mkString)
}
