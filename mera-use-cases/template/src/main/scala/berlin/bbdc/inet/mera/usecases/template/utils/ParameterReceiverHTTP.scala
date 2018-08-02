package berlin.bbdc.inet.mera.usecases.template.utils

import java.io._
import java.net.{InetAddress, InetSocketAddress, Socket}
import java.nio.charset.Charset
import java.nio.file.Path
import java.nio.file.Files
import java.util
import java.util.stream.Collectors

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}

import scala.io.BufferedSource
import berlin.bbdc.inet.mera.commons.tools.JsonUtils

class ParameterReceiverHTTP(getter: =>Int, setter: Int=>Unit, portFilePath: Option[Path] = None, port: Int=0) {
  val server : HttpServer = HttpServer.create(new InetSocketAddress(port), 0)

  def getPort = server.getAddress.getPort

  def start() = {
    server.createContext("/", new MyHTTPHandler(getter, setter))
    server.setExecutor(null)
    server.start()
    portFilePath.map{ case path =>
      println(path.toAbsolutePath.toString)
      println(path.toString)
      if (! Files.exists(path.toAbsolutePath)) {
        Files.createFile(path.toAbsolutePath)
      }
      val line = util.Arrays.asList(s"${InetAddress.getLocalHost().getHostName()}:${getPort}") //localhost
      Files.write(path.toAbsolutePath, line, Charset.forName("UTF-8"))
    }
  }

  def cancel(): Unit = {
    server.stop(3)
  }
}

private class MyHTTPHandler(getter: =>Int, setter: Int=>Unit) extends HttpHandler {
  override def handle(he: HttpExchange): Unit = {
    he.getRequestMethod match {
      case "POST" => handlePost(he, he.getRequestBody)
      case "GET" => handleGet(he)
      case x => throw new Exception(s"Don't know what '$x' is about")
    }
  }

  def handleGet(he: HttpExchange): Unit = {
    val response = JsonUtils.toJson(new Delay(getter))
    he.sendResponseHeaders(200, response.length)
    val out = he.getResponseBody
    out.write(response.getBytes)
    out.close()
  }

  def handlePost(he: HttpExchange, is: InputStream): Unit = {
    val result : String = new BufferedReader(new InputStreamReader(is))
      .lines().collect(Collectors.joining(""))
    try {
      val newDelay: Int = JsonUtils.fromJson[Delay](result).delay
      is.close()
      setter(newDelay)
    } catch {
      case _: Throwable => println("Could not parse " + result)
                he.sendResponseHeaders(400, -1)
    }
    he.sendResponseHeaders(200, -1)
  }
}

case class Delay(delay: Int)
