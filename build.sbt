lazy val noPublishSettings = Seq(
  publish := (),
  publishLocal := (),
  publishArtifact := false
)

lazy val root = (project in file("."))
  .settings(mainClass in Universal := Some("org.scalaexercises.evaluator.EvaluatorServer"))
  .settings(stage <<= (stage in Universal in `evaluator-server`))
  .settings(noPublishSettings: _*)
  .aggregate(`evaluator-server`, `evaluator-shared-jvm`, `evaluator-shared-js`, `evaluator-client-jvm`, `evaluator-client-js`)

lazy val `evaluator-shared` = (crossProject in file("shared"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(name := "evaluator-shared")

lazy val `evaluator-shared-jvm` = `evaluator-shared`.jvm
lazy val `evaluator-shared-js` = `evaluator-shared`.js

lazy val scalaJSSettings = Seq(
  requiresDOM := false,
  scalaJSUseRhino := false,
  jsEnv := NodeJSEnv().value
)

lazy val `evaluator-client` = (crossProject in file("client"))
  .dependsOn(`evaluator-shared`)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    name := "evaluator-client",
    libraryDependencies <++= libraryVersions { v => Seq(
      "org.typelevel" %% "cats-free" % v('cats),
      "io.circe" %% "circe-core" % v('circe),
      "io.circe" %% "circe-generic" % v('circe),
      "io.circe" %% "circe-parser" % v('circe),
      "org.log4s" %% "log4s" % v('log4s),
      "org.slf4j" % "slf4j-simple" % v('slf4j),
      // Testing libraries
      "org.scalatest" %% "scalatest" % v('scalaTest) % "test"
    )
    },
    libraryDependencies += "fr.hmil" %%% "roshttp" % "1.1.0"
)
  .jsSettings(scalaJSSettings: _*)

lazy val `evaluator-client-jvm` = `evaluator-client`.jvm
lazy val `evaluator-client-js` = `evaluator-client`.js

lazy val `evaluator-server` = (project in file("server"))
  .dependsOn(`evaluator-shared-jvm`)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(noPublishSettings: _*)
  .settings(
    name := "evaluator-server",
    libraryDependencies <++= libraryVersions { v => Seq(
      "io.monix" %% "monix" % v('monix),
      "org.http4s" %% "http4s-dsl" % v('http4s),
      "org.http4s" %% "http4s-blaze-server" % v('http4s),
      "org.http4s" %% "http4s-blaze-client" % v('http4s),
      "org.http4s" %% "http4s-circe" % v('http4s),
      "io.circe" %% "circe-core" % v('circe),
      "io.circe" %% "circe-generic" % v('circe),
      "io.circe" %% "circe-parser" % v('circe),
      "com.typesafe" % "config" % v('config),
      "com.pauldijou" %% "jwt-core" % v('jwtcore),
      "org.log4s" %% "log4s" % v('log4s),
      "org.slf4j" % "slf4j-simple" % v('slf4j),
      "io.get-coursier" %% "coursier" % v('coursier),
      "io.get-coursier" %% "coursier-cache" % v('coursier),
      "org.scalatest" %% "scalatest" % v('scalaTest) % "test"
    )
    }
  )
  .settings(compilerDependencySettings: _*)

onLoad in Global := (Command.process("project evaluator-server", _: State)) compose (onLoad in Global).value
addCommandAlias("publishSignedAll", ";evaluator-sharedJS/publishSigned;evaluator-sharedJVM/publishSigned;evaluator-clientJS/publishSigned;evaluator-clientJVM/publishSigned")
