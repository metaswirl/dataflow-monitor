package berlin.bbdc.inet.mera.server.model

import java.io.{File, PrintWriter}

class ModelFileWriter(val folder: String) {
  // TODO: Find better format for target and inferred metrics. Especially for variable sized values, such as the input distribution.
  val folderFile = new File(folder)
  folderFile.mkdirs()

  val graphWriter: PrintWriter = new PrintWriter(folder + "/graph")
  val metricWriter: PrintWriter = new PrintWriter(folder + "/metrics.csv")
  val inferredMetricWriter: PrintWriter = new PrintWriter(folder + "/inferred_metrics.csv")
  val targetMetricWriter: PrintWriter = new PrintWriter(folder + "/target_metrics.csv")
  metricWriter.write("time;key;value\n")
  inferredMetricWriter.write("time;task;selectivity;inputRate;capacity;inQueue;outQueue;inDist;outDist\n")
  targetMetricWriter.write("time;task;targetInputRate;targetOutputRate;targetPartialOutRate\n")

  def writeGraph(model : Model): Unit = {
    graphWriter.write(model.toString)
    graphWriter.flush()
  }
  def updateMetrics(ts:Long, metrics: Iterable[(String, Double)]): Unit = {
    metrics.foreach { case (key: String, value: Double) => metricWriter.write(f"$ts;$key;$value\n") }
    metricWriter.flush()
  }
  def updateInferredMetrics(model : Model): Unit = {
    for (task <- model.tasks) {
      inferredMetricWriter.write(f"${System.currentTimeMillis()};${task.parent.id}%s.${task.subtask}%d;${task.selectivity};${task.inRate};${task.capacity};${task.inQueueSaturation};${task.outQueueSaturation};${task.inDist};${task.outDist}\n")
    }
    inferredMetricWriter.flush()
  }
  def updateTargetMetrics(model:Model): Unit = {
    for (task <- model.tasks) {
      targetMetricWriter.write(f"${System.currentTimeMillis()};${task.parent.id}%s.${task.subtask}%d;${task.targetInRate};${task.targetOutRate};${task.targetPartialOutRate}\n")
    }
    targetMetricWriter.flush()
  }
}
