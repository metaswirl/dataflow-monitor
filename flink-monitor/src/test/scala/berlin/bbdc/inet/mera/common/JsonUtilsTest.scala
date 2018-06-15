package berlin.bbdc.inet.mera.common

import berlin.bbdc.inet.mera.common.tools.JsonUtils
import org.specs2.mutable.Specification

class JsonUtilsTest extends Specification {
  private val s1 = Student("Maria", 15, 1.33f, isPassing = true)
  private val s2 = Student("Adam", 13, 4.55f, isPassing = false)
  private val s3 = Student("Anna", 16, 2.37f, isPassing = true)
  private val map = Map("s1" -> s1, "s2" -> s2, "s3" -> s3)

  private val s1Json = """{"name":"Maria","age":15,"gpa":1.33,"isPassing":true}"""
  private val s2Json = """{"name":"Adam","age":13,"gpa":4.55,"isPassing":false}"""
  private val s3Json = """{"name":"Anna","age":16,"gpa":2.37,"isPassing":true}"""
  private val mapJson = """{"s1":""" + s1Json + ""","s2":""" + s2Json + ""","s3":""" + s3Json + "}"

  "JsonUtils" should {
    "transform an object to a json" in {
      JsonUtils.toJson(s1) mustEqual s1Json
      JsonUtils.toJson(s2) mustEqual s2Json
      JsonUtils.toJson(s3) mustEqual s3Json
    }

    "transform a map to a json" in {
      JsonUtils.toJson(map) mustEqual mapJson
    }

    "transform a json to an object" in {
      JsonUtils.fromJson[Student](s1Json) mustEqual s1
      JsonUtils.fromJson[Student](s2Json) mustEqual s2
      JsonUtils.fromJson[Student](s3Json) mustEqual s3
    }
  }

}

case class Student(name: String, age: Int, gpa: Float, isPassing: Boolean)
