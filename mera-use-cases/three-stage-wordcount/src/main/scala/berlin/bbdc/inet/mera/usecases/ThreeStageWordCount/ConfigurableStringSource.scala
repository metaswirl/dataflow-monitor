package berlin.bbdc.inet.mera.usecases.ThreeStageWordCount

import java.util.concurrent.{ArrayBlockingQueue, Executors, Future}

import org.apache.flink.configuration.Configuration
import org.apache.flink.metrics.Gauge
import org.apache.flink.streaming.api.functions.source.{RichSourceFunction, SourceFunction}
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicReference

import org.apache.flink.api.java.utils.ParameterTool
import berlin.bbdc.inet.mera.usecases.template.utils.ParameterReceiverHTTP

import scala.io.{BufferedSource, Source}
import scala.util.Random

class LineGenerator {
  /*
    the average word is 10.26 characters long
    selecting 20 words on average should produce around 200 characters per line
    (this is what Dhalion used in their evaluation)
   */
  var src: BufferedSource = _
  try {
    // In a perfect world, this would always work. But Flink drops the resource files
    // TODO: figure out what Flink is doing with the resources
    src = Source.fromFile(getClass.getClassLoader.getResource("words.txt").getFile)
  } catch {
    case e: java.io.FileNotFoundException =>
      src = Source.fromFile("/nfs/general/datasets/words.txt")
  }
  val words: Array[String] = src.getLines.toArray

  def generate(): String = {
    (0 until 20).map(_ => {
      words(Random.nextInt(words.length))
    }).mkString(" ")
  }
}

// TODO: Separate the string generator from the source and make the source generic
class ConfigurableStringSource(var rate: Int, val queueCapacity : Int, var port: Int) extends RichSourceFunction[String] {
  // TODO: remove null values
  var running = true
  var paramReceiver : ParameterReceiverHTTP = null

  var ReporterThread : Future[_] = null
  var genThread : Future[_] = null
  var rateReceiverThread: Future[_] = null
  var rateTuple: (Int, Int) = (0, 0) // first elem: items second elem: time interval
  lazy val queue : ArrayBlockingQueue[String] = new ArrayBlockingQueue[String](queueCapacity)
  lazy val queueOverFlowCtr: AtomicReference[Long] = new AtomicReference(0L)
  lazy val generator: LineGenerator = new LineGenerator()

  override def cancel(): Unit = {
    running = false
    paramReceiver.cancel()
  }

  override def close(): Unit = {
    super.close()
    cancel()
  }

  def setRateTuple(rate: Int): (Int, Int) = {
    def gcd(a: Int, b: Int): Int =
      if (b==0) a
      else gcd(b, a%b)

    val rateGcd = gcd(rate, 1000)
    (rate/rateGcd, 1000/rateGcd)
  }

  lazy val producer = new Thread(new Runnable {
    override def run() = {
      val lowWaterMark: Int = (0.5 * queueCapacity).toInt
      val highWaterMark: Int = (0.8 * queueCapacity).toInt

      while (running) {
        // normal rate
        Thread.sleep(rateTuple._2)
        val remCap = queue.remainingCapacity()
        if (remCap > rateTuple._1) {
          for (a <- 0 until rateTuple._1) {
            queue.put(generator.generate())
          }
        } else {
          // TODO: It should be possible to do this simpler
          val x: Long = queueOverFlowCtr.get()
          queueOverFlowCtr.set(x - rateTuple._1.toLong)
        }
        if (queueOverFlowCtr.get() > 0 && remCap < lowWaterMark) {
          // catchup rate
          val begin = System.currentTimeMillis()
          while (running && queueOverFlowCtr.get() > 0 && queue.remainingCapacity() > highWaterMark) {
            queue.put(generator.generate())
            // TODO: It should be possible to do this simpler
            val x: Long = queueOverFlowCtr.get()
            queueOverFlowCtr.set(x - 1L)
          }
          // add items not generated during catchup
          // TODO: It should be possible to do this simpler
          val x: Long = queueOverFlowCtr.get()
          queueOverFlowCtr.set(x + (((System.currentTimeMillis() - begin) / rateTuple._2) * rateTuple._1))
        }
      }
    }
  })

  override def open(parameters: Configuration): Unit = {
    rateTuple = setRateTuple(rate)
    super.open(parameters)
    port += getRuntimeContext.getIndexOfThisSubtask
    producer.start()
    val rc = this.getRuntimeContext
    getRuntimeContext().getExecutionConfig().getGlobalJobParameters() match {
      case param: ParameterTool =>
        val stateDir = param.getRequired("dirs.state");
        val portFilePath = Some(Paths.get(stateDir,
          "source-" + rc.getTaskName + "-" + rc.getIndexOfThisSubtask))
        paramReceiver = new ParameterReceiverHTTP(getRate, setRate, portFilePath)
        paramReceiver.start()
      case _ => throw new ClassCastException
    }
    getRuntimeContext.getMetricGroup().gauge[Long,Gauge[Long]]("backlog", new Gauge[Long] {
      override def getValue: Long = queue.size().toLong + queueOverFlowCtr.get.toLong
    })
  }

  def setRate(r : Int): Unit = {
    rate = r
    rateTuple = setRateTuple(r)
  }
  def getRate(): Int = {
    return rate
  }

  override def run(ctx: SourceFunction.SourceContext[String]): Unit = {
    while (running) {
      ctx.collect(queue.take())
    }
  }
}
