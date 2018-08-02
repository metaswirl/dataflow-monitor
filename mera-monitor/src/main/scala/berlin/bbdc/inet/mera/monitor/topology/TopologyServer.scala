package berlin.bbdc.inet.mera.monitor.topology

import java.net.ConnectException
import java.util.concurrent.TimeUnit

import berlin.bbdc.inet.mera.commons.tools.JsonUtils
import berlin.bbdc.inet.mera.monitor.model.CommType.CommType
import berlin.bbdc.inet.mera.monitor.model.CommType
import berlin.bbdc.inet.mera.monitor.model.Model
import berlin.bbdc.inet.mera.monitor.model.ModelBuilder
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.apache.http.client.methods.HttpGet
import org.apache.http.conn.HttpHostConnectException
import org.apache.http.impl.client.{CloseableHttpClient, HttpClientBuilder}
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.io.Source

class TopologyServer(hostname: String, port: Integer, cli: AbstractFlinkClient) {
  // So far there is no way to update the model. In the future we would like to update the model,
  // but this would require further changes to the ModelFileWriter etc.

  def buildModels(): Map[String, Model] = {
    var models = new mutable.HashMap[String, Model]
    cli.establishConnection()
    //get list of all running jobs. for each job build a model
    for (jid <- cli.getJobs().jobsRunning) {
      val job = cli.getJob(jid)
      val modelBuilder = new ModelBuilder
      //iterate over vertices and for each add a new operator to the model
      job.vertices foreach (v => {
        modelBuilder.addSuccessor(v.name, v.parallelism, TopologyServer.findCommTypeById(v.id, job.plan),
          v.name contains "loadshedder", Some(getHostMappings(jid, v.id)))
      })
      models += (jid -> modelBuilder.createModel(1000))
    }

    if (models.isEmpty) {
      throw new IllegalStateException("No models detected in Flink JobManager")
    }

    models.toMap
  }

  private def getHostMappings(jobId : String, vertexId : String) : Map[Int, String] =
    cli.getFullVertex(jobId, vertexId).subtasks.map(x => x.subtask -> x.host).toMap
}

object TopologyServer {

  def findCommTypeById(id: String, plan: Plan): CommType = {
    plan.nodes.foreach(n => {
      if (n.inputs != null) {
        n.inputs.foreach(i => {
          if (i.id.equals(id)) {
            i.shipStrategy match {
              case "HASH" | "RANGE" =>
                return CommType.POINTWISE
              case "REBALANCE" | "FORWARD" =>
                return CommType.ALL_TO_ALL
              case t =>
                throw new IllegalStateException("Unknown CommType " + t)
            }
          }
        })
      }
    })
    CommType.UNCONNECTED
  }
}

// TODO: Solve this with implicits
abstract class AbstractFlinkClient {
  def getJobs(): AllJobs
  def getJob(jobId: String): Job
  def getFullVertex(jobId: String, vertexId: String): FullVertex
  def establishConnection(): Unit
}

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
      LOG.info(s"No job is running. Retrying in 5 seconds." )
      Thread.sleep(5000)
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


@JsonIgnoreProperties(ignoreUnknown = true)
case class AllJobs(@JsonProperty("jobs-running") jobsRunning: List[String])

@JsonIgnoreProperties(ignoreUnknown = true)
case class Job(jid: String,
               name: String,
               vertices: List[Vertex],
               plan: Plan)

@JsonIgnoreProperties(ignoreUnknown = true)
case class Vertex(id: String,
                  name: String,
                  parallelism: Int)

@JsonIgnoreProperties(ignoreUnknown = true)
case class FullVertex(id: String,
                  name: String,
                  parallelism: Int,
                  subtasks: List[SubTask])

@JsonIgnoreProperties(ignoreUnknown = true)
case class SubTask(subtask: Int,
                   host: String)

@JsonIgnoreProperties(ignoreUnknown = true)
case class Plan(nodes: List[Node])

@JsonIgnoreProperties(ignoreUnknown = true)
case class Node(id: String,
                description: String,
                inputs: List[Input])

@JsonIgnoreProperties(ignoreUnknown = true)
case class Input(id: String,
                 @JsonProperty("ship_strategy") shipStrategy: String)
