
name := "flink-reporter"

version := "0.1"

scalaVersion := "2.11.12"

organization := "berlin.bbdc.inet"

val FlinkVersion = "1.4.2"
val AkkaVersion = "2.4.20"

// -------- https://scalapb.github.io/index.html ----
PB.targets in Compile := Seq(
  scalapb.gen() -> (sourceManaged in Compile).value
)

libraryDependencies ++= Seq(
  "org.apache.flink" % "flink-scala_2.11" % FlinkVersion, // different scala version not sure if this is a problem
  "org.apache.flink" % "flink-streaming-scala_2.11" % FlinkVersion,
  "org.apache.flink" % "flink-metrics-core" % FlinkVersion,
  "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.8.0",
  "com.trueaccord.scalapb" %% "scalapb-runtime" % com.trueaccord.scalapb.compiler.Version.scalapbVersion % "protobuf"
)


assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs@_*) => MergeStrategy.discard
  case PathList("akka", xs@_*) => MergeStrategy.first
  case PathList("reference.conf", xs@_*) => MergeStrategy.first
  case PathList("rootdoc.txt", xs@_*) => MergeStrategy.first
  // Default strategy
  case x => MergeStrategy.deduplicate
}
