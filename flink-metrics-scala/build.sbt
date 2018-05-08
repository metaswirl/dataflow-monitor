
resolvers in ThisBuild ++= Seq(
  "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
  "Apache Development Snapshot Repository" at "https://repository.apache.org/content/repositories/snapshots/",
  Resolver.mavenLocal, Resolver.bintrayRepo("commercetools", "maven"))

name := "mera"

version := "0.2"

organization := "berlin.bbdc.inet"

val AkkaVersion = "2.5.11"
val AkkaHttpVersion = "10.1.0"
val HttpClientVersion = "4.5.5"
val JacksonScalaVersion = "2.9.4"
val Specs2Version = "4.0.2"

// -------- https://scalapb.github.io/index.html ---- BEGIN
PB.targets in Compile := Seq(
  scalapb.gen() -> (sourceManaged in Compile).value
)

scalaVersion := "2.11.12"

libraryDependencies ++= Seq(
  "io.dropwizard.metrics" % "metrics-core" % "3.1.0",
  "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.8.0",
  "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
  "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
  "com.typesafe.akka" %% "akka-remote" % AkkaVersion,
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % JacksonScalaVersion,
  "org.apache.httpcomponents" % "httpclient" % HttpClientVersion,
  "org.specs2" %% "specs2-core" % Specs2Version % "test",
  "org.specs2" %% "specs2-mock" % Specs2Version % "test",
  "com.typesafe.akka" %% "akka-http-testkit" % AkkaHttpVersion % "test",
  "com.trueaccord.scalapb" %% "scalapb-runtime" % com.trueaccord.scalapb.compiler.Version.scalapbVersion % "protobuf"
)
