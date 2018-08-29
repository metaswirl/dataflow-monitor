import Dependencies._
import Commons._

lazy val meraCommons = (project in file("mera-commons")).
  settings(commonSettings: _*).
  settings(libraryDependencies ++= meraCommonsDependencies).
  disablePlugins(AssemblyPlugin)

lazy val loadshedder = (project in file("mera-loadshedder")).
  settings(commonSettings: _*).
  settings(libraryDependencies ++= loadshedderDependencies).
  dependsOn(meraCommons).
  disablePlugins(AssemblyPlugin)

lazy val template = (project in file("mera-use-cases/template")).
  settings(commonSettings: _*).
  settings(libraryDependencies ++= templateDependencies).
  dependsOn(meraCommons).
  disablePlugins(AssemblyPlugin)

lazy val threeStageWordcount = (project in file("mera-use-cases/three-stage-wordcount")).
  settings(commonSettings: _*).
  settings(libraryDependencies ++= useCaseDependencies).
  dependsOn(template).dependsOn(loadshedder)

lazy val reporter = (project in file("mera-reporter")).
  settings(commonSettings: _*).
  settings( libraryDependencies ++= reporterDependencies).
  dependsOn(meraCommons)

lazy val monitor = (project in file("mera-monitor")).
  settings(commonSettings: _*).
  settings(libraryDependencies ++= monitorDependencies).
  settings(mainClass in assembly := Some("berlin.bbdc.inet.mera.monitor.Starter")).
  dependsOn(meraCommons)

