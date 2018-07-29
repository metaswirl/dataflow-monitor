package berlin.bbdc.inet.mera.usecases.template

import java.nio.file.{Files, Path, Paths}

import com.typesafe.config.ConfigFactory


class JobTemplate extends App {
  val defaultOutputDirPath = Paths.get(ConfigFactory.load().getString("dirs.output"))
  val defaultOutputFilePath = defaultOutputDirPath.resolve("result-" + System.currentTimeMillis())
  val defaultStateDirPath = Paths.get(ConfigFactory.load().getString("dirs.state"))
  JobTemplate.createFolderIfNotExists(defaultOutputDirPath)
  JobTemplate.createFolderIfNotExists(defaultStateDirPath)
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
