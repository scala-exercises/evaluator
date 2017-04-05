lazy val root = (project in file("."))
  .settings(mainClass in Universal := Some("org.scalaexercises.evaluator.EvaluatorServer"))
  .settings(stage <<= (stage in Universal in `evaluator-server`))
  .settings(noPublishSettings: _*)
  .aggregate(
    `evaluator-server`,
    `evaluator-shared-jvm`,
    `evaluator-shared-js`,
    `evaluator-client-jvm`,
    `evaluator-client-js`)

lazy val `evaluator-shared` = (crossProject in file("shared"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(name := "evaluator-shared")

lazy val `evaluator-shared-jvm` = `evaluator-shared`.jvm
lazy val `evaluator-shared-js`  = `evaluator-shared`.js

lazy val `evaluator-client` = (crossProject in file("client"))
  .dependsOn(`evaluator-shared`)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    name := "evaluator-client",
    libraryDependencies ++= Seq(
      %%("roshttp"),
      %%("cats-free"),
      %%("circe-core"),
      %%("circe-generic"),
      %%("circe-parser"),
      %%("log4s"),
      %("slf4j-simple"),
      %%("scalatest") % "test"
    )
  )
  .jsSettings(sharedJsSettings: _*)

lazy val `evaluator-client-jvm` = `evaluator-client`.jvm
lazy val `evaluator-client-js`  = `evaluator-client`.js

lazy val `evaluator-server` = (project in file("server"))
  .dependsOn(`evaluator-shared-jvm`)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(AutomateHeaderPlugin)
  .enablePlugins(sbtdocker.DockerPlugin)
  .settings(noPublishSettings: _*)
  .settings(
    name := "evaluator-server",
    libraryDependencies ++= Seq(
      %%("monix"),
      %%("circe-core"),
      %%("circe-generic"),
      %%("circe-parser"),
      %%("log4s"),
      %("slf4j-simple"),
      %%("http4s-dsl", http4sV),
      %%("http4s-blaze-server", http4sV),
      %%("http4s-blaze-client", http4sV),
      %%("http4s-circe", http4sV),
      %("config"),
      %%("jwt-core"),
      "io.get-coursier" %% "coursier" % "1.0.0-M15-3",
      "io.get-coursier" %% "coursier-cache" % "1.0.0-M15-3",
      %%("scalatest")   % "test"
    ),
    assemblyJarName in assembly := "evaluator-server.jar"
  )
  .settings(dockerSettings)
  .settings(scalaMacroDependencies: _*)

lazy val `smoketests` = (project in file("smoketests"))
  .dependsOn(`evaluator-server`)
  .settings(noPublishSettings: _*)
  .settings(
    name := "evaluator-server-smoke-tests",
    libraryDependencies ++= Seq(
      %%("circe-core"),
      %%("circe-generic"),
      %%("circe-parser"),
      %%("http4s-blaze-client", http4sV),
      %%("http4s-circe", http4sV),
      %%("jwt-core"),
      %%("scalatest") % "test"
    )
  )

onLoad in Global := (Command
  .process("project evaluator-server", _: State)) compose (onLoad in Global).value
addCommandAlias(
  "publishSignedAll",
  ";evaluator-sharedJS/publishSigned;evaluator-sharedJVM/publishSigned;evaluator-clientJS/publishSigned;evaluator-clientJVM/publishSigned"
)

pgpPassphrase := Some(getEnvVar("PGP_PASSPHRASE").getOrElse("").toCharArray)
pgpPublicRing := file(s"$gpgFolder/pubring.gpg")
pgpSecretRing := file(s"$gpgFolder/secring.gpg")
