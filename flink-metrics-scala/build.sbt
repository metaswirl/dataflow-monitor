resolvers in ThisBuild ++= Seq("Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
  "Apache Development Snapshot Repository" at "https://repository.apache.org/content/repositories/snapshots/",
  Resolver.mavenLocal, Resolver.bintrayRepo("commercetools", "maven"))

name := "mera"

version := "0.2"

organization := "berlin.bbdc.inet"

val FlinkVersion = "1.4.2"
val AkkaVersion = "2.5.11"
val AkkaHttpVersion = "10.1.0"
val HttpClientVersion = "4.5.5"
val JacksonScalaVersion = "2.9.4"
val Specs2Version = "4.0.2"


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

// %% - means add scala version to end of name
libraryDependencies ++= Seq(
  //"org.slf4j" % "slf4j-api" % "1.7.25",
  //"org.slf4j" % "slf4j-log4j12" % "1.7.25",
  "io.dropwizard.metrics" % "metrics-core" % "3.1.0",
  "org.apache.flink" % "flink-scala_2.11" % FlinkVersion, // different scala version not sure if this is a problem
  "org.apache.flink" % "flink-streaming-scala_2.11" % FlinkVersion,
  "org.apache.flink" % "flink-metrics-core" % FlinkVersion,
  "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.8.0",
  "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
  "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % JacksonScalaVersion,
  "org.apache.httpcomponents" % "httpclient" % HttpClientVersion,
  "org.specs2" %% "specs2-core" % Specs2Version % "test",
  "org.specs2" %% "specs2-mock" % Specs2Version % "test",
  "com.typesafe.akka" %% "akka-http-testkit" % AkkaHttpVersion % "test"
)

// Niklas: So I have conflicts because both flink and this codebase use Akka, but
// different versions of Akka.
assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs@_*) => MergeStrategy.discard
  case PathList("akka", xs@_*) => MergeStrategy.first
  case PathList("reference.conf", xs@_*) => MergeStrategy.first
  case PathList("rootdoc.txt", xs@_*) => MergeStrategy.first
  // Default strategy
  case x => MergeStrategy.deduplicate
}
