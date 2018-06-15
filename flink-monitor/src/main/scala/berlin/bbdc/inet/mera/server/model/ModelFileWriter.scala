package berlin.bbdc.inet.mera.server.model

import java.io.{File, PrintWriter}

case class ModelFileWriter(folder: String, writeMetrics: Boolean = false) {
  // TODO: Find better format for target and inferred metrics. Especially for variable sized values, such as the input distribution.
  val folderFile = new File(folder)
  folderFile.mkdirs()

  val startOptimization: PrintWriter = new PrintWriter(folder + "/optimization_start.csv")
  val graphWriter: PrintWriter = new PrintWriter(folder + "/graph.csv")
  val metricWriter: PrintWriter = new PrintWriter(folder + "/metrics.csv")
  val inferredMetricNodeWriter: PrintWriter = new PrintWriter(folder + "/inferred_metrics_nodes.csv")
  val inferredMetricEdgeWriter: PrintWriter = new PrintWriter(folder + "/inferred_metrics_edges.csv")
  val targetMetricWriter: PrintWriter = new PrintWriter(folder + "/target_metrics.csv")
  metricWriter.write("time;key;value\n")
  inferredMetricNodeWriter.write("time;task;selectivity;inputRate;capacity;inQueue;outQueue\n")
  inferredMetricEdgeWriter.write("time;source;target;outFraction;inFraction\n")
  targetMetricWriter.write("time;task;targetInputRate;targetOutputRate;targetPartialOutRate\n")


  def writeStartOptimization(): Unit = {
    val now = System.currentTimeMillis()
    startOptimization.write(s"$now\n")
    startOptimization.flush()
  }
  def writeGraph(model : Model): Unit = {
    graphWriter.write("source;target\n")
    graphWriter.write(model.taskEdges.map(te => s"${te.source};${te.target}\n").mkString)
    graphWriter.flush()
  }
  def updateMetrics(ts:Long, metrics: Iterable[(String, Double)]): Unit = {
    if (writeMetrics) {
      metrics.foreach { case (key: String, value: Double) => metricWriter.write(f"$ts;$key;$value\n") }
      metricWriter.flush()
      metricWriter.flush()
    }
  }
  def updateInferredMetrics(model : Model): Unit = {
    val now = System.currentTimeMillis()
    for (task <- model.tasks.values) {
      //TODO: change to s interpolation if possible
      inferredMetricNodeWriter.write(f"$now;${task.id};${task.selectivity};${task.inRate};${task.capacity};${task.inQueueSaturation};${task.outQueueSaturation}\n")
    }
    for (te <- model.taskEdges) {
      inferredMetricEdgeWriter.write(f"$now;${te.source};${te.target};${te.outF};${te.inF}\n")
    }
    inferredMetricNodeWriter.flush()
    inferredMetricEdgeWriter.flush()
  }
  def updateTargetMetrics(model:Model): Unit = {
    for (task <- model.tasks.values) {
      targetMetricWriter.write(f"${System.currentTimeMillis()};${task.id};${task.targetInRate};${task.targetOutRate};${task.targetPartialOutRate}\n")
    }
    targetMetricWriter.flush()
  }
  def close(): Unit = {
    graphWriter.flush()
    metricWriter.flush()
    inferredMetricNodeWriter.flush()
    inferredMetricEdgeWriter.flush()
    targetMetricWriter.flush()

    graphWriter.close()
    metricWriter.close()
    inferredMetricNodeWriter.close()
    inferredMetricEdgeWriter.close()
    targetMetricWriter.close()
    startOptimization.close()
  }
}
