package berlin.bbdc.inet.mera.server.akkaserver

import java.util.concurrent.{Executor, Executors, TimeUnit}

import akka.actor.ActorSelection
import berlin.bbdc.inet.mera.common.akka.{ConfirmRegistration, LoadShedderRegistration, SendNewValue}
import org.slf4j.{Logger, LoggerFactory}

import scala.util.Random

object LoadShedderManager {
  val LOG: Logger = LoggerFactory.getLogger(getClass)

  private var loadShedders: Map[String, ActorSelection] = Map()
  private val scheduler = Executors.newSingleThreadScheduledExecutor()

  def registerLoadShedder(m: LoadShedderRegistration): Unit = {
    LOG.debug(s"Register loadShedder ${m.id} at ${m.address}:${m.port}")
    val remoteMaster = AkkaMessenger.actorSystem.actorSelection(s"akka.tcp://LoadShedderSystem@${m.address}:${m.port}/user/${m.id}")
    loadShedders += (m.id -> remoteMaster)
    remoteMaster ! ConfirmRegistration(m.id)

    scheduler.schedule(new Runnable {
      val r: Random = Random
      def run(): Unit = LoadShedderManager.sendNewValue(m.id, r.nextDouble())
    }, 5, TimeUnit.SECONDS)
  }

  def sendNewValue(loadShedderId: String, value: Double): Unit = {
    loadShedders.get(loadShedderId) match {
      case Some(actor) => actor ! SendNewValue(value)
      case None => throw new Error(s"Trying to send new value to an unknown loadshedder $loadShedderId")
    }
  }
}
