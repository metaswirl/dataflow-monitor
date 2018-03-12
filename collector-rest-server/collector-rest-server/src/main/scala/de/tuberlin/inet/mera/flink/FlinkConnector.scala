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

  def scheduleFlinkPeriodicRequest(interval: Long): Unit = {
    val ex = new ScheduledThreadPoolExecutor(1)
    val task = new Runnable {
      def run(): Unit = {
        //get current jobs from Flink
        val jobs = getRestContent(flinkUrl + flinkPort + flinkJobs)
        //map to intermediate object
        val job: Jobs = objectMapper.readValue[Jobs](jobs)
        //sum all of them to a list
        val allCurrentJobs: List[String] = job.jobsRunning ::: job.jobsFinished :::
          job.jobsCancelled ::: job.jobsFailed
        //iterate through the list and add/update them in the jobsMap
        for (job <- allCurrentJobs) {
          val jobProperties: String = getRestContent(flinkUrl + flinkPort + flinkJobs + job)

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
