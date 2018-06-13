package berlin.bbdc.inet.mera.server.akkaserver

import akka.actor.ActorSelection
import berlin.bbdc.inet.mera.common.akka.LoadShedderRegistration
import org.slf4j.{Logger, LoggerFactory}

object LoadShedderManager {
  val LOG: Logger = LoggerFactory.getLogger(getClass)

  private val loadShedders = Map.newBuilder[String, ActorSelection]

  def registerLoadShedder(m: LoadShedderRegistration) = ???

}