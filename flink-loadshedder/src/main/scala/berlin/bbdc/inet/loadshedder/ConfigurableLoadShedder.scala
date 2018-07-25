package berlin.bbdc.inet.loadshedder

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
import berlin.bbdc.inet.loadshedder.AkkaMessenger.Tick
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import org.apache.flink.api.common.functions.RichFlatMapFunction
import org.apache.flink.configuration.Configuration
import org.apache.flink.configuration.GlobalConfiguration
import org.apache.flink.configuration.JobManagerOptions
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

  // count of dropped items
  var dropCount = 0L

  // drop rate that will become active in the next time window
  var newDropRate: Int = dropRate

  var now: Long = lastTimestamp

  var taskName = ""

  private val config = ConfigFactory.load()

  val jobManagerIpAddress: String = GlobalConfiguration.loadConfiguration.getString(JobManagerOptions.ADDRESS)

  lazy val system: ActorSystem = ActorSystem(config.getString("loadshedder.system"), loadAkkaConfig())

  // Mera Actor
  lazy val master: ActorSelection = system.actorSelection(
    s"akka.tcp://${config.getString("mera.system")}@$jobManagerIpAddress:${config.getInt("mera.port")}/user/master")

  // Local actor talking to Mera
  lazy val akkaMessenger: ActorRef = system.actorOf(AkkaMessenger.props(master), name = taskName)

  override def open(parameters: Configuration): Unit = {
    super.open(parameters)
    taskName = getRuntimeContext.getTaskName + "." + getRuntimeContext.getIndexOfThisSubtask.toString
    registerLoadShedder()
  }

  def setDropRate(value: Int): Unit = {
    newDropRate = value
  }

  override def flatMap(value: T, out: Collector[T]): Unit = {
    // shortcut, do nothing when no drop is configured
    if (newDropRate <= 0 && dropRate <= 0) {
      out.collect(value)
      return
    }
    // for performance reasons we take new timestamps only every 1000 items
    // TODO: replace this check with a timer (e.g. java.utilTimer)
    if (count % 1000 == 0) now = System.currentTimeMillis()

    if (now - lastTimestamp > 1000) {
      lastTimestamp = now
      dropCount = 0
      dropRate = newDropRate
    }
    count += 1

    // drop the first n items in a time window
    if (dropCount < dropRate) {
      dropCount += 1
    } else {
      out.collect(value)
    }
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
      LOG.debug(s"Loadshedder new value received for $flinkTaskName: ${m.value}")
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
