package berlin.bbdc.inet.loadshedder

import akka.actor.{Actor, ActorRef, ActorSelection, ActorSystem, Address, ExtendedActorSystem, Extension, ExtensionId, Props, Timers}
import berlin.bbdc.inet.loadshedder.AkkaMessenger.{Tick, TickKey}
import berlin.bbdc.inet.mera.common.akka.{ConfirmRegistration, LoadShedderRegistration, SendNewValue}
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import org.apache.flink.api.common.functions.RichFlatMapFunction
import org.apache.flink.configuration.{Configuration, GlobalConfiguration, JobManagerOptions}
import org.apache.flink.util.Collector
import org.slf4j.LoggerFactory

import scala.concurrent.duration._

/**
 * Loadshedder variant of the RichFlatMapFunction. The operator intends to drop records
 * in regards to its dropRate parameter.
 * The operator uses Akka to communicate with Mera-monitor. It sets up an Akka ActorSystem with a name
 * taken from application.conf file under "loadshedder.system". It then connects to Mera-monitor
 * which should be reachable under the system name taken from "mera.system" and port "mera.port".
 * Once connected to Mera, the operator is able to receive new values of the dropRate parameter.
 *
 * The basic syntax for using a ConfigurableLoadShedder:
 *
 * DataSet<X> input = ...;
 * DataSet<Y> result = input.flatMap(new ConfigurableLoadShedder());
 *
 *
 * @param dropRate Initial value of dropRate parameter (default = 0).
 * @tparam T Type of the input and output elements.
 */
class ConfigurableLoadShedder[T](private var dropRate: Int = 0)
  extends RichFlatMapFunction[T, T] with Serializable {
  //TODO: please document these variables. do we need all of them? //Kajetan
  var refTime: Long = System.currentTimeMillis()
  var count: Long = 0
  var dropCount = 0L
  var newDropRate: Int = dropRate
  var now: Long = refTime

  // name of the task
  var myName = ""

  val config = ConfigFactory.load()

  // IP address of Job Manager
  val jobManagerIpAddress: String = GlobalConfiguration.loadConfiguration.getString(JobManagerOptions.ADDRESS)

  // local Akka ActorSystem
  lazy val system: ActorSystem = ActorSystem(config.getString("loadshedder.system"), loadAkkaConfig())

  // Mera Actor
  lazy val master: ActorSelection = system.actorSelection(
    s"akka.tcp://${config.getString("mera.system")}@$jobManagerIpAddress:${config.getInt("mera.port")}/user/master")

  // Local actor talking to Mera
  lazy val akkaMessenger: ActorRef = system.actorOf(AkkaMessenger.props(master), name = myName)

  override def open(parameters: Configuration): Unit = {
    super.open(parameters)
    myName = getRuntimeContext.getTaskName + "." + getRuntimeContext.getIndexOfThisSubtask.toString
    registerLoadShedder()
  }

  def setDropRate(value: Int): Unit = {
    newDropRate = value
  }

  override def flatMap(value: T, out: Collector[T]): Unit = {
    if (count % 1000 == 0) now = System.currentTimeMillis()
    if (now - refTime > 1000) {
      refTime = now
      count = 0
      dropCount = 0
      dropRate = newDropRate
    }
    if (newDropRate <= 0) {
      out.collect(value)
      return
    }
    count += 1
    if (count <= dropRate) {
      dropCount += 1
    } else {
      out.collect(value)
    }

  }

  private def registerLoadShedder(): Unit = {
    // load the network address and port
    val address = AddressExtension.hostOf(system)
    val port = AddressExtension.portOf(system)
    // initialize loadshedder registration in Mera
    akkaMessenger ! InitRegistration(myName, address, port, setDropRate)
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
class AkkaMessenger(master: ActorSelection) extends Actor with Timers {
  val LOG = LoggerFactory.getLogger(getClass)

  // name of the Flink task
  var name = ""
  // network address of the task
  var address = ""
  //network port of the task
  var port = 0
  //function to set the dropRate param of the task
  var setter: Int => Unit = _

  def receive: Receive = {
    case m: InitRegistration =>
      name = m.id
      address = m.address
      port = m.port
      setter = m.setter
      LOG.info("Loadshedder init registration: " + name + ", " + address + ":" + port)
      // start periodic task in case Mera is not responding
      timers.startPeriodicTimer(TickKey, Tick, 5.second)
      master ! LoadShedderRegistration(name, address, port)
    case Tick =>
      master ! LoadShedderRegistration(name, address, port)
    case m: ConfirmRegistration =>
      assert(name == m.id)
      timers.cancel(TickKey)
      LOG.info(s"Loadshedder ${m.id} registration confirmed")
    case m: SendNewValue =>
      LOG.debug(s"Loadshedder new value received for $name: ${m.value}")
      setter(m.value)
  }
}

object AkkaMessenger {
  def props(master: ActorSelection): Props = Props(new AkkaMessenger(master))

  private case object TickKey

  private case object Tick

}

class AddressExtension(system: ExtendedActorSystem) extends Extension {
  val address: Address = system.provider.getDefaultAddress
}

object AddressExtension extends ExtensionId[AddressExtension] {
  def hostOf(system: ActorSystem): String = AddressExtension(system).address.host.getOrElse("")

  def portOf(system: ActorSystem): Int = AddressExtension(system).address.port.getOrElse(0)

  def createExtension(system: ExtendedActorSystem): AddressExtension = new AddressExtension(system)
}

final case class InitRegistration(id: String, address: String, port: Int, setter: Int => Unit)
