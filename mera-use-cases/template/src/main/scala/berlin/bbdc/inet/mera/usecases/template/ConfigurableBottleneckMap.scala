package berlin.bbdc.inet.mera.usecases.template

import java.nio.file.Paths

import berlin.bbdc.inet.mera.usecases.template.utils.ParameterReceiverSocket
import berlin.bbdc.inet.mera.usecases.template.utils.ParameterReceiverHTTP
import org.apache.flink.api.common.functions.RichMapFunction
import org.apache.flink.configuration.Configuration
import com.typesafe.config.ConfigFactory
import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.metrics.Gauge

class ConfigurableBottleneckMap[T](var delay: Long = 0) extends RichMapFunction[T, T] with Serializable {
  var receiver : ParameterReceiverHTTP = _

  override def open(parameters: Configuration): Unit = {
    super.open(parameters)
    val rc = this.getRuntimeContext
    getRuntimeContext().getExecutionConfig().getGlobalJobParameters() match {
      case param : ParameterTool =>
        val stateDir = param.getRequired("dirs.state");
        val portFilePath = Some(Paths.get(stateDir,
          "bottleneck-" + rc.getTaskName + "-" + rc.getIndexOfThisSubtask))
        receiver = new ParameterReceiverHTTP(getDelay, setDelay, portFilePath)
        receiver.start()
      case _ => throw new ClassCastException
    }
    getRuntimeContext.getMetricGroup().gauge[Long,Gauge[Long]]("bottleneckDelay", new Gauge[Long] {
      override def getValue: Long = delay
    })
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
