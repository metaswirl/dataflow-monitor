val ScalatraVersion = "2.6.2"
val LogBackVersion = "1.2.3"
val JavaxServletVersion = "3.1.0"
val Json4sJacksonVersion = "3.5.2"
val HttpClientVersion = "4.5.5"

organization := "de.tuberlin.inet"

name := "Collector Rest Server"

version := "0.1.0-SNAPSHOT"

scalaVersion := "2.12.4"

resolvers += Classpaths.typesafeReleases

libraryDependencies ++= Seq(
  "org.scalatra" %% "scalatra" % ScalatraVersion,
  "org.scalatra" %% "scalatra-scalatest" % ScalatraVersion % "test",
  "ch.qos.logback" % "logback-classic" % LogBackVersion % "runtime",
  "org.eclipse.jetty" % "jetty-webapp" % "9.4.8.v20171121" % "container",
  "javax.servlet" % "javax.servlet-api" % JavaxServletVersion % "provided",
  "org.scalatra" %% "scalatra-json" % ScalatraVersion,
  "org.json4s"   %% "json4s-jackson" % Json4sJacksonVersion,
  "org.apache.httpcomponents" % "httpclient" % HttpClientVersion,
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.5.3",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.9.4"
)

enablePlugins(SbtTwirl)
enablePlugins(ScalatraPlugin)
