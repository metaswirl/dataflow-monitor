package berlin.bbdc.inet.mera.server.akkaserver

import java.util.concurrent.{Executors, ScheduledExecutorService, ScheduledFuture, TimeUnit}

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import berlin.bbdc.inet.mera.common.akka.LoadShedderRegistration
import berlin.bbdc.inet.mera.message.MetricUpdate
import berlin.bbdc.inet.mera.server.metrics.{Counter, Gauge, Histogram, Meter, MetricKey}
import berlin.bbdc.inet.mera.server.model.{Model, ModelFileWriter, ModelTraversal, ModelUpdater}
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import org.slf4j.{Logger, LoggerFactory}

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

  }

  override def postStop(): Unit = {
    modelTraversal.cancel()
    mfw.close()
    traversalFuture foreach (_.cancel(true))
    super.postStop()
  }

  private def initModelWriter(model: Model): ModelFileWriter = {
    val folder: String = s"/tmp/mera_${System.currentTimeMillis()}"
    LOG.debug("Writing info to " + folder)
    val mfw = ModelFileWriter(folder, writeMetrics = true)
    mfw.writeGraph(model)
    mfw
  }

  private def processMetricUpdate(d: MetricUpdate): Unit = {
    mfw.updateMetrics(d.timestamp, d.counters.map(t => (t.key, t.count.toDouble)) ++
      d.meters.map(t => (t.key, t.rate)) ++ d.gauges.map(t => (t.key, t.value)))
    modelUpdater.update(d.timestamp,
      d.counters.map(t => (MetricKey.buildKey(t.key), Counter(t.count))).toList ++
        d.meters.map(t => (MetricKey.buildKey(t.key), Meter(t.count, t.rate))).toList ++
        d.hists.map(t => (MetricKey.buildKey(t.key), Histogram(t.count, t.min, t.max, t.mean))).toList ++
        d.gauges.map(t => (MetricKey.buildKey(t.key), Gauge(t.value))).toList
    )
    if (traversalFuture.isEmpty) {
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
