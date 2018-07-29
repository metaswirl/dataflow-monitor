package berlin.bbdc.inet.mera.usecases.ThreeStageWordCount

import org.apache.flink.streaming.api.scala._
//import org.apache.flink.api.scala._
import berlin.bbdc.inet.mera.loadshedder.ConfigurableLoadShedder
import berlin.bbdc.inet.mera.usecases.template.{ConfigurableBottleneckMap, JobTemplate}
import org.apache.flink.api.common.functions.{FlatMapFunction, RichFlatMapFunction, RichMapFunction}
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator
import org.apache.flink.util.Collector

object Job extends JobTemplate {
  val env = StreamExecutionEnvironment.getExecutionEnvironment
  env.disableOperatorChaining()
  env.getConfig.setLatencyTrackingInterval(500)

  val text = env.addSource(new ConfigurableStringSource(10, 10000, 22300)).name("source").setParallelism(1).disableChaining()

  val counts = text.flatMap(new ConfigurableLoadShedder[String]())
                      .name("loadshedder0")
                      .setParallelism(1)
                    .flatMap { _.toLowerCase.split("\\W+").filter { _.nonEmpty } }
                    .flatMap(new ConfigurableLoadShedder[String]())
                      .name("loadshedder1")
                      .setParallelism(3)
                    .map { (_, 1) }.setParallelism(2)
                    .keyBy(0)
                    .sum(1)
                       .setParallelism(2)
                    .map(new ConfigurableBottleneckMap[(String, Int)]())
                       .name("bottleneck")
                       .setParallelism(1)
      .print

  //defaultWriteAsCsv(counts)

  env.execute("ThreeStageWordCount")
}



