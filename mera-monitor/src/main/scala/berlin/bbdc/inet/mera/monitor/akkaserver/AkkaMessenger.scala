package berlin.bbdc.inet.mera.monitor.akkaserver

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import berlin.bbdc.inet.mera.commons.akka.LoadShedderRegistration
import berlin.bbdc.inet.mera.commons.message.MetricUpdate
import berlin.bbdc.inet.mera.monitor.metrics.Counter
import berlin.bbdc.inet.mera.monitor.metrics.Gauge
import berlin.bbdc.inet.mera.monitor.metrics.Histogram
import berlin.bbdc.inet.mera.monitor.metrics.Meter
import berlin.bbdc.inet.mera.monitor.metrics.MetricKey
import berlin.bbdc.inet.mera.monitor.model.Model
import berlin.bbdc.inet.mera.monitor.model.ModelFileWriter
import berlin.bbdc.inet.mera.monitor.model.ModelTraversal
import berlin.bbdc.inet.mera.monitor.model.ModelUpdater
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class AkkaMessenger(model: Model) extends Actor {

  val LOG: Logger = LoggerFactory.getLogger(getClass)
  val mfw: ModelFileWriter = initModelWriter(model)
  val modelUpdater = ModelUpdater(model)
  val modelTraversal = ModelTraversal(model, mfw)

  var traversalFuture: Option[ScheduledFuture[_]] = None

  override def receive: PartialFunction[Any, Unit] = {
    case d: MetricUpdate =>
      processMetricUpdate(d)
    case m: LoadShedderRegistration =>
      LoadShedderManager.registerLoadShedder(m)
    case _ => LOG.warn("Could not parse message!")
  }

  override def postStop(): Unit = {
    modelTraversal.cancel()
    mfw.close()
    traversalFuture foreach (_.cancel(true))
    super.postStop()
  }

  private def initModelWriter(model: Model): ModelFileWriter = {
    val path = ConfigFactory.load().getString("dirs.metrics")
    val folder: String = if (path.length > 0) {
        path
      } else {
        s"/tmp/mera_${System.currentTimeMillis()}"
      }
    LOG.debug("Writing info to " + folder)
    val mfw = ModelFileWriter(folder, writeMetrics = true)
    mfw.writeGraph(model)
    mfw
  }

  private def processMetricUpdate(d: MetricUpdate): Unit = {
    val now = System.currentTimeMillis()
    mfw.updateMetrics(now, d.counters.map(t => (t.key, t.count.toDouble)) ++
      d.meters.map(t => (t.key, t.rate)) ++ d.gauges.map(t => (t.key, t.value)))
    modelUpdater.update(now,
      d.counters.map(t => (MetricKey.buildKey(t.key), Counter(t.count))) ++
        d.meters.map(t => (MetricKey.buildKey(t.key), Meter(t.count, t.rate))) ++
        d.hists.map(t => (MetricKey.buildKey(t.key), Histogram(t.count, t.min, t.max, t.mean))) ++
        d.gauges.map(t => (MetricKey.buildKey(t.key), Gauge(t.value)))
    )
    if (traversalFuture.isEmpty) {
      model.runtimeStatus.receivedFirstMetrics = true
      LOG.info("Started receiving metrics. Starting model traversal.")
      val schd: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
      traversalFuture = Option.apply(schd.scheduleAtFixedRate(modelTraversal, 5, 3, TimeUnit.SECONDS))
    }
  }
}

object AkkaMessenger {
  val LOG: Logger = LoggerFactory.getLogger(getClass)
  val actorSystem = ActorSystem("AkkaMessenger", loadConfig())

  def start(model: Model): Unit = {
    val remote: ActorRef = actorSystem.actorOf(Props(new AkkaMessenger(model)), name = "master")
  }

  def loadConfig(): Config = {
    val isCluster = ConfigFactory.load.getBoolean("akkaMessenger.isCluster")
    val config = ConfigFactory.load("meraAkka/akkaMessenger.conf")

    LOG.info(s"Running a cluster configuration: $isCluster")
    if (isCluster) config
    else config.withValue("akka.remote.netty.tcp.hostname", ConfigValueFactory.fromAnyRef("localhost"))
  }

}
