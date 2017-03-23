/*
 * scala-exercises - evaluator-compiler
 * Copyright (C) 2015-2016 47 Degrees, LLC. <http://www.47deg.com>
 */

package org.scalaexercises.evaluator

import monix.execution.Scheduler
import org.scalaexercises.evaluator.helper._
import org.scalatest._

import scala.concurrent.duration._
import scala.language.postfixOps

class EvaluatorSpec extends FunSpec with Matchers {
  implicit val scheduler: Scheduler = Scheduler.io("exercises-spec")
  val evaluator                     = new Evaluator(20 seconds)

  val remotes: List[String] = "https://oss.sonatype.org/content/repositories/releases/" :: Nil

  describe("evaluation") {
    it("can evaluate simple expressions, for Scala 2.11") {
      val result: EvalResult[Int] = evaluator
        .eval("{ 41 + 1 }", remotes = commonResolvers, dependencies = scalaDependencies(Scala211))
        .unsafePerformSync

      result should matchPattern {
        case EvalSuccess(_, 42, _) ⇒
      }
    }

    it("can evaluate simple expressions, for Scala 2.12") {
      val result: EvalResult[Int] = evaluator
        .eval("{ 41 + 1 }", remotes = commonResolvers, dependencies = scalaDependencies(Scala212))
        .unsafePerformSync

      result should matchPattern {
        case EvalSuccess(_, 42, _) ⇒
      }
    }

    it("fails with a timeout when takes longer than the configured timeout") {
      val result: EvalResult[Int] = evaluator
        .eval(
          "{ while(true) {}; 123 }",
          remotes = commonResolvers,
          dependencies = scalaDependencies(Scala211))
        .unsafePerformSync

      result should matchPattern {
        case Timeout(_) ⇒
      }
    }

    it("can load dependencies for an evaluation") {
      val code = """
import cats.data.Xor

Xor.Right(42).toOption.get
      """
      val remotes =
        List("https://oss.sonatype.org/content/repositories/releases/")
      val dependencies = List(
        Dependency("org.typelevel", "cats_2.11", "0.6.0")
      )

      val result: EvalResult[Int] = evaluator
        .eval(
          code,
          remotes = remotes,
          dependencies = dependencies
        )
        .unsafePerformSync

      result should matchPattern {
        case EvalSuccess(_, 42, _) =>
      }
    }

    it(
      s"can load binary incompatible dependencies for an evaluation, for scala ${BuildInfo.scalaVersion}") {

      val result: EvalResult[Int] = evaluator
        .eval(
          fetchCode,
          remotes = commonResolvers,
          dependencies = fetchLibraryDependencies(toScalaVersion(BuildInfo.scalaVersion))
        )
        .unsafePerformSync

      result should matchPattern {
        case EvalSuccess(_, _, _) =>
      }
    }

    it("can load different versions of a dependency across evaluations") {
      val code = """
import cats._
Eval.now(42).value
      """
      val remotes =
        List("https://oss.sonatype.org/content/repositories/releases/")
      val dependencies1 = List(
        Dependency("org.typelevel", "cats_2.11", "0.4.1")
      ) ++ scalaDependencies(Scala211)
      val dependencies2 = List(
        Dependency("org.typelevel", "cats_2.11", "0.6.0")
      ) ++ scalaDependencies(Scala211)

      val result1: EvalResult[Int] = evaluator
        .eval(
          code,
          remotes = remotes,
          dependencies = dependencies1
        )
        .unsafePerformSync
      val result2: EvalResult[Int] = evaluator
        .eval(
          code,
          remotes = remotes,
          dependencies = dependencies2
        )
        .unsafePerformSync

      result1 should matchPattern {
        case EvalSuccess(_, 42, _) =>
      }
      result2 should matchPattern {
        case EvalSuccess(_, 42, _) =>
      }
    }

    it("can run code from the exercises content") {
      val code = """
import stdlib._
Asserts.scalaTestAsserts(true)
"""
      val dependencies = List(
        Dependency("org.scala-exercises", "exercises-stdlib_2.11", "0.3.0-SNAPSHOT")
      ) ++ scalaDependencies(Scala211)

      val result: EvalResult[Unit] = evaluator
        .eval(
          code,
          remotes = commonResolvers,
          dependencies = dependencies
        )
        .unsafePerformSync

      result should matchPattern {
        case EvalSuccess(_, (), _) =>
      }
    }

    it("captures exceptions when running the exercises content") {
      val code = """
import stdlib._
Asserts.scalaTestAsserts(false)
"""
      val dependencies = List(
        Dependency("org.scala-exercises", "exercises-stdlib_2.11", "0.3.0-SNAPSHOT")
      ) ++ scalaDependencies(Scala211)

      val result: EvalResult[Unit] = evaluator
        .eval(
          code,
          remotes = commonResolvers,
          dependencies = dependencies
        )
        .unsafePerformSync

      result shouldBe a[EvalRuntimeError[_]]
    }

    describe("can run code from the exercises content") {
      val code = "{import cats._; Eval.now(42).value}"

      val dependencies = Dependency("org.typelevel", "cats_2.11", "0.6.0") :: Nil

      val result: EvalResult[Unit] = evaluator
        .eval(code, remotes = remotes, dependencies = dependencies)
        .unsafePerformSync

      result should matchPattern {
        case EvalSuccess(_, 42, _) =>
      }
    }
  }
}