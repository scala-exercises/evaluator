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

import cats.effect.IO
import org.scalaexercises.evaluator.helper._
import org.scalatest.Succeeded
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._
import scala.language.postfixOps

class EvaluatorSpec extends AnyFunSpec with Matchers with Implicits {

  val evaluator = new Evaluator[IO](10 seconds)

  describe("evaluation") {

    it("can evaluate simple expressions, for Scala 2.13") {
      val result: EvalResult[Int] = evaluator
        .eval("{ 41 + 1 }", remotes = commonResolvers, dependencies = scalaDependencies(Scala213))
        .unsafeRunSync()

      result should matchPattern {
        case EvalSuccess(_, 42, _) =>
      }
    }

    it("fails with a timeout when takes longer than the configured timeout") {
      val result: EvalResult[Int] = evaluator
        .eval(
          "{ while(true) {}; 123 }",
          remotes = commonResolvers,
          dependencies = scalaDependencies(Scala213)
        )
        .unsafeRunSync()

      result should matchPattern {
        case Timeout(_) =>
      }
    }

    it("can load dependencies for an evaluation") {
      val code =
        """
import cats.effect._

IO(47 / 2).handleErrorWith(_ => IO.pure(0)).unsafeRunSync()
      """
      val remotes =
        List("https://oss.sonatype.org/content/repositories/releases/")
      val dependencies = List(
        Dependency("org.typelevel", "cats-effect_2.13", "2.1.0")
      )

      val result: EvalResult[Int] = evaluator
        .eval(
          code,
          remotes = remotes,
          dependencies = scalaDependencies(Scala213) ++ dependencies
        )
        .unsafeRunSync()

      result should matchPattern { case EvalSuccess(_, 23, _) => }
    }

    it(
      s"can load binary incompatible dependencies for an evaluation, for scala ${BuildInfo.scalaVersion}"
    ) {

      val result: EvalResult[Int] = evaluator
        .eval(
          circeCode,
          remotes = commonResolvers,
          dependencies = circeLibraryDependencies(toScalaVersion(BuildInfo.scalaVersion))
        )
        .unsafeRunSync()

      result should matchPattern {
        case EvalSuccess(_, _, _) =>
      }
    }

    it("can load different versions of a dependency across evaluations") {
      val code =
        """
import cats._
Eval.now(42).value
      """
      val remotes =
        List("https://oss.sonatype.org/content/repositories/releases/")
      val dependencies1 = List(
        Dependency("org.typelevel", "cats-core_2.13", "2.1.0")
      ) ++ scalaDependencies(Scala213)
      val dependencies2 = List(
        Dependency("org.typelevel", "cats-core_2.13", "2.0.0")
      ) ++ scalaDependencies(Scala213)

      val result1: EvalResult[Int] = evaluator
        .eval(
          code,
          remotes = remotes,
          dependencies = dependencies1
        )
        .unsafeRunSync()
      val result2: EvalResult[Int] = evaluator
        .eval(
          code,
          remotes = remotes,
          dependencies = dependencies2
        )
        .unsafeRunSync()

      result1 should matchPattern {
        case EvalSuccess(_, 42, _) =>
      }
      result2 should matchPattern {
        case EvalSuccess(_, 42, _) =>
      }
    }

    it("can run code from the exercises content") {
      val code = exerciseContentCode(true)
      val dependencies = List(
        Dependency("org.scala-exercises", "exercises-stdlib_2.13", exercisesVersion)
      ) ++ scalaDependencies(Scala213)

      val result: EvalResult[Unit] = evaluator
        .eval(
          code,
          remotes = commonResolvers,
          dependencies = dependencies
        )
        .unsafeRunSync()

      result should matchPattern {
        case EvalSuccess(_, Succeeded, _) =>
      }
    }

    it("captures exceptions when running the exercises content") {

      val dependencies = List(
        Dependency("org.scala-exercises", "exercises-stdlib_2.13", exercisesVersion)
      ) ++ scalaDependencies(Scala213)

      val result: EvalResult[Unit] = evaluator
        .eval(
          exerciseContentCode(false),
          remotes = commonResolvers,
          dependencies = dependencies
        )
        .unsafeRunSync()

      result shouldBe a[EvalRuntimeError[_]]
    }

    it("can run code with 2.13 dependencies") {
      val code = "{import cats._; Eval.now(42).value}"

      val dependencies = Dependency("org.typelevel", "cats-core_2.13", "2.1.0") :: Nil

      val result: EvalResult[Int] = evaluator
        .eval(code, remotes = remotes, dependencies = dependencies)
        .unsafeRunSync()

      result should matchPattern {
        case EvalSuccess(_, 42, _) =>
      }
    }
  }
}
