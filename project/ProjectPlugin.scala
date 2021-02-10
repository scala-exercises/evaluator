import sbt.Keys._
import sbt._
import sbtassembly.AssemblyPlugin.autoImport.assembly
import sbtbuildinfo.BuildInfoKey
import sbtbuildinfo.BuildInfoPlugin.autoImport._
import sbtdocker.DockerPlugin.autoImport._
import com.alejandrohdezma.sbt.github.SbtGithubPlugin

object ProjectPlugin extends AutoPlugin {

  override def trigger: PluginTrigger = allRequirements

  override def requires: Plugins = plugins.JvmPlugin && SbtGithubPlugin

  object autoImport {

    object V {
      lazy val cats                = "2.4.0"
      lazy val catsEffect          = "2.4.0"
      lazy val http4s              = "0.21.18"
      lazy val circe               = "0.13.0"
      lazy val log4s               = "1.7.0"
      lazy val scalatest           = "3.2.3"
      lazy val scalatestplusScheck = "3.2.2.0"
      lazy val jodaTime            = "2.10.10"
      lazy val slf4j               = "1.7.30"
      lazy val jwtCore             = "5.0.0"
      lazy val coursier            = "2.0.9"
      lazy val config              = "1.4.1"
      lazy val scala               = "2.13.3"
    }

    lazy val dockerSettings = Seq(
      docker := (docker dependsOn assembly).value,
      dockerfile in docker := {

        val artifact: File     = assembly.value
        val artifactTargetPath = artifact.name

        sbtdocker.immutable.Dockerfile.empty
          .from("ubuntu:latest")
          .run("apt-get", "update")
          .run("apt-get", "install", "-y", "openjdk-8-jdk")
          .run("useradd", "-m", "evaluator")
          .user("evaluator")
          .add(artifact, artifactTargetPath)
          .cmdRaw(
            s"java -Dhttp.port=$$PORT -Deval.auth.secretKey=$$EVAL_SECRET_KEY -jar $artifactTargetPath"
          )
      },
      imageNames in docker := Seq(
        ImageName(repository =
          s"registry.heroku.com/${sys.props.getOrElse("evaluator.heroku.name", "scala-evaluator")}/web"
        )
      )
    )

    lazy val serverScalaMacroDependencies: Seq[Setting[_]] = {
      Seq(
        libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value,
        libraryDependencies += "org.scala-lang" % "scala-reflect"  % scalaVersion.value
      )
    }

    lazy val serverHttpDependencies = Seq(
      libraryDependencies ++= Seq(
        "org.typelevel"     %% "cats-core"             % V.cats,
        "org.typelevel"     %% "cats-effect"           % V.catsEffect,
        "io.circe"          %% "circe-core"            % V.circe,
        "io.circe"          %% "circe-generic"         % V.circe,
        "org.slf4j"          % "slf4j-simple"          % V.slf4j,
        "org.http4s"        %% "http4s-dsl"            % V.http4s,
        "org.http4s"        %% "http4s-blaze-server"   % V.http4s,
        "org.http4s"        %% "http4s-circe"          % V.http4s,
        "io.get-coursier"   %% "coursier"              % V.coursier,
        "io.get-coursier"   %% "coursier-cache"        % V.coursier,
        "com.typesafe"       % "config"                % V.config,
        "com.pauldijou"     %% "jwt-core"              % V.jwtCore,
        "io.get-coursier"   %% "coursier-cats-interop" % V.coursier,
        "org.scalatest"     %% "scalatest"             % V.scalatest,
        "org.scalatestplus" %% "scalacheck-1-14"       % V.scalatestplusScheck,
        "joda-time"          % "joda-time"             % V.jodaTime
      )
    )

    lazy val buildInfoSettings = Seq(
      buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
      buildInfoPackage := "org.scalaexercises.evaluator"
    )

  }

  override def projectSettings: Seq[Def.Setting[_]] =
    Seq(
      scmInfo := Some(
        ScmInfo(
          url("https://github.com/scala-exercises/evaluator"),
          "scm:git:https://github.com/scala-exercises/evaluator.git",
          Some("scm:git:git@github.com:scala-exercises/evaluator.git")
        )
      ),
      scalacOptions ~= (_ filterNot (_ == "-Xfuture")),
      scalacOptions += "-Ymacro-annotations",
      javacOptions ++= Seq("-encoding", "UTF-8", "-Xlint:-options"),
      parallelExecution in Test := false,
      cancelable in Global := true
    )
}
