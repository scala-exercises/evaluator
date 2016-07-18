val http4sVersion = "0.14.1"

val circeVersion = "0.4.1"

lazy val evaluator = (project in file("."))
  .settings(
    name := "evaluator",
    scalaVersion := "2.11.8",
    resolvers += Resolver.sonatypeRepo("snapshots"),
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-compiler" % scalaVersion.value,
      "io.monix" %% "monix" % "2.0-RC8",
      "org.http4s" %% "http4s-dsl" % http4sVersion,
      "org.http4s" %% "http4s-blaze-server" % http4sVersion,
      "org.http4s" %% "http4s-blaze-client" % http4sVersion,
      "org.http4s" %% "http4s-circe" % http4sVersion,
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,
      "com.typesafe" % "config" % "1.3.0",
      "com.pauldijou" %% "jwt-core" % "0.8.0",
      "org.log4s" %% "log4s" % "1.3.0",
      "org.slf4j" % "slf4j-simple" % "1.7.21",
      "io.get-coursier" %% "coursier" % "1.0.0-M12",
      "io.get-coursier" %% "coursier-cache" % "1.0.0-M12",
      "org.scalatest" %% "scalatest" % "2.2.4" % "test"
    )
  )

enablePlugins(JavaAppPackaging)

addCompilerPlugin(
  "org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full
)
