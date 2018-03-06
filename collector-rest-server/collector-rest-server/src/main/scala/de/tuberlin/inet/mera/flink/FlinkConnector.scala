package de.tuberlin.inet.mera.flink

import java.util.concurrent.{ScheduledThreadPoolExecutor, TimeUnit}

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import de.tuberlin.inet.mera.model.Jobs
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable
import scala.io.Source


object FlinkConnector {
  val logger: Logger = LoggerFactory.getLogger(getClass)

  var interval: Long = 10L

  var jobsMap = new mutable.HashMap[String, List[String]]

  var flinkPort: Integer = 8081
  var flinkUrl: String = "http://localhost:"
  val flinkJobs: String = "/jobs/"

  val objectMapper = new ObjectMapper() with ScalaObjectMapper
  objectMapper.registerModule(DefaultScalaModule)

  def getJobsList: mutable.HashMap[String, List[String]] = {
    val map = jobsMap
    jobsMap = new mutable.HashMap[String, List[String]]
    map
  }

  def scheduleFlinkPeriodicRequest(): Unit = {
    val ex = new ScheduledThreadPoolExecutor(1)
    val task = new Runnable {
      def run() = {
        val jobs = getRestContent(flinkUrl + flinkPort + flinkJobs)
        val job: Jobs = objectMapper.readValue[Jobs](jobs)
        val allCurrentJobs: List[String] = job.jobsRunning ::: job.jobsFinished :::
          job.jobsCancelled ::: job.jobsFailed
        for (job <- allCurrentJobs) {
          val jobProperties: String = getRestContent(flinkUrl + flinkPort + flinkJobs + job)
          val list: Option[List[String]] = jobsMap.get(job)
          if (list.isEmpty)
            jobsMap += (job -> List(jobProperties))
          else {
            val add: List[String] = list.get ::: List(jobProperties)
            jobsMap += (job -> add)
          }
        }
      }
    }
    val f = ex.scheduleAtFixedRate(task, 1, interval, TimeUnit.SECONDS)
    //    f.cancel(false)
  }

  private def getRestContent(url: String): String = {
    val client = HttpClientBuilder.create().build()
    val request = new HttpGet(url)

    val response = client.execute(request)
    val entity = response.getEntity
    var content = ""
    if (entity != null) {
      val inputStream = entity.getContent
      content = Source.fromInputStream(inputStream).getLines.mkString
      inputStream.close()
    }
    client.close()
    content
  }
}
