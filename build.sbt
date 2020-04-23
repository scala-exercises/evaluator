addCommandAlias("ci-test", "scalafmtCheck; scalafmtSbtCheck; test")
addCommandAlias("ci-docs", "project-docs/mdoc")

Universal / javaOptions += "-Dscala.classpath.closeZip=true"

lazy val `evaluator-server` = (project in file("server"))
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(AutomateHeaderPlugin)
  .enablePlugins(sbtdocker.DockerPlugin)
  .enablePlugins(BuildInfoPlugin)
  .settings(noPublishSettings: _*)
  .settings(
    name := "evaluator-server",
    serverHttpDependencies,
    assemblyJarName in assembly := "evaluator-server.jar"
  )
  .settings(dockerSettings: _*)
  .settings(buildInfoSettings: _*)
  .settings(serverScalaMacroDependencies: _*)

lazy val smoketests = (project in file("smoketests"))
  .dependsOn(`evaluator-server`)
  .enablePlugins(BuildInfoPlugin)
  .settings(noPublishSettings: _*)
  .settings(
    name := "evaluator-server-smoke-tests",
    smoketestDependencies
  )
  .settings(buildInfoSettings: _*)

lazy val root = (project in file("."))
  .settings(mainClass in Universal := Some("org.scalaexercises.evaluator.EvaluatorServer"))
  .settings(stage := (stage in Universal in `evaluator-server`).value)
  .settings(noPublishSettings: _*)
  .aggregate(`evaluator-server`)
  .dependsOn(`evaluator-server`)

lazy val `project-docs` = (project in file(".docs"))
  .aggregate(`evaluator-server`, smoketests)
  .dependsOn(`evaluator-server`, smoketests)
  .settings(moduleName := "evaluator-project-docs")
  .settings(mdocIn := file(".docs"))
  .settings(mdocOut := file("."))
  .settings(skip in publish := true)
  .enablePlugins(MdocPlugin)
