lazy val noPublishSettings = Seq(
  publish := (),
  publishLocal := (),
  publishArtifact := false
)

lazy val root = (project in file("."))
  .settings(noPublishSettings: _*)
  .aggregate(`evaluator-server`, `evaluator-compiler`, `evaluator-shared-jvm`, `evaluator-shared-js`, `evaluator-client-jvm`, `evaluator-client-js`)

lazy val `evaluator-shared` = (crossProject in file("shared"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(name := "evaluator-shared")

lazy val `evaluator-shared-jvm` = `evaluator-shared`.jvm
lazy val `evaluator-shared-js` = `evaluator-shared`.js

lazy val scalaJSSettings = Seq(
  requiresDOM := false,
  scalaJSUseRhino := false,
  jsEnv := NodeJSEnv().value,
  libraryDependencies ++= Seq(
    "fr.hmil" %%% "roshttp" % v('roshttp),
    "org.typelevel" %%% "cats-free" % v('cats),
    "io.circe" %%% "circe-core" % v('circe),
    "io.circe" %%% "circe-generic" % v('circe),
    "io.circe" %%% "circe-parser" % v('circe)
  )
)

lazy val `evaluator-client` = (crossProject in file("client"))
  .dependsOn(`evaluator-shared`)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    name := "evaluator-client",
    libraryDependencies ++= Seq(
      "fr.hmil" %% "roshttp" % v('roshttp),
      "org.typelevel" %% "cats-free" % v('cats),
      "io.circe" %% "circe-core" % v('circe),
      "io.circe" %% "circe-generic" % v('circe),
      "io.circe" %% "circe-parser" % v('circe),
      "org.log4s" %% "log4s" % v('log4s),
      "org.slf4j" % "slf4j-simple" % v('slf4j),
      // Testing libraries
      "org.scalatest" %% "scalatest" % v('scalaTest) % "test"
    )
)
  .jsSettings(scalaJSSettings: _*)

lazy val `evaluator-client-jvm` = `evaluator-client`.jvm
lazy val `evaluator-client-js` = `evaluator-client`.js

lazy val `evaluator-server` = (project in file("server"))
  .dependsOn(`evaluator-shared-jvm`, `evaluator-compiler` % "test->test;compile->compile")
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(AutomateHeaderPlugin)
  .enablePlugins(sbtdocker.DockerPlugin)
  .settings(noPublishSettings: _*)
  .settings(
    name := "evaluator-server",
    libraryDependencies ++= Seq(
      "io.monix" %% "monix" % v('monix),
      "org.http4s" %% "http4s-dsl" % v('http4s),
      "org.http4s" %% "http4s-blaze-server" % v('http4s),
      "org.http4s" %% "http4s-blaze-client" % v('http4s),
      "org.http4s" %% "http4s-circe" % v('http4s),
      "io.circe" %% "circe-core" % v('circe),
      "io.circe" %% "circe-generic" % v('circe),
      "io.circe" %% "circe-parser" % v('circe),
      "com.typesafe" % "config" % v('config),
      "com.pauldijou" %% "jwt-core" % v({
        scalaVersion.value match {
          case sVersion if sVersion startsWith "2.11" => 'jwtcore_211
          case _ => 'jwtcore
        }
      }),
      "org.log4s" %% "log4s" % v('log4s),
      "org.slf4j" % "slf4j-simple" % v('slf4j),
      "org.scalatest" %% "scalatest" % v('scalaTest) % "test"
    ),
    assemblyJarName in assembly := "evaluator-server.jar"
  )
  .settings(dockerSettings)

lazy val `evaluator-compiler` = (project in file("compiler"))
  .dependsOn(`evaluator-shared-jvm`)
  .enablePlugins(AutomateHeaderPlugin)
  .enablePlugins(BuildInfoPlugin)
  .settings(
    name := "evaluator-compiler"
  )
  .settings(compilerDependencySettings: _*)

lazy val `smoketests` = (project in file("smoketests"))
  .dependsOn(`evaluator-server`)
  .settings(
    name := "evaluator-server-smoke-tests",
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % v('scalaTest) % "test",
      "org.http4s" %% "http4s-blaze-client" % v('http4s),
      "org.http4s" %% "http4s-circe" % v('http4s),
      "io.circe" %% "circe-core" % v('circe),
      "io.circe" %% "circe-generic" % v('circe),
      "io.circe" %% "circe-parser" % v('circe),
      "com.pauldijou" %% "jwt-core" % v('jwtcore)
    )

  )

addCommandAlias("publishSignedAll", ";evaluator-sharedJS/publishSigned;evaluator-sharedJVM/publishSigned;evaluator-clientJS/publishSigned;evaluator-clientJVM/publishSigned;evaluator-compiler/+publishSigned")

lazy val dockerSettings = Seq(
  docker <<= docker dependsOn assembly,
  dockerfile in docker := {

    val artifact: File = assembly.value
    val artifactTargetPath = artifact.name

    sbtdocker.immutable.Dockerfile.empty
      .from("ubuntu:latest")
      .run("apt-get", "update")
      .run("apt-get", "install", "-y", "openjdk-8-jdk")
      .run("useradd", "-m", "evaluator")
      .user("evaluator")
      .add(artifact, artifactTargetPath)
      .cmdRaw(s"java -Dhttp.port=$$PORT -Deval.auth.secretKey=$$EVAL_SECRET_KEY -jar $artifactTargetPath")
  },
  imageNames in docker := Seq(ImageName(repository = "registry.heroku.com/scala-evaluator/web"))
)
