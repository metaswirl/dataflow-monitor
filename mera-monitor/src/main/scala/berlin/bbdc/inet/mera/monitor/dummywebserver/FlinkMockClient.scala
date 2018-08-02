package berlin.bbdc.inet.mera.monitor.dummywebserver

import berlin.bbdc.inet.mera.monitor.topology.{AbstractFlinkClient, AllJobs, FullVertex, Job}
import berlin.bbdc.inet.mera.commons.tools.JsonUtils

import scala.io.Source
import scala.reflect.ClassTag

class FlinkMockClient(dirPath: String) extends AbstractFlinkClient {
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

