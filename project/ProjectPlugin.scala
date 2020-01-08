import de.heikoseeberger.sbtheader.HeaderPlugin
import de.heikoseeberger.sbtheader.HeaderPlugin.autoImport._
import sbt.Keys._
import sbt.{Def, _}
import sbtassembly.AssemblyPlugin.autoImport.assembly
import sbtbuildinfo.BuildInfoKey
import sbtbuildinfo.BuildInfoPlugin.autoImport._
import sbtdocker.DockerPlugin.autoImport._
import sbtorgpolicies.OrgPoliciesPlugin.autoImport._
import sbtorgpolicies._
import sbtorgpolicies.model._

object ProjectPlugin extends AutoPlugin {

  override def trigger: PluginTrigger = allRequirements

  override def requires: Plugins = plugins.JvmPlugin && HeaderPlugin && OrgPoliciesPlugin

  object autoImport {

    object V {
      lazy val http4s      = "0.20.15"
      lazy val circe       = "0.12.3"
      lazy val log4s       = "1.7.0"
      lazy val scalatest   = "3.1.0"
      lazy val slf4jSimple = "1.7.30"
      lazy val jwtCore     = "4.2.0"
      lazy val coursier    = "2.0.0-RC5-6"
      lazy val config      = "1.4.0"
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
            s"java -Dhttp.port=$$PORT -Deval.auth.secretKey=$$EVAL_SECRET_KEY -jar $artifactTargetPath")
      },
      imageNames in docker := Seq(ImageName(repository =
        s"registry.heroku.com/${sys.props.getOrElse("evaluator.heroku.name", "scala-evaluator")}/web"))
    )

    lazy val serverScalaMacroDependencies: Seq[Setting[_]] = {
      Seq(
        libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value,
        libraryDependencies += "org.scala-lang" % "scala-reflect"  % scalaVersion.value,
        libraryDependencies += compilerPlugin(%%("paradise") cross CrossVersion.full),
        libraryDependencies ++= {
          CrossVersion.partialVersion(scalaVersion.value) match {
            // if scala 2.11+ is used, quasiquotes are merged into scala-reflect
            case Some((2, scalaMajor)) if scalaMajor >= 11 => Seq()
            // in Scala 2.10, quasiquotes are provided by macro paradise
            case Some((2, 10)) =>
              Seq(
                %%("quasiquotes") cross CrossVersion.binary
              )
          }
        }
      )
    }

    lazy val serverHttpDependencies = Seq(
      libraryDependencies ++= Seq(
        %%("circe-core", V.circe),
        %%("circe-generic", V.circe),
        %("slf4j-simple", V.slf4jSimple),
        %%("http4s-dsl", V.http4s),
        %%("http4s-blaze-server", V.http4s),
        %%("http4s-circe", V.http4s),
        %("config", V.config),
        %%("jwt-core", V.jwtCore),
        %%("coursier", V.coursier),
        %%("coursier-cache", V.coursier),
        "io.get-coursier" %% "coursier-cats-interop" % V.coursier,
        %%("scalatest", V.scalatest)
      )
    )

    lazy val buildInfoSettings = Seq(
      buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
      buildInfoPackage := "org.scalaexercises.evaluator"
    )

    lazy val smoketestDependencies = Seq(
      libraryDependencies ++= Seq(
        %%("circe-core", V.circe),
        %%("circe-generic", V.circe),
        %%("circe-parser", V.circe),
        %%("http4s-blaze-client", V.http4s),
        %%("http4s-circe", V.http4s),
        %%("jwt-core", V.jwtCore),
        %%("scalatest", V.scalatest) % "test"
      )
    )

  }

  override def projectSettings: Seq[Def.Setting[_]] =
    Seq(
      name := "evaluator",
      description := "Scala Exercises: The path to enlightenment",
      startYear := Option(2016),
      resolvers ++= Seq(
        Resolver.mavenLocal,
        Resolver.sonatypeRepo("snapshots"),
        Resolver.sonatypeRepo("releases")),
      orgGithubSetting := GitHubSettings(
        organization = "scala-exercises",
        project = name.value,
        organizationName = "Scala Exercises",
        groupId = "org.scala-exercises",
        organizationHomePage = url("https://www.scala-exercises.org"),
        organizationEmail = "hello@47deg.com"
      ),
      orgLicenseSetting := ApacheLicense,
      scalaVersion := "2.12.10",
      scalaOrganization := "org.scala-lang",
      javacOptions ++= Seq("-encoding", "UTF-8", "-Xlint:-options"),
      scalacOptions += "-Ypartial-unification",
      fork in Test := false,
      parallelExecution in Test := false,
      cancelable in Global := true,
      headerLicense := Some(
        HeaderLicense.Custom(
          s"""|
              | scala-exercises - ${name.value}
              | Copyright (C) 2015-2019 47 Degrees, LLC. <http://www.47deg.com>
              |
              |""".stripMargin
        ))
    ) ++ shellPromptSettings
}
