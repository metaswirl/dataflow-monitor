import sbt._
import Keys._

object Dependencies {
  val flinkVersion = "1.4.2"
  val specs2Version = "4.0.2"
  val jacksonScalaVersion = "2.9.4"
  val akkaHttpVersion = "10.1.0"
  val akkaVersion = "2.5.11"
  val akkaVersionOld = "2.4.20"
  val httpClientVersion = "4.5.5"

  val jackson = "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonScalaVersion
  val flinkScala = "org.apache.flink" %% "flink-scala" % flinkVersion 
  val flinkStreamScala = "org.apache.flink" %% "flink-streaming-scala" % flinkVersion
  val flinkMetricsCore = "org.apache.flink" % "flink-metrics-core" % flinkVersion
  val typesafeConfig = "com.typesafe" % "config" % "1.3.3"
  val qosLogback = "ch.qos.logback" % "logback-classic" % "1.2.3"
  val specs2Core = "org.specs2" %% "specs2-core" % specs2Version % "test"
  val specs2Mock = "org.specs2" %% "specs2-mock" % specs2Version % "test"
  val apacheHttpComponentsCore =  "org.apache.httpcomponents" % "httpcomponents-core" % "4.4.10"
  val apacheHttpComponentsClient = "org.apache.httpcomponents" % "httpclient" % httpClientVersion
  val dropwizardMetrics = "io.dropwizard.metrics" % "metrics-core" % "3.1.0"
  val typesafeLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.8.0"
  val typesafeAkkaActor = "com.typesafe.akka" %% "akka-actor" % akkaVersion
  val typesafeAkkaActorOld = "com.typesafe.akka" %% "akka-actor" % akkaVersionOld
  val typesafeAkkaHttp = "com.typesafe.akka" %% "akka-http" % akkaHttpVersion
  val typesafeAkkaHttpTestkit = "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % "test"
  val typesafeAkkaStream = "com.typesafe.akka" %% "akka-stream" % akkaVersion
  val typesafeAkkaRemote = "com.typesafe.akka" %% "akka-remote" % akkaVersion

  val flinkProvidedDependencies: Seq[ModuleID] = Seq(
    flinkScala, flinkStreamScala
  ).map(_ % "provided")
  val testDependencies: Seq[ModuleID] = Seq(
    specs2Core, specs2Mock
  )
  val monitorDependencies: Seq[ModuleID] = Seq(
    typesafeLogging, typesafeAkkaHttp, typesafeAkkaHttpTestkit,
    typesafeAkkaStream, typesafeAkkaRemote, typesafeAkkaActor,
    qosLogback, dropwizardMetrics, apacheHttpComponentsClient, jackson,
  ) ++ testDependencies

  // WARNING: Don't risk overlap between Flink's dependencies and your own
  val meraCommonsDependencies: Seq[ModuleID] = Seq(
    jackson
  ) ++ testDependencies
  val useCaseDependencies: Seq[ModuleID] = Seq(
  ) ++ flinkProvidedDependencies ++ testDependencies
  val templateDependencies: Seq[ModuleID] = Seq(
    apacheHttpComponentsCore
  ) ++ flinkProvidedDependencies ++ testDependencies
  val loadshedderDependencies: Seq[ModuleID] = Seq(
    typesafeAkkaActorOld % "provided"
  ) ++ flinkProvidedDependencies ++ testDependencies
  val reporterDependencies: Seq[ModuleID] = Seq(
    typesafeAkkaActorOld, qosLogback, flinkMetricsCore
  ).map(_ % "provided") ++ flinkProvidedDependencies ++ testDependencies
}
