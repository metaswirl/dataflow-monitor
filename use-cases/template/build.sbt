ThisBuild / resolvers ++= Seq(
  "Apache Development Snapshot Repository" at "https://repository.apache.org/content/repositories/snapshots/",
  //"Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
  Resolver.mavenLocal
)

name := "template"
version := "0.1"
organization := "berlin.bbdc.inet.jobs"

scalaVersion := "2.11.12"
val Specs2Version = "4.0.2"
val flinkVersion = "1.4.2"
val AkkaVersion = "2.4.20"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
  "org.apache.flink" %% "flink-scala" % flinkVersion ,
  "org.apache.flink" %% "flink-streaming-scala" % flinkVersion,
  "com.typesafe" % "config" % "1.3.3",
  "org.specs2" %% "specs2-core" % Specs2Version % "test",
  "org.specs2" %% "specs2-mock" % Specs2Version % "test"
)
