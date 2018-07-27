package berlin.bbdc.inet.jobs.template.utils

import java.util.concurrent.{Executors, Future}
import java.nio.file.Path
import java.nio.file.Files

class ParameterReceiverSocket(getter: =>Int, setter: Int=>Unit, portFilePath: Option[Path] = None) extends Runnable {
  import java.net._
  import java.io._
  import scala.io._

  val server: ServerSocket = new ServerSocket(0)
  private var running = true
  private var thread : Future[_] = _

  def getPort() = server.getLocalPort

  override def run(): Unit = {
    portFilePath.map{ case path => Files.newOutputStream(path).write(server.getLocalPort) }

    while (running) {
      val s = server.accept()
      val lines = new BufferedSource(s.getInputStream()).getLines()
      val out = new PrintStream(s.getOutputStream())
      try {
        val firstLine = lines.next()
        if (firstLine.isEmpty()) {
          out.println("Parameter: " + getter)
        } else {
          val newDuration = firstLine.toInt
          //gen.setNumLinesPerInterval(newDuration)
          setter(newDuration)
          out.println("SUCCESS new param: " + getter)
        }
      } catch {
        case e: java.lang.NumberFormatException =>
          println("RateReceiver: Could not convert String")
          out.println("ERROR: Could not convert String")
        case e: java.lang.ClassCastException =>
          println("RateReceiver: Could not convert String")
          out.println("ERROR: Could not convert String")
      } finally {
        out.flush()
      }
      s.close()
    }
    server.close()
  }

  def start() = {
    thread = Executors.newSingleThreadExecutor().submit(this)
  }

  def cancel(): Unit ={
    running = false
    thread.cancel(true)
  }
}
