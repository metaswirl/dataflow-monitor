import de.tuberlin.inet.mera.collector.server._
import de.tuberlin.inet.mera.flink.FlinkConnector
import javax.servlet.ServletContext
import org.scalatra._
import org.slf4j.{Logger, LoggerFactory}

class ScalatraBootstrap extends LifeCycle {
  val logger: Logger = LoggerFactory.getLogger(getClass)

  override def init(context: ServletContext) {
    context.mount(new CollectorRestServlet, "/*")
  }

  FlinkConnector.interval = 5
  FlinkConnector.scheduleFlinkPeriodicRequest()
}
