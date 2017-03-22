import de.heikoseeberger.sbtheader.{HeaderPattern, HeaderPlugin}
import de.heikoseeberger.sbtheader.HeaderPlugin.autoImport._
import sbt.Keys._
import sbt._
import sbtorgpolicies._
import sbtorgpolicies.OrgPoliciesPlugin.autoImport._
import sbtorgpolicies.model._

object EvaluatorBuild extends AutoPlugin {

  override def trigger: PluginTrigger = allRequirements

  override def requires: Plugins = plugins.JvmPlugin && HeaderPlugin && OrgPoliciesPlugin

  object autoImport {
    lazy val http4sV = "0.15.7a"
  }

  override def projectSettings =
    Seq(
      name := "evaluator",
      description := "Scala Exercises: The path to enlightenment",
      startYear := Option(2016),
      resolvers ++= Seq(
        Resolver.mavenLocal,
        Resolver.sonatypeRepo("snapshots"),
        Resolver.sonatypeRepo("releases")),
      orgGithubSettings := GitHubSettings(
        organization = "scala-exercises",
        project = name.value,
        organizationName = "Scala Exercises",
        groupId = "org.scala-exercises",
        organizationHomePage = url("https://www.scala-exercises.org"),
        organizationEmail = "hello@47deg.com",
        license = ApacheLicense
      ),
      scalaOrganization := "org.scala-lang",
      javacOptions ++= Seq("-encoding", "UTF-8", "-Xlint:-options"),
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
