package berlin.bbdc.inet.mera.server.webserver

import akka.http.scaladsl.testkit.Specs2RouteTest
import berlin.bbdc.inet.mera.server.model.{Model, Operator}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

import scala.collection.immutable.ListMap
import scala.concurrent.ExecutionContextExecutor
import scala.io.Source

class WebServerSpec extends Specification with Specs2RouteTest with Mockito with WebService {

  implicit val executionContext: ExecutionContextExecutor = system.dispatcher
  implicit val model: Model = mock[Model]

  model.operators returns ListMap[String, Operator](
    "Sink" -> mock[Operator],
  "Filter" -> mock[Operator],
  "Source" -> mock[Operator]
  )

  "WebService" should {


    "return index.html for GET request in the root path" in {
      Get() ~> route ~> check {
        entityAs[String] shouldEqual Source.fromInputStream(getClass.getClassLoader.getResourceAsStream("static/index.html")).mkString
      }
    }

    "return list of operator ids for GET /data/operators" in {
      Get("/data/operators") ~> route ~> check {
        responseAs[String] shouldEqual """["Source","Filter","Sink"]"""
      }
    }
  }

  override def collectNewValuesOfMetric(id: String, resolution: Int): Map[String, (Long, Double)] = ???
}
