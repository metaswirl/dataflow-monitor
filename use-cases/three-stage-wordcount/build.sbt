ThisBuild / resolvers ++= Seq(
    "Apache Development Snapshot Repository" at "https://repository.apache.org/content/repositories/snapshots/",
    //"Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
    Resolver.mavenLocal
)

mainClass in (Compile, run) := Some("berlin.bbdc.inet.jobs.ThreeStageWordCount.Job")
assembly / mainClass := Some("berlin.bbdc.inet.jobs.ThreeStageWordCount.Job")


name := "ThreeStageWordCount"

version := "0.1"

organization := "berlin.bbdc.inet.jobs"
val AkkaVersion = "2.5.11"
val flinkVersion = "1.4.2"

ThisBuild / scalaVersion := "2.11.12"

lazy val loadshedder = ProjectRef(file("../../flink-loadshedder"), "flink-loadshedder")

lazy val root = (project in file(".")).
  settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
      "org.apache.flink" %% "flink-scala" % flinkVersion % "provided",
      "org.apache.flink" %% "flink-streaming-scala" % flinkVersion % "provided",
    )
  ).dependsOn(loadshedder)



// make run command include the provided dependencies
Compile / run  := Defaults.runTask(Compile / fullClasspath,
                                   Compile / run / mainClass,
                                   Compile / run / runner
                                  ).evaluated

// exclude Scala library from assembly
assembly / assemblyOption  := (assembly / assemblyOption).value.copy(includeScala = false)
