package berlin.bbdc.inet.mera.loadshedder

import java.util.concurrent.{Executors, ScheduledExecutorService, TimeUnit}

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSelection
import akka.actor.ActorSystem
import akka.actor.Address
import akka.actor.Cancellable
import akka.actor.ExtendedActorSystem
import akka.actor.Extension
import akka.actor.ExtensionId
import akka.actor.Props
import berlin.bbdc.inet.mera.commons.akka.ConfirmRegistration
import berlin.bbdc.inet.mera.commons.akka.LoadShedderRegistration
import berlin.bbdc.inet.mera.commons.akka.SendNewValue
import berlin.bbdc.inet.mera.loadshedder.AkkaMessenger.Tick
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import org.apache.flink.api.common.functions.RichFlatMapFunction
import org.apache.flink.configuration.Configuration
import org.apache.flink.configuration.GlobalConfiguration
import org.apache.flink.configuration.JobManagerOptions
import org.apache.flink.metrics.Gauge
import org.apache.flink.util.Collector
import org.slf4j.LoggerFactory

import scala.concurrent.duration._

/**
 * Loadshedder variant of the RichFlatMapFunction. The operator drops the first
 * n records every second. The number n is decided by the dropRate parameter.
 *
 * The operator uses Akka to communicate with Mera-monitor. It sets up an Akka
 * ActorSystem with a name taken from application.conf file under
 * "loadshedder.system". It then connects to Mera-monitor which should be
 * reachable under the system name taken from "mera.system" and port
 * "mera.port".  Once connected to Mera, the operator is able to receive new
 * values of the dropRate parameter.
 *
 * The basic syntax for using a ConfigurableLoadShedder:
 *
 * DataSet<X> input = ...;
 * DataSet<Y> result = input.flatMap(new ConfigurableLoadShedder[X]());
 *
 *
 * @param dropRate Initial value of dropRate parameter (default = 0).
 * @tparam T Type of the input and output elements.
 */
class ConfigurableLoadShedder[T](private var dropRate: Int = 0)
  extends RichFlatMapFunction[T, T] with Serializable {

  import system.dispatcher

  // end of last time window
  var lastTimestamp: Long = System.currentTimeMillis()

  // count of forwarded items
  var count = 0L
  var numberOfRecords = Long.MaxValue

  // count of dropped items
  var dropCount = 0L

  // drop rate that will become active in the next time window
  var newDropRate: Int = dropRate

  // ratio between number of items and drop rate
  var dropRatio: Long = 0

  var taskName = ""

  private val config = ConfigFactory.load()

  val jobManagerIpAddress: String = GlobalConfiguration.loadConfiguration.getString(JobManagerOptions.ADDRESS)

  lazy val system: ActorSystem = ActorSystem(config.getString("loadshedder.system"), loadAkkaConfig())

  // Mera Actor
  lazy val master: ActorSelection = system.actorSelection(
    s"akka.tcp://${config.getString("mera.system")}@$jobManagerIpAddress:${config.getInt("mera.port")}/user/master")
  private val LOG = LoggerFactory.getLogger(getClass)
  LOG.info(s"found jobmanager at $jobManagerIpAddress${config.getInt("mera.port")}")

  // Local actor talking to Mera
  lazy val akkaMessenger: ActorRef = system.actorOf(AkkaMessenger.props(master), name = taskName)

  override def open(parameters: Configuration): Unit = {
    super.open(parameters)
    taskName = getRuntimeContext.getTaskName + "." + getRuntimeContext.getIndexOfThisSubtask.toString
    registerLoadShedder()
    getRuntimeContext.getMetricGroup().gauge[Long,Gauge[Long]]("dropCount", new Gauge[Long] {
      override def getValue: Long = dropCount
    })
    getRuntimeContext.getMetricGroup().gauge[Int,Gauge[Int]]("dropRate", new Gauge[Int] {
      override def getValue: Int = dropRate
    })
    Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(new Runnable {
      override def run(): Unit = {
        reset()
      }
    }, 0, 1, TimeUnit.SECONDS)
    // schedule resetting the counter every second
  }

  def setDropRate(value: Int): Unit = {
    newDropRate = value
  }

  def reset() = {
    synchronized {
      dropRate = newDropRate
      numberOfRecords = count // could be average across multiple windows
      count = 0
      dropRatio=if (dropRate > 0) (numberOfRecords / dropRate) else 0
    }
  }

  override def flatMap(value: T, out: Collector[T]): Unit = {
    /* shortcut, do nothing when no drop is configured
       methods:
        a) count number of records per time window
           TODO: generate average from last m time window
           a1) sample every nth packet.
               Disadvantage: could be to regular.
           a2) drop each packet with a probability 1/n
               Disadvantage: could be to costly in terms of performance
           a3) drop every n/k packet with the probability 1/(n*k)
        b) drop items at the beginning of the time window
    */
    // TODO: Is the synchronized statement really necessary?
    synchronized {
      count += 1
    }
    if (dropRatio > 0 && count % dropRatio == 0) {
      dropCount += 1
      return
    }
    out.collect(value)
  }

  private def registerLoadShedder(): Unit = {
    // load the network address and port
    val address = AddressExtension.hostOf(system)
    val port = AddressExtension.portOf(system)
    // frequent message sent until registration is confirmed
    val cancellable= system.scheduler.schedule(
      0 seconds,
      5 seconds,
      akkaMessenger,
      Tick
    )
    // initialize loadshedder registration in Mera
    akkaMessenger ! InitRegistration(taskName, address, port, setDropRate, cancellable)
    // periodic message sent in case Mera was restarted
    system.scheduler.schedule(
      0 milliseconds,
      30 seconds,
      akkaMessenger,
      Tick
    )
  }

  // checks if Flink is ran in a cluster configuration
  private def loadAkkaConfig(): Config = {
    val akkaConfig = ConfigFactory.load("akkaConfig.conf")

    if (config.getBoolean("isCluster")) {
      akkaConfig
    } else {
      akkaConfig.withValue("akka.remote.netty.tcp.hostname", ConfigValueFactory.fromAnyRef("localhost"))
    }
  }
}

// Actor for communication with Mera-monitor
class AkkaMessenger(master: ActorSelection) extends Actor {

  private val LOG = LoggerFactory.getLogger(getClass)

  var flinkTaskName = ""
  var flinkTaskAddress = ""
  var flinkTaskPort = 0
  //function to set the dropRate param of the task
  var setter: Int => Unit = _
  var cancellable: Cancellable = _

  def receive: Receive = {
    case m: InitRegistration =>
      flinkTaskName = m.id
      flinkTaskAddress = m.address
      flinkTaskPort = m.port
      setter = m.setter
      cancellable = m.cancellable
      LOG.info("Loadshedder init registration: " + flinkTaskName + ", " + flinkTaskAddress + ":" + flinkTaskPort)
      // start periodic task in case Mera is not responding
      master ! LoadShedderRegistration(flinkTaskName, flinkTaskAddress, flinkTaskPort)
    case Tick =>
      master ! LoadShedderRegistration(flinkTaskName, flinkTaskAddress, flinkTaskPort)
    case m: ConfirmRegistration =>
      assert(flinkTaskName == m.id)
      cancellable.cancel()
      LOG.info(s"Loadshedder ${m.id} registration confirmed")
      // register every 30s in case Mera was restarted
    case m: SendNewValue =>
      LOG.info(s"Loadshedder new value received for $flinkTaskName: ${m.value}")
      setter(m.value)
  }
}

object AkkaMessenger {
  def props(master: ActorSelection): Props = Props(new AkkaMessenger(master))

  case object Tick

}

class AddressExtension(system: ExtendedActorSystem) extends Extension {
  val address: Address = system.provider.getDefaultAddress
}

object AddressExtension extends ExtensionId[AddressExtension] {
  def hostOf(system: ActorSystem): String = AddressExtension(system).address.host.getOrElse("")

  def portOf(system: ActorSystem): Int = AddressExtension(system).address.port.getOrElse(0)

  def createExtension(system: ExtendedActorSystem): AddressExtension = new AddressExtension(system)
}

final case class InitRegistration(id: String, address: String, port: Int, setter: Int => Unit, cancellable: Cancellable)
