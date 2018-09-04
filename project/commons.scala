import sbt._
import Keys._
import sbtassembly.AssemblyKeys._
import sbtassembly.MergeStrategy
import sbtassembly.PathList

object Commons {
  val commonSettings: Seq[Def.Setting[_]] = Seq(
    organization := "berlin.bbdc.inet",
    scalaVersion := "2.11.12",
    version := "0.1-SNAPSHOT",
    test in assembly := {},
    resolvers ++= Seq(
      "Apache Development Snapshot Repository" at "https://repository.apache.org/content/repositories/snapshots/",
      "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
      Resolver.mavenLocal
    ),
    assemblyOutputPath in assembly := file(".") /
      "artifacts" / (name.value + "-" + version.value + ".jar"),
    cleanFiles += file(".") / "artifacts", //baseDirectory { base => base / "artifacts" },
    assemblyMergeStrategy in assembly := {
      case PathList("META-INF", xs @ _*) => MergeStrategy.discard
      case "application.conf" => MergeStrategy.concat
      case "reference.conf" => MergeStrategy.concat
      case x => MergeStrategy.first
    }
  )
}
