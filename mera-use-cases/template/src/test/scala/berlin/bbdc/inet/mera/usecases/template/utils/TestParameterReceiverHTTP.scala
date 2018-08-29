package berlin.bbdc.inet.mera.usecases.template.utils

import java.io._
import java.net.{HttpURLConnection, URL}
import java.nio.file.{Files, Path, Paths}
import java.util.stream.Collectors

import berlin.bbdc.inet.mera.commons.tools.JsonUtils
import org.specs2.mutable.Specification
import org.specs2.mutable.BeforeAfter

class TestParameterReceiverHTTP extends Specification {
  val path : File = File.createTempFile("tmp", "")

  "ParameterReceiverHTTP" >> {
    "start and stop" >> {
      val server = new ParameterReceiverHTTP(4, (_:Int)=>(), None)
      server.start()
      server.cancel()
      // TODO: catch exceptions
      true must_== true
    }
    "handle GET" >> {
      var number = 0
      var returnedNumber = 0

      def setNumber(n: Int): Unit = {
        number = n
      }

      def getNumber() = number

      val server = new ParameterReceiverHTTP(getNumber, setNumber, None)
      server.start()

      val url = s"http://localhost:${server.getPort}/"
      new URL(url).openConnection() match {
        case con: HttpURLConnection =>
          con.setRequestMethod("GET")
          con.setDoOutput(false)
          con.setDoInput(true)
          con.getResponseCode must_== 200
          con.getInputStream
          val result: String = new BufferedReader(new InputStreamReader(con.getInputStream))
            .lines().collect(Collectors.joining(""))
          val res = JsonUtils.fromJson[Delay](result)
          returnedNumber = res.delay
        case _ => throw new ClassCastException
      }
      server.cancel()
      number must_== 0
      returnedNumber must_== 0
    }

    "handle POST" >> {
      var number = 0

      def setNumber(n: Int): Unit = {
        number = n
      }

      def getNumber() = number

      val server = new ParameterReceiverHTTP(getNumber, setNumber, None)
      server.start()

      val newDelay = new Delay(100)
      val newDelayJson = JsonUtils.toJson(newDelay)

      val url = s"http://localhost:${server.getPort}/"
      new URL(url).openConnection() match {
        case con: HttpURLConnection =>
          con.setDoOutput(true)
          con.setRequestMethod("POST")

          //con.setRequestProperty("Accept", "application/json");
          val os = con.getOutputStream()
          os.write(newDelayJson.getBytes)
          os.close()
          con.getResponseCode must_== 200
        case _ => throw new ClassCastException
      }
      number must_== 100
    }
    "write port to file" >> {
      val server = new ParameterReceiverHTTP(4, (_:Int)=>(), Some(Paths.get(path.getPath)))
      server.start()
      server.cancel()
      path.exists() must_== true
    }
  }

  step(path.delete())
}
