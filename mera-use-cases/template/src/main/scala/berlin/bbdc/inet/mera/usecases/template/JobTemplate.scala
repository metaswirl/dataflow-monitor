package berlin.bbdc.inet.mera.usecases.template

import java.nio.file.{Files, Path, Paths}

import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.streaming.api.scala._
import scala.collection.JavaConversions._

class JobTemplate extends App {
  val props = ParameterTool.fromArgs(args)
  println("parameter: " + args.mkString("|"))
  println(props.toMap.map(pair => pair._1+"="+pair._2).mkString(";"))
  val defaultOutputDirPath = Paths.get(props.get("dirs.output"))
  val defaultStateDirPath = Paths.get(props.get("dirs.state"))
  JobTemplate.createFolderIfNotExists(defaultOutputDirPath)
  JobTemplate.createFolderIfNotExists(defaultStateDirPath)

  val env = StreamExecutionEnvironment.getExecutionEnvironment
  env.getConfig.setGlobalJobParameters(props)

  /*
    props.getConfiguration.toMap.foreach(it =>
      println(s"\tkey='${it._1}' -> value='${it._2}'")
    )
  */
}

object JobTemplate {
  private def createFolderIfNotExists(path: Path) = {
    if (Files.exists(path) && ! Files.isDirectory(path)) {
      throw new Exception(s"directory $path exists and is not a directory")
    } else if (! Files.exists(path)) {
      Files.createDirectories(path)
    }
  }
}
