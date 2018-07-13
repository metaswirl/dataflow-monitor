package berlin.bbdc.inet.jobs.ThreeStageWordCount

import java.util.concurrent.{ArrayBlockingQueue, Executors, Future}

import berlin.bbdc.inet.jobs.ThreeStageWordCount.utils.ParameterReceiver
import org.apache.flink.configuration.Configuration
import org.apache.flink.metrics.{Gauge}
import org.apache.flink.streaming.api.functions.source.{RichSourceFunction, SourceFunction}

class Generator(queue: ArrayBlockingQueue[String], rate : Long) extends Runnable {
  //private var lines = scala.io.Source.fromFile( "/tmp/lines.txt" ).getLines.toList
  private var thread: Future[_] = _
  private var running = true

  def cancel() = {
    running = false
    thread.cancel(true)
  }

  def start() = {
    thread = Executors.newSingleThreadExecutor().submit(this)
  }

  override def run(): Unit = {
    var oldTime = System.currentTimeMillis()
    var count = 0
    while (running) {
      if (count % 500 == 0 && count >= rate) {
        val now = System.currentTimeMillis()
        val waitTime = 1000 - (now - oldTime)
        if (waitTime > 0) {
          Thread.sleep(waitTime)
        }
        oldTime = now + waitTime
        count = 0
      }
      queue.put("asd jlk qwe cxf zxggh")
      count += 1
    }
  }
}

class ConfigurableStringSource(var rate: Int, val queueCapacity : Int, var port: Int) extends RichSourceFunction[String] {
  var running = true
  var queue : ArrayBlockingQueue[String] = null
  var gen : Generator = null
  var paramReceiver : ParameterReceiver = null

  var ReporterThread : Future[_] = null
  var genThread : Future[_] = null
  var rateReceiverThread: Future[_] = null

  override def cancel(): Unit = {
    running = false
    paramReceiver.cancel()
  }

  override def close(): Unit = {
    super.close()
    cancel()
  }

  override def open(parameters: Configuration): Unit = {
    super.open(parameters)
    queue = new ArrayBlockingQueue[String](queueCapacity)
    gen = new Generator(queue, rate)
    gen.start()
    port += getRuntimeContext.getIndexOfThisSubtask
    paramReceiver = new ParameterReceiver(port, getRate, setRate)
    paramReceiver.start()
    getRuntimeContext.getMetricGroup().gauge[Int,Gauge[Int]]("backlog", new Gauge[Int] {
      override def getValue: Int = {
        val c = queue.size()
        c
      }
    })
  }
  def setRate(r : Int): Unit = {
    rate = r
  }
  def getRate(): Int = {
    return rate
  }

  override def run(ctx: SourceFunction.SourceContext[String]): Unit = {
    var oldTime = System.currentTimeMillis()
    var count = 0
    while (running) {

      // using the rate here gives me an upper bound of the source's output rate
      // Maybe I do not need it?
      if (count % 500 == 0 && count >= rate) {
        while (1000 - (System.currentTimeMillis() - oldTime) > 0) {
          Thread.sleep(Math.max(1000 - (System.currentTimeMillis() - oldTime), 0))
        }
        oldTime = System.currentTimeMillis()
        count = 0
      }
      ctx.collect(queue.take())
      count += 1
    }
  }
}
