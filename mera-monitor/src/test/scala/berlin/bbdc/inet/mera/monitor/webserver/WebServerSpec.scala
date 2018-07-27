package berlin.bbdc.inet.mera.monitor.webserver

import akka.http.scaladsl.model.{HttpEntity, HttpMethods, HttpRequest, MediaTypes}
import akka.http.scaladsl.testkit.Specs2RouteTest
import berlin.bbdc.inet.mera.commons.tools.JsonUtils
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll

import scala.concurrent.ExecutionContextExecutor
import scala.io.Source

class WebServerSpec extends Specification with Specs2RouteTest with Mockito with WebService with BeforeAll {

  implicit val executionContext: ExecutionContextExecutor = system.dispatcher
  implicit val metricContainer: MetricContainer = mock[MetricContainer]

  val taskInitMessage = TaskInitMessage(List("id0", "id1"), "metric", 5)
  val tId = "tId"
  val mId = "mId"
  val since = 3

  def beforeAll(): Unit = {
    metricContainer.metricsList returns
      Vector[String]("Metric1", "Metric2", "Metric3")

    metricContainer.getInitMetric returns
      List(InitializedMetric("id1", "m1", 1), InitializedMetric("id2", "m2", 2))

    metricContainer.topology returns List()

    metricContainer.postInitMetric(taskInitMessage) returns "OK"

    metricContainer.getMetricsOfTask((tId, mId), since) returns TaskMetrics(tId, List())
  }

  "WebService" should {

    "return index.html for GET /" in {
      Get() ~> route ~> check {
        entityAs[String] shouldEqual Source.fromInputStream(getClass.getClassLoader.getResourceAsStream("static/index.html")).mkString
      }
    }

    "return list of metrics for GET /data/metrics" in {
      Get("/data/metrics") ~> route ~> check {
        entityAs[String] shouldEqual """["Metric1","Metric2","Metric3"]"""
      }
    }

    "return list of initialized metrics for GET /data/metrics/tasks/init" in {
      Get("/data/metrics/tasks/init") ~> route ~> check {
        entityAs[String] shouldEqual
          """[{"taskId":"id1","metricId":"m1","resolution":1},{"taskId":"id2","metricId":"m2","resolution":2}]"""

      }
    }

    "return empty list for empty topology for GET /data/topology" in {
      Get("/data/topology") ~> route ~> check {
        entityAs[String] shouldEqual "[]"
      }
    }

    "return taskInitMessage for POST /data/metrics/tasks/init" in {
      val postRequest = HttpRequest(
        HttpMethods.POST,
        uri = "/data/metrics/tasks/init",
        entity = HttpEntity(MediaTypes.`application/json`, JsonUtils.toJson(taskInitMessage)))

      postRequest ~> route ~> check {
        entityAs[String] shouldEqual """"OK""""
      }
    }

    "return swagger.json for GET /swagger" in {
      Get({"/swagger"}) ~> route ~> check {
        entityAs[String] shouldEqual Source.fromInputStream(getClass.getClassLoader.getResourceAsStream("static/swagger/swagger.json")).mkString
      }
    }

    "return empty metric values for GET /data/metrics/task with params" in {
      Get(s"/data/metrics/task?metricId=$mId&since=$since&taskId=$tId") ~> route ~> check {
        entityAs[String] shouldEqual s"""{"taskId":"$tId","values":[]}"""
      }
    }
  }
}
