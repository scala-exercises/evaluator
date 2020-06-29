/*
 * Copyright 2016-2020 47 Degrees Open Source <https://www.47deg.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scalaexercises.evaluator

object helper {

  val remotes: List[String]    = "https://oss.sonatype.org/content/repositories/releases/" :: Nil
  val exercisesVersion: String = "0.6.1"

  sealed abstract class ScalaVersion(val version: String)

  case object Scala213 extends ScalaVersion("2.13.3")

  def toScalaVersion(v: String): ScalaVersion =
    v match {
      case version if version.startsWith("2.13") => Scala213
      case _                                     => throw new IllegalArgumentException(s"Unknown Scala Version $v")
    }

  val commonResolvers = List(
    "https://oss.sonatype.org/content/repositories/snapshots",
    "https://oss.sonatype.org/content/repositories/public",
    "http://repo1.maven.org/maven2"
  )

  def scalaDependencies(scala: ScalaVersion): List[Dependency] =
    List(
      Dependency("org.scala-lang", s"scala-library", s"${scala.version}"),
      Dependency("org.scala-lang", s"scala-reflect", s"${scala.version}"),
      Dependency("org.scala-lang", s"scala-compiler", s"${scala.version}"),
      Dependency("org.scala-lang.modules", s"scala-xml_${scala.version.substring(0, 4)}", "1.2.0")
    )

  def circeLibraryDependencies(scala: ScalaVersion): List[Dependency] = {
    val sv = scala.version

    val circeVersion = "0.13.0"
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
