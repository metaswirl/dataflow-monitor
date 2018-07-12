package berlin.bbdc.inet.jobs.ThreeStageWordCount

import berlin.bbdc.inet.jobs.ThreeStageWordCount.utils.ParameterReceiver
import org.apache.flink.api.common.functions.{MapFunction, RichMapFunction}
import org.apache.flink.configuration.Configuration

class ConfigurableBottleneck[T](var delay: Long, var port: Int) extends RichMapFunction[T, T] with Serializable {
  var receiver : ParameterReceiver = _

  override def open(parameters: Configuration): Unit = {
    super.open(parameters)
    port += getRuntimeContext.getIndexOfThisSubtask
    receiver = new ParameterReceiver(port, getDelay, setDelay)
    receiver.start()
  }

  override def close(): Unit = {
    super.close()
    receiver.cancel()
  }
  def setDelay(newDelay : Int): Unit = {
    delay = newDelay.toLong
  }
  def getDelay: Int = {
    delay.toInt
  }
  override def map(value: T): T = {
    Thread.sleep(delay)
    value
  }
}
