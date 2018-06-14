package berlin.bbdc.inet.mera.common.akka

final case class LoadShedderRegistration(id: String, address: String, port: Int)

final case class ConfirmRegistration(id: String)

final case class SendNewValue(value: Int)
