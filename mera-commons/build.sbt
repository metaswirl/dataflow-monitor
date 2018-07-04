
name := "mera-commons"

organization := "berlin.bbdc.inet"
scalaVersion := "2.11.12"
version      := "0.1.0"

val JacksonScalaVersion = "2.9.4"
val Specs2Version = "4.0.2"

lazy val root = Project("mera-commons", file("."))

libraryDependencies ++= Seq(
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % JacksonScalaVersion,
  "org.specs2" %% "specs2-core" % Specs2Version % "test"
)
