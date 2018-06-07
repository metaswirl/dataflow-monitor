
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

// "provided" -> here means sbt assembly will not add this dependencies
// TODO: It would probably be better to shade the akka version
libraryDependencies ++= Seq(
  "org.apache.flink" % "flink-scala_2.11" % FlinkVersion % "provided", 
  "org.apache.flink" % "flink-streaming-scala_2.11" % FlinkVersion % "provided",
  "org.apache.flink" % "flink-metrics-core" % FlinkVersion % "provided",
  "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.8.0",
  "com.trueaccord.scalapb" %% "scalapb-runtime" % com.trueaccord.scalapb.compiler.Version.scalapbVersion % "protobuf"
)
