package berlin.bbdc.inet.mera.usecases.template.utils

import java.io.{DataInputStream, InputStream, OutputStream}
import java.net.InetSocketAddress
import java.nio.charset.Charset
import java.nio.file.Path
import java.nio.file.Files
import java.util

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}

import scala.io.BufferedSource

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
    val response = getter.toString
    he.sendResponseHeaders(200, response.length)
    val out = he.getResponseBody
    out.write(response.getBytes)
    out.close()
  }

  def handlePost(he: HttpExchange, is: InputStream): Unit = {
    val dis = new DataInputStream(is)
    val newDuration = dis.readInt()
    setter(newDuration)
    is.close()
    he.sendResponseHeaders(200, -1)
  }
}
