package berlin.bbdc.inet.jobs.template

import java.nio.file.Paths

import berlin.bbdc.inet.jobs.template.utils.{ParameterReceiverHTTP, ParameterReceiverSocket}
import org.apache.flink.api.common.functions.RichMapFunction
import org.apache.flink.configuration.Configuration
import com.typesafe.config.ConfigFactory

class ConfigurableBottleneckMap[T](var delay: Long = 0) extends RichMapFunction[T, T] with Serializable {
  var receiver : ParameterReceiverHTTP = _

  override def open(parameters: Configuration): Unit = {
    super.open(parameters)
    val portFilePath = Some(Paths.get(ConfigFactory.load().getString("dirs.state"),
                            this.getRuntimeContext.getTaskNameWithSubtasks))
    receiver = new ParameterReceiverHTTP(getDelay, setDelay, portFilePath)
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
