import de.heikoseeberger.sbtheader.{HeaderPattern, HeaderPlugin}
import de.heikoseeberger.sbtheader.HeaderPlugin.autoImport._
import com.typesafe.sbt.SbtPgp.autoImport._
import sbtbuildinfo.BuildInfoKeys._
import sbt.Keys._
import sbt._
import sbtbuildinfo.BuildInfoKey

object EvaluatorBuild extends AutoPlugin {

  override def requires = plugins.JvmPlugin && HeaderPlugin

  override def trigger = allRequirements

  object autoImport {

    val v = Map(
      'cats -> "0.8.1",
      'circe -> "0.6.1",
      'config -> "1.3.0",
      'coursier -> "1.0.0-M15",
      'http4s -> "0.15.3a",
      'jclcore -> "2.8",
      'jwtcore_211 -> "0.9.2",
      'jwtcore -> "0.10.0",
      'log4s -> "1.3.4",
      'monix -> "2.1.2",
      'roshttp -> "2.0.1",
      'scalacheck -> "1.13.4",
      'scalaTest -> "3.0.1",
      'slf4j -> "1.7.21"
    )


    def compilerDependencySettings = Seq(
      crossScalaVersions := Seq("2.11.8", "2.12.1"),
      libraryDependencies ++= Seq(
        "io.monix" %% "monix" % v('monix),
        "io.get-coursier" %% "coursier" % v('coursier),
        "io.get-coursier" %% "coursier-cache" % v('coursier),
        "org.xeustechnologies" % "jcl-core" % v('jclcore),
        "org.scala-lang" % "scala-compiler" % scalaVersion.value,
        "org.scala-lang" % "scala-reflect" % scalaVersion.value,
        "org.log4s" %% "log4s" % v('log4s),
        "org.slf4j" % "slf4j-simple" % v('slf4j),
        "org.scalatest" %% "scalatest" % v('scalaTest) % "test",
        compilerPlugin(
          "org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full
        )
      ),
      dependencyOverrides += "org.scala-lang" % "scala-compiler" % scalaVersion.value,
      buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
      buildInfoPackage := "org.scalaexercises.evaluator"
    )
  }

  import autoImport._

  override def projectSettings =
    baseSettings ++
      publishSettings ++
      miscSettings


  private[this] def baseSettings = Seq(
    version := "0.1.2-SNAPSHOT",
    organization := "org.scala-exercises",
    scalaVersion := "2.12.1",

    resolvers ++= Seq(
      Resolver.mavenLocal,
      Resolver.sonatypeRepo("snapshots"),
      Resolver.sonatypeRepo("releases"),
      "caliberweb repo" at "https://s3-us-west-2.amazonaws.com/repo.caliberweb.com/release"
    ),

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

      s"$blue$projectName$white>${c.RESET} "
    }
  )

  private[this] lazy val gpgFolder = sys.env.getOrElse("PGP_FOLDER", ".")

  private[this] lazy val publishSettings = Seq(
    organizationName := "Scala Exercises",
    organizationHomepage := Some(new URL("http://scala-exercises.org")),
    startYear := Some(2016),
    description := "Scala Exercises: The path to enlightenment",
    homepage := Some(url("http://scala-exercises.org")),
    pgpPassphrase := Some(sys.env.getOrElse("PGP_PASSPHRASE", "").toCharArray),
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
