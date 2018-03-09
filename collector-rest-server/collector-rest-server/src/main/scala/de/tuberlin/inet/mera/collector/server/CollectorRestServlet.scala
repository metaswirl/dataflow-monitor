package de.tuberlin.inet.mera.collector.server

import java.util.concurrent.{ScheduledThreadPoolExecutor, TimeUnit}

import de.tuberlin.inet.mera.flink.FlinkConnector
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
import org.json4s.{DefaultFormats, Formats}
import org.scalatra._
import org.scalatra.json._
import org.slf4j.LoggerFactory

import scala.io.Source

class CollectorRestServlet extends ScalatraServlet with JacksonJsonSupport {
  protected implicit lazy val jsonFormats: Formats = DefaultFormats
  val logger = LoggerFactory.getLogger(getClass)

  var restEndpointPort: Integer = 8080
  var restEndPointHostname: String = "localhost"

  var flinkPort: Integer = 8081
  var flinkUrl: String = "http://localhost"

  get("/") {
    views.html.hello()
  }

  get("/restEndpoint") {
    restEndPointHostname + ":" + String.valueOf(restEndpointPort)
  }

  get("/jobsList") {
    contentType = formats("json")
    FlinkConnector.getJobsList
  }

}

object CollectorRestServlet {
  val logger = LoggerFactory.getLogger(getClass)
  val interval : Long = 5


}
