lazy val root = (project in file("."))
  .aggregate(`evaluator-server`, `evaluator-shared`, `evaluator-client`)

lazy val `evaluator-shared` = (project in file("shared"))
  .settings(name := "evaluator-shared")

lazy val `evaluator-client` = (project in file("client"))
  .settings(name := "evaluator-client")
  .dependsOn(`evaluator-shared`)

lazy val `evaluator-server` = (project in file("server"))
  .enablePlugins(JavaAppPackaging)
  .settings(
    name := "evaluator-server",
    libraryDependencies <++= libraryVersions { v => Seq(
      "org.scala-exercises" %% "evaluator-types" % "0.1-SNAPSHOT",
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
  .dependsOn(`evaluator-shared`)

onLoad in Global := (Command.process("project evaluator-server", _: State)) compose (onLoad in Global).value