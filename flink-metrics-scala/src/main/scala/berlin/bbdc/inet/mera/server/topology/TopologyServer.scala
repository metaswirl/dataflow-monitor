package berlin.bbdc.inet.mera.server.topology

import java.util.concurrent.{ScheduledThreadPoolExecutor, TimeUnit}

import berlin.bbdc.inet.mera.common.JsonUtils
import berlin.bbdc.inet.mera.server.model.{CommType, Model, ModelBuilder, Operator}
import com.fasterxml.jackson.annotation.{JsonIgnoreProperties, JsonProperty}
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable
import scala.io.Source

class TopologyServer(hostname: String, port: Integer) {
  // So far there is no way to update the model. In the future we would like to update the model,
  // but this would require further changes to the ModelFileWriter etc.

  private val FLINK_URL = "http://" + hostname + ":" + port
  private val JOBS = "/jobs"
  private val VERTICES = "/vertices"

  private val LOG: Logger = LoggerFactory.getLogger(getClass)

  var jobsMap = new mutable.HashMap[String, List[String]]

  def createModelBuilder(): ModelBuilder = {
    val modelBuilder = new ModelBuilder()
    modelBuilder.addSuccessor("Source: Socket Stream", 1, CommType.Ungrouped)
    modelBuilder.addSuccessor("Flat Map", 3, CommType.Ungrouped)
    modelBuilder.addSuccessor("Filter", 2, CommType.Ungrouped)
    modelBuilder.addSuccessor("Map", 2, CommType.Grouped)
    modelBuilder.addSuccessor("aggregation", 2, CommType.Ungrouped)
    modelBuilder.addSuccessor("bottleneck", 1, CommType.Ungrouped)
    modelBuilder.addSuccessor("Sink: Unnamed", 2, CommType.Ungrouped)
    modelBuilder
  }

  def buildModels(): Map[String, Model] = {
    /*
    1. get list of jobs
    2. for each job
      - get list of vertices
      - for each vertex
        + get name
        + get parallelism
        +
     */
    var models = new mutable.HashMap[String, Model]
    for (jid <- getJobList) {
      val jobJson: String = getRestContent(JOBS + "/" + jid + VERTICES)
      val job = JsonUtils.fromJson[Job](jobJson)
      val modelBuilder = new ModelBuilder
      job.vertices foreach (v => modelBuilder.addSuccessor(v.name, v.parallelism, CommType.Ungrouped))
      //FIXME: what is n here?
      models += (jid -> modelBuilder.createModel(1000))
    }
    models.toMap
  }


  def getJobsMap: mutable.HashMap[String, List[String]] = {
    val temp = mutable.HashMap[String, List[String]]() ++= jobsMap
    jobsMap = new mutable.HashMap[String, List[String]]
    temp
  }

  def scheduleFlinkPeriodicRequest(interval: Long): Unit = {
    val ex = new ScheduledThreadPoolExecutor(1)
    val task = new Runnable {

      def run(): Unit = {
        //iterate through the list and add/update them in the jobsMap
        for (job <- getJobList) {
          val jobProperties: String = getRestContent(JOBS + "/" + job)

          jobsMap.get(job) match {
            case Some(list) => jobsMap += (job -> (list ::: List(jobProperties)))
            case None => jobsMap += (job -> List(jobProperties))
          }
        }
      }
    }
    ex.scheduleAtFixedRate(task, 1, interval, TimeUnit.SECONDS)
    //    f.cancel(false)
  }

  private def getJobList: List[String] = {
    //get current jobs from Flink
    val allJobsJson = getRestContent(JOBS)
    //map to intermediate object
    val allJobs: AllJobs = JsonUtils.fromJson[AllJobs](allJobsJson)
    //sum all of them to a list
    allJobs.jobsRunning
  }

  private def getRestContent(dir: String): String = {
    val url = FLINK_URL + dir
    val client = HttpClientBuilder.create().build()
    val request = new HttpGet(url)

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
  }


}

@JsonIgnoreProperties(ignoreUnknown = true)
case class AllJobs(@JsonProperty("jobs-running") jobsRunning: List[String])

@JsonIgnoreProperties(ignoreUnknown = true)
case class Job(jid: String,
               name: String,
               vertices: List[Vertex])

@JsonIgnoreProperties(ignoreUnknown = true)
case class Vertex(id: String,
                  name: String,
                  parallelism: Int)