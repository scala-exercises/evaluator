import org.scalafmt.sbt.ScalaFmtPlugin
import org.scalafmt.sbt.ScalaFmtPlugin.autoImport._
import de.heikoseeberger.sbtheader.{HeaderPattern, HeaderPlugin}
import de.heikoseeberger.sbtheader.HeaderPlugin.autoImport._
import com.typesafe.sbt.SbtPgp.autoImport._
import sbt.Keys._
import sbt._

object EvaluatorBuild extends AutoPlugin {

  override def requires = plugins.JvmPlugin && ScalaFmtPlugin && HeaderPlugin

  override def trigger = allRequirements

  object autoImport {

    val v = Map(
      'cats -> "0.7.2",
      'circe -> "0.5.2",
      'config -> "1.3.0",
      'coursier -> "1.0.0-M12",
      'http4s -> "0.14.8",
      'jwtcore -> "0.8.0",
      'log4s -> "1.3.0",
      'monix -> "2.0.3",
      'roshttp -> "1.1.0",
      'scalacheck -> "1.12.5",
      'scalaTest -> "2.2.6",
      'slf4j -> "1.7.21"
    )


    def compilerDependencySettings = Seq(
      libraryDependencies ++= Seq(
        "org.scala-lang" % "scala-compiler" % scalaVersion.value,
        compilerPlugin(
          "org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full
        )
      )
    )
  }

  import autoImport._

  override def projectSettings =
    baseSettings ++
      reformatOnCompileSettings ++
      publishSettings ++
      miscSettings


  private[this] def baseSettings = Seq(
    version := "0.1.0-SNAPSHOT",
    organization := "org.scala-exercises",
    scalaVersion := "2.11.8",
    scalafmtConfig in ThisBuild := Some(file(".scalafmt")),

    resolvers ++= Seq(Resolver.mavenLocal, Resolver.sonatypeRepo("snapshots"), Resolver.sonatypeRepo("releases")),

    parallelExecution in Test := false,
    cancelable in Global := true,

    scalacOptions ++= Seq(
      "-deprecation", "-feature", "-unchecked", "-encoding", "utf8"),
    scalacOptions ++= Seq(
      "-language:implicitConversions",
      "-language:higherKinds"),
    javacOptions ++= Seq("-encoding", "UTF-8", "-Xlint:-options")
  )

  private[this] def miscSettings = Seq(
    headers <<= (name, version) { (name, version) => Map(
      "scala" -> (
        HeaderPattern.cStyleBlockComment,
        s"""|/*
            | * scala-exercises-$name
            | * Copyright (C) 2015-2016 47 Degrees, LLC. <http://www.47deg.com>
            | */
            |
          |""".stripMargin)
    )},
    shellPrompt := { s: State =>
      val c = scala.Console
      val blue = c.RESET + c.BLUE + c.BOLD
      val white = c.RESET + c.BOLD

      val projectName = Project.extract(s).currentProject.id

      s"$blue$projectName$white>${c.RESET}"
    }
  )

  private[this] lazy val gpgFolder = sys.env.getOrElse("SE_GPG_FOLDER", ".")

  private[this] lazy val publishSettings = Seq(
    organizationName := "Scala Exercises",
    organizationHomepage := Some(new URL("http://scala-exercises.org")),
    startYear := Some(2016),
    description := "Scala Exercises: The path to enlightenment",
    homepage := Some(url("http://scala-exercises.org")),
    pgpPassphrase := Some(sys.env.getOrElse("SE_GPG_PASSPHRASE", "").toCharArray),
    pgpPublicRing := file(s"$gpgFolder/pubring.gpg"),
    pgpSecretRing := file(s"$gpgFolder/secring.gpg"),
    credentials += Credentials(
      "Sonatype Nexus Repository Manager",
      "oss.sonatype.org",
      sys.env.getOrElse("PUBLISH_USERNAME", ""),
      sys.env.getOrElse("PUBLISH_PASSWORD", "")),
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/scala-exercises/evaluator"),
        "https://github.com/scala-exercises/evaluator.git"
      )
    ),
    licenses := Seq("Apache License, Version 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
    publishMavenStyle := true,
    publishArtifact in Test := false,
    pomIncludeRepository := Function.const(false),
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (isSnapshot.value)
        Some("Snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("Releases" at nexus + "service/local/staging/deploy/maven2")
    }
  )
}
