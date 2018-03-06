package de.tuberlin.inet.mera.collector.server

import org.scalatra.test.scalatest._

class CollectorRestServletTests extends ScalatraFunSuite {

  addServlet(classOf[CollectorRestServlet], "/*")

  test("GET / on CollectorRestServlet should return status 200"){
    get("/"){
      status should equal (200)
    }
  }

}
