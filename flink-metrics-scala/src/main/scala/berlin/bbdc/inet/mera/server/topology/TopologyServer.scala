package berlin.bbdc.inet.mera.server.topology

import java.util.concurrent.{ScheduledThreadPoolExecutor, TimeUnit}

import berlin.bbdc.inet.mera.server.model.{CommType, ModelBuilder}
import com.fasterxml.jackson.annotation.{JsonIgnoreProperties, JsonProperty}
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
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
  private val LOG: Logger = LoggerFactory.getLogger(getClass)

  val objectMapper = new ObjectMapper() with ScalaObjectMapper
  objectMapper.registerModule(DefaultScalaModule)

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
    return modelBuilder
  }


  def getJobsList: mutable.HashMap[String, List[String]] = {
    val temp = mutable.HashMap[String, List[String]]() ++= jobsMap
    jobsMap = new mutable.HashMap[String, List[String]]
    temp
  }

  def scheduleFlinkPeriodicRequest(interval: Long): Unit = {
    val ex = new ScheduledThreadPoolExecutor(1)
    val task = new Runnable {
      def run(): Unit = {
        //get current jobs from Flink
        val jobs = getRestContent(FLINK_URL + JOBS)
        //map to intermediate object
        val job: Jobs = objectMapper.readValue[Jobs](jobs)
        //sum all of them to a list
        val allCurrentJobs: List[String] = job.jobsRunning ::: job.jobsFinished :::
          job.jobsCancelled ::: job.jobsFailed
        //iterate through the list and add/update them in the jobsMap
        for (job <- allCurrentJobs) {
          val jobProperties: String = getRestContent(FLINK_URL + JOBS + "/" + job)

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

  private def getRestContent(url: String): String = {
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
case class Jobs(var timestamp: Long = 0,
                @JsonProperty("jobs-running") jobsRunning: List[String],
                @JsonProperty("jobs-finished") jobsFinished: List[String],
                @JsonProperty("jobs-cancelled") jobsCancelled: List[String],
                @JsonProperty("jobs-failed") jobsFailed: List[String]) {

}
