
import sbt._
import Keys._

object Dependencies {
  val flinkVersion = "1.4.2"
  val specs2Version = "4.0.2"
  val jacksonScalaVersion = "2.9.4"
  val akkaHttpVersion = "10.1.0"
  val akkaVersion = "2.4.20"
  val httpClientVersion = "4.5.5"

  val jackson = "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonScalaVersion
  val flinkScala = "org.apache.flink" %% "flink-scala" % flinkVersion 
  val flinkStreamScala = "org.apache.flink" %% "flink-streaming-scala" % flinkVersion
  val flinkMetricsCore = "org.apache.flink" % "flink-metrics-core" % flinkVersion % "provided"
  val typesafeConfig = "com.typesafe" % "config" % "1.3.3"
  val specs2Core = "org.specs2" %% "specs2-core" % specs2Version % "test"
  val specs2Mock = "org.specs2" %% "specs2-mock" % specs2Version % "test"
  val dropwizardMetrics = "io.dropwizard.metrics" % "metrics-core" % "3.1.0"
  val qosLogback = "ch.qos.logback" % "logback-classic" % "1.2.3"
  val typesafeLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.8.0"
  val typesafeAkkaActor = "com.typesafe.akka" %% "akka-actor" % akkaVersion
  val typesafeAkkaHttp = "com.typesafe.akka" %% "akka-http" % akkaHttpVersion
  val typesafeAkkaHttpTestkit = "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % "test"
  val typesafeAkkaStream = "com.typesafe.akka" %% "akka-stream" % akkaVersion
  val typesafeAkkaremote = "com.typesafe.akka" %% "akka-remote" % akkaVersion
  val apacheHttpComponents = "org.apache.httpcomponents" % "httpclient" % httpClientVersion

  val commonDependencies: Seq[ModuleID] = Seq(
    flinkScala, flinkStreamScala, specs2Core, specs2Mock
  )
  val meraCommonsDependencies: Seq[ModuleID] = Seq(
    specs2Core, jackson
  )
  val useCaseDependencies: Seq[ModuleID] = Seq(
  ) ++ commonDependencies
  val templateDependencies: Seq[ModuleID] = Seq(
    typesafeConfig
  ) ++ commonDependencies
  val loadshedderDependencies: Seq[ModuleID] = Seq(
    typesafeAkkaActor
  ) ++ commonDependencies
  val monitorDependencies: Seq[ModuleID] = Seq(
    typesafeLogging, typesafeAkkaHttp, typesafeAkkaHttpTestkit,
    typesafeAkkaStream, typesafeAkkaremote, typesafeAkkaActor,
    qosLogback, dropwizardMetrics, apacheHttpComponents, jackson,
    specs2Core, specs2Mock
  )
  val reporterDependencies: Seq[ModuleID] = Seq(
    flinkScala, flinkStreamScala, flinkMetricsCore, typesafeAkkaActor, qosLogback
  )
}
