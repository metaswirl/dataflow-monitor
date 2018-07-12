package berlin.bbdc.inet.jobs.ThreeStageWordCount

import org.apache.flink.streaming.api.scala._
import berlin.bbdc.inet.loadshedder.ConfigurableLoadShedder

object Job extends App {
  val env = StreamExecutionEnvironment.getExecutionEnvironment
  env.disableOperatorChaining()
  env.getConfig.setLatencyTrackingInterval(500)

  val text = env.addSource(new ConfigurableStringSource(10, 10000, 22300)).name("source").setParallelism(1).disableChaining()

  val counts = text.flatMap(new ConfigurableLoadShedder[String](0)).name("loadshedder0").setParallelism(1)
                   .flatMap { _.toLowerCase.split("\\W+").filter { _.nonEmpty } }
                      .flatMap(new ConfigurableLoadShedder[String](0)).name("loadshedder1")
                      .setParallelism(3)
                   .map { (_, 1) }
                      .setParallelism(2)
                      .flatMap(new ConfigurableLoadShedder[(String, Int)](0)).name("loadshedder2")
                      .setParallelism(2)
    .keyBy(0)
    .sum(1).setParallelism(2)
                   .map(new ConfigurableBottleneck[(String, Int)](500, 22333)).name("bottleneck").setParallelism(1)
                   .print.setParallelism(2)

  env.execute("ThreeStageWordCount")
}



