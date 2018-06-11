package berlin.bbdc.inet.mera.server.akkaserver

import java.util.concurrent.{Executors, ScheduledExecutorService, ScheduledFuture, TimeUnit}

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
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
  }

  override def postStop(): Unit = {
    modelTraversal.cancel()
    mfw.close()
    traversalFuture match {
      case Some(f) => f.cancel(true)
      case None =>
    }
    super.postStop()
  }

  def initModelWriter(model: Model): ModelFileWriter = {
    val folder: String = s"/tmp/mera_${System.currentTimeMillis()}"
    LOG.info("Writing info to " + folder)
    val mfw = ModelFileWriter(folder, writeMetrics = true)
    mfw.writeGraph(model)
    mfw
  }

  def processMetricUpdate(d: MetricUpdate): Unit = {
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

  def start(model: Model): Unit = {
    val actorSystem = ActorSystem("AkkaMetric", loadConfig())
    val remote: ActorRef = actorSystem.actorOf(Props(new AkkaMessenger(model)), name = "master")
  }

  def loadConfig(): Config = {
    val isCluster = ConfigFactory.load.getBoolean("metricReceiver.isCluster")
    val config = ConfigFactory.load("meraAkka/metricServer.conf")

    LOG.info(s"Running a cluster configuration: $isCluster")
    if (isCluster) config
    else config.withValue("akka.remote.netty.tcp.hostname", ConfigValueFactory.fromAnyRef("localhost"))
  }

}