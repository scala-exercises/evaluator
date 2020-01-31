/*
 *
 *  scala-exercises - evaluator-server
 *  Copyright (C) 2015-2019 47 Degrees, LLC. <http://www.47deg.com>
 *
 */

package org.scalaexercises.evaluator

object helper {

  val remotes: List[String]    = "https://oss.sonatype.org/content/repositories/releases/" :: Nil
  val exercisesVersion: String = "0.5.0-SNAPSHOT"

  sealed abstract class ScalaVersion(val version: String)

  case object Scala211 extends ScalaVersion("2.11.12")

  case object Scala212 extends ScalaVersion("2.12.10")

  case object Scala213 extends ScalaVersion("2.13.1")

  def toScalaVersion(v: String): ScalaVersion = v match {
    case version if version.startsWith("2.11") => Scala211
    case version if version.startsWith("2.12") => Scala212
    case version if version.startsWith("2.13") => Scala213
    case _                                     => throw new IllegalArgumentException(s"Unknown Scala Version $v")
  }

  val commonResolvers = List(
    "https://oss.sonatype.org/content/repositories/snapshots",
    "https://oss.sonatype.org/content/repositories/public",
    "http://repo1.maven.org/maven2"
  )

  def scalaDependencies(scala: ScalaVersion): List[Dependency] = List(
    Dependency("org.scala-lang", s"scala-library", s"${scala.version}"),
    Dependency("org.scala-lang", s"scala-reflect", s"${scala.version}"),
    Dependency("org.scala-lang", s"scala-compiler", s"${scala.version}"),
    Dependency("org.scala-lang.modules", s"scala-xml_${scala.version.substring(0, 4)}", "1.2.0")
  )

  def circeLibraryDependencies(scala: ScalaVersion): List[Dependency] = {
    val sv = scala.version

    val circeVersion = "0.12.3"
    List(
      Dependency("io.circe", s"circe-core_${sv.substring(0, 4)}", circeVersion),
      Dependency("io.circe", s"circe-generic_${sv.substring(0, 4)}", circeVersion),
      Dependency("io.circe", s"circe-parser_${sv.substring(0, 4)}", circeVersion)
    ) ++ scalaDependencies(scala)
  }

  //TODO: Update test code for newer version of Fetch
  def exerciseContentCode(assertCheck: Boolean) =
    s"""
import org.scalaexercises.content._
import stdlib.Asserts
import stdlib.Asserts._
import stdlib._

Asserts.scalaTestAsserts($assertCheck)
    """

  val json = """"{
               |    "id": "c730433b-082c-4984-9d66-855c243266f0",
               |    "name": "Foo",
               |    "counts": [1, 2, 3],
               |    "values": {
               |      "bar": true,
               |      "baz": 100.001,
               |      "qux": ["a", "b"]
               |    }
               |  }"""".stripMargin

  val circeCode =
    s"""
  import cats.syntax.either._
  import io.circe._
  import io.circe.parser._

  val json: String = ""$json""

  val doc: Json    = parse(json).getOrElse(Json.Null)

  val cursor: HCursor = doc.hcursor

  cursor.downField("values").downField("baz").as[Double]
    """
}
