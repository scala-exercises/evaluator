ThisBuild / organization       := "org.scala-exercises"
ThisBuild / githubOrganization := "47degrees"
ThisBuild / scalaVersion       := V.scala

Universal / javaOptions += "-Dscala.classpath.closeZip=true"
Universal / mainClass := Some("org.scalaexercises.evaluator.EvaluatorServer")

stage           := (`evaluator-server` / Universal / stage).value
(publish / skip) := true

addCommandAlias("ci-test", "scalafmtCheckAll; scalafmtSbtCheck; test")
addCommandAlias("ci-docs", "github; mdoc; headerCreateAll")
addCommandAlias("ci-publish", "github; ci-release")

lazy val `evaluator-server` = (project in file("server"))
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(sbtdocker.DockerPlugin)
  .enablePlugins(BuildInfoPlugin)
  .settings((publish / skip) := true)
  .settings(
    name := "evaluator-server",
    serverHttpDependencies,
    (assembly / assemblyJarName) := "evaluator-server.jar"
  )
  .settings(dockerSettings: _*)
  .settings(buildInfoSettings: _*)
  .settings(serverScalaMacroDependencies: _*)

lazy val documentation = project
  .settings(mdocOut := file("."))
  .settings(publish / skip := true)
  .enablePlugins(MdocPlugin)
