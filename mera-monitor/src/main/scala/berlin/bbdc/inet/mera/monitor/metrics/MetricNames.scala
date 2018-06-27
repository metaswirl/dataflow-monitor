package berlin.bbdc.inet.mera.monitor.metrics

object MetricNames {
  def buffersByChannel(taskNumber : Int) : String = f"Network.Output.0.$taskNumber%d.buffersByChannel"
  val outPoolUsage = "buffers.outPoolUsage"
  val inPoolUsage = "buffers.inPoolUsage"
  val numRecordsInPerSecond = "numRecordsInPerSecond"
  val numRecordsOutPerSecond = "numRecordsOutPerSecond"
  val numRecordsOut = "numRecordsOut"
  val numRecordsIn = "numRecordsIn"
}
