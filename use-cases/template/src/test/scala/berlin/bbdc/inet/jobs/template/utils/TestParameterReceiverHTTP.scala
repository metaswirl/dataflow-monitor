package berlin.bbdc.inet.jobs.template.utils

import java.io.{BufferedReader, DataOutputStream, File, InputStreamReader}
import java.net.{HttpURLConnection, URL}
import java.nio.file.{Files, Path, Paths}

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
    "handle messages" >> {
      var number = 0
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
          con.getResponseCode must_== 200

          con.getInputStream
          val br = new BufferedReader(new InputStreamReader(con.getInputStream()))
          val res = br.readLine().toInt
          res must_== 0
        case _ => throw new ClassCastException
      }
      number must_== 0

      new URL(url).openConnection() match {
        case con: HttpURLConnection =>
          con.setRequestMethod("POST")
          con.setDoOutput(true)
          new DataOutputStream(con.getOutputStream()).writeInt(100)
          con.getResponseCode must_== 200
        case _ => throw new ClassCastException
      }
      number must_== 100

      new URL(url).openConnection() match {
        case con: HttpURLConnection =>
          con.setRequestMethod("GET")
          val br = new BufferedReader(new InputStreamReader(con.getInputStream()))
          val res = br.readLine().toInt
          res must_== 100
        case _ => throw new ClassCastException
      }

      server.cancel()
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
