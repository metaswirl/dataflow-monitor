package berlin.bbdc.inet.mera.commons.tools

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper

import scala.reflect._

object JsonUtils {

  val mapper = new ObjectMapper with ScalaObjectMapper
  mapper.registerModule(DefaultScalaModule)
//  mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  def toJson(value: Map[Symbol, Any]): String = {
    toJson(value map { case (k,v) => k.name -> v})
  }

  def toJson(value: Any): String = {
    mapper.writeValueAsString(value)
  }

//  def toMap[V](json:String)(implicit m: ClassTag[V]): Map[String, V] = fromJson[Map[String,V]](json)

  def fromJson[T: ClassTag](json: String): T = {
    mapper.readValue[T](json, classTag[T].runtimeClass.asInstanceOf[Class[T]])
  }


}
