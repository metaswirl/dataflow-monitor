resolvers in ThisBuild ++= Seq("Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
  "Apache Development Snapshot Repository" at "https://repository.apache.org/content/repositories/snapshots/",
  Resolver.mavenLocal, Resolver.bintrayRepo("commercetools", "maven"))

name := "flinkMetricsScala"

version := "0.1"

organization := "berlin.bbdc.inet.flinkReporterScala"

val flinkVersion = "1.3.2"

// -------- https://scalapb.github.io/index.html ---- BEGIN
PB.targets in Compile := Seq(
  scalapb.gen() -> (sourceManaged in Compile).value
)

// (optional) If you need scalapb/scalapb.proto or anything from
// google/protobuf/*.proto
libraryDependencies += "com.trueaccord.scalapb" %% "scalapb-runtime" % com.trueaccord.scalapb.compiler.Version.scalapbVersion % "protobuf"
// -------- https://scalapb.github.io/index.html ---- END


scalaVersion := "2.11.12"
// using 2.11.12 because Flink requires 2.11 :-/

// WARNING: Intellij throws a fit, sbt from console works fine!

libraryDependencies ++= Seq(
  "org.slf4j" % "slf4j-api" % "1.7.25",
  "org.slf4j" % "slf4j-log4j12" % "1.7.25",
  "io.dropwizard.metrics" % "metrics-core" % "3.1.0",
  "org.apache.flink" % "flink-scala_2.11" % flinkVersion, // different scala version not sure if this is a problem
  "org.apache.flink" % "flink-streaming-scala_2.11" % flinkVersion,
  "org.apache.flink" % "flink-metrics-core" % flinkVersion,
  "com.typesafe.akka" % "akka-actor_2.11" % "2.4.20"
)

// %% - means add scala version to end of name
