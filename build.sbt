lazy val `evaluator-shared` = (project in file("shared"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(name := "evaluator-shared")

lazy val `evaluator-server` = (project in file("server"))
  .dependsOn(`evaluator-shared`)
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

lazy val `smoketests` = (project in file("smoketests"))
  .dependsOn(`evaluator-server` % "compile->compile;test->test")
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
  .aggregate(`evaluator-server`, `evaluator-shared`)

addCommandAlias(
  "publishSignedAll",
  ";evaluator-sharedJS/publishSigned;evaluator-sharedJVM/publishSigned;evaluator-clientJS/publishSigned;evaluator-clientJVM/publishSigned"
)

pgpPassphrase := Some(getEnvVar("PGP_PASSPHRASE").getOrElse("").toCharArray)
pgpPublicRing := file(s"$gpgFolder/pubring.gpg")
pgpSecretRing := file(s"$gpgFolder/secring.gpg")
