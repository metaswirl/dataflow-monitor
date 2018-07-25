ThisBuild / resolvers ++= Seq(
  "Apache Development Snapshot Repository" at "https://repository.apache.org/content/repositories/snapshots/",
  //"Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
  Resolver.mavenLocal
)

name := "ThreeStageWordCount"
version := "0.1"
organization := "berlin.bbdc.inet.jobs"

scalaVersion := "2.11.12"
val flinkVersion = "1.4.2"

libraryDependencies ++= Seq(
  "org.apache.flink" %% "flink-scala" % flinkVersion ,
  "org.apache.flink" %% "flink-streaming-scala" % flinkVersion ,
  "berlin.bbdc.inet" % "flink-loadshedder_2.11" % "0.1.0"
)

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}
