package berlin.bbdc.inet.mera.monitor.topology

abstract class AbstractFlinkClient {
  def getJobs(): AllJobs
  def getJob(jobId: String): Job
  def getFullVertex(jobId: String, vertexId: String): FullVertex
  def establishConnection(): Unit
}

