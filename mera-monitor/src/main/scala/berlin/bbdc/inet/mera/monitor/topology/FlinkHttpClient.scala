package berlin.bbdc.inet.mera.monitor.topology

import java.net.ConnectException
import berlin.bbdc.inet.mera.commons.tools.JsonUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.apache.http.client.methods.HttpGet
import org.apache.http.conn.HttpHostConnectException
import org.apache.http.impl.client.{CloseableHttpClient, HttpClientBuilder}
import scala.io.Source

class FlinkHttpClient(hostname: String, port: Int) extends AbstractFlinkClient  {
  private val FLINK_URL = "http://" + hostname + ":" + port
  private val JOBS_URL = FLINK_URL + "/jobs"
  private def JOB_URL(jobId: String) = FLINK_URL + "/jobs/" + jobId
  private def VERTEX_URL(jobId: String, vertexId: String) = FLINK_URL + "/jobs/" + jobId + "/vertices/" + vertexId
  private val LOG: Logger = LoggerFactory.getLogger(getClass)

  def establishConnection() = {
    var connected = false
    while (!connected) {
      try {
        getJobs()
        connected = true
      } catch {
        case _: ConnectException =>
          LOG.warn("Could not connect to flink. Sleeping for three seconds.")
          Thread.sleep(3000)
      }
    }
  }

  def getJobs(): AllJobs = {
    var allJobs = JsonUtils.fromJson[AllJobs](getRestContent(JOBS_URL))
    while (allJobs.jobsRunning.size < 1) {
      LOG.info(s"No job is running. Retrying in 3 seconds." )
      Thread.sleep(3000)
      allJobs = JsonUtils.fromJson[AllJobs](getRestContent(JOBS_URL))
    }
    LOG.info(s"Detected ${allJobs.jobsRunning.size} running jobs: ${allJobs.jobsRunning.toString}" )
    allJobs
  }

  def getJob(jobId: String): Job = JsonUtils.fromJson[Job](getRestContent(JOB_URL(jobId)))

  def getFullVertex(jobId: String, vertexId: String): FullVertex =
    JsonUtils.fromJson[FullVertex](getRestContent(VERTEX_URL(jobId, vertexId)))

  private def getRestContent(url: String): String = {
    val client = HttpClientBuilder.create().build()
    val request = new HttpGet(url)
    try {
      val response = client.execute(request)
      Option(response.getEntity)
        .map(x => x.getContent)
        .map(y => {
          val res = Source.fromInputStream(y).getLines.mkString
          y.close()
          client.close()
          res
        }) match {
        case Some(s) => s
        case None => ""
      }
    } catch {
      case _: HttpHostConnectException =>
        throw new ConnectException(s"No instance of Flink's Jobmanager found at $FLINK_URL")
    }
  }
}

