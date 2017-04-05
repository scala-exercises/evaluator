import de.heikoseeberger.sbtheader.{HeaderPattern, HeaderPlugin}
import de.heikoseeberger.sbtheader.HeaderPlugin.autoImport._
import sbt.Keys._
import sbt.{Def, _}
import sbtassembly.AssemblyPlugin.autoImport.assembly
import sbtbuildinfo.BuildInfoKey
import sbtbuildinfo.BuildInfoKeys.{buildInfoKeys, buildInfoPackage}
import sbtdocker.DockerPlugin.autoImport._
import sbtorgpolicies._
import sbtorgpolicies.model._
import sbtorgpolicies.OrgPoliciesPlugin.autoImport._

object ProjectPlugin extends AutoPlugin {

  override def trigger: PluginTrigger = allRequirements

  override def requires: Plugins = plugins.JvmPlugin && HeaderPlugin && OrgPoliciesPlugin

  object autoImport {
    lazy val http4sV = "0.15.7a"

    lazy val dockerSettings = Seq(
      docker <<= docker dependsOn assembly,
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
      scalaVersion := "2.11.8",
      scalaOrganization := "org.scala-lang",
      javacOptions ++= Seq("-encoding", "UTF-8", "-Xlint:-options"),
      fork in Test := false,
      parallelExecution in Test := false,
      cancelable in Global := true,
      headers := Map(
        "scala" -> (HeaderPattern.cStyleBlockComment,
        s"""|/*
            | * scala-exercises - ${name.value}
            | * Copyright (C) 2015-2016 47 Degrees, LLC. <http://www.47deg.com>
            | */
            |
            |""".stripMargin)
      )
    ) ++ shellPromptSettings
}