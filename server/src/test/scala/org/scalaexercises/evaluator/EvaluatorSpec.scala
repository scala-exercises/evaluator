/*
 * scala-exercises-evaluator-server
 * Copyright (C) 2015-2016 47 Degrees, LLC. <http://www.47deg.com>
 */

package org.scalaexercises.evaluator

import monix.execution.Scheduler
import org.scalatest._
import org.scalatest.exceptions.TestFailedException

import scala.concurrent.duration._
import scala.language.postfixOps

class EvaluatorSpec extends FunSpec with Matchers {
  implicit val scheduler: Scheduler = Scheduler.io("exercises-spec")
  val evaluator                     = new Evaluator(20 seconds)

  val remotes = "https://oss.sonatype.org/content/repositories/releases/" :: Nil

  describe("evaluation") {
    it("can evaluate simple expressions") {
      val result: EvalResult[Int] = evaluator.eval("{ 41 + 1 }").run

      result should matchPattern {
        case EvalSuccess(_, 42, _) ⇒
      }
    }

    it("fails with a timeout when takes longer than the configured timeout") {
      val result: EvalResult[Int] =
        evaluator.eval("{ while(true) {}; 123 }").run

      result should matchPattern {
        case Timeout(_) ⇒
      }
    }

    describe("can load dependencies for an evaluation") {
      val code = "{import cats._; Eval.now(42).value}"

      val dependencies = Dependency("org.typelevel", "cats_2.11", "0.6.0") :: Nil

      val result: EvalResult[Int] = evaluator
        .eval(code, remotes = remotes, dependencies = dependencies)
        .run

      result should matchPattern {
        case EvalSuccess(_, 42, _) =>
      }
    }

    describe("can load different versions of a dependency across evaluations") {
      val code = "{import cats._; Eval.now(42).value}"

      val dependencies1 = Dependency("org.typelevel", "cats_2.11", "0.4.1") :: Nil

      val dependencies2 = Dependency("org.typelevel", "cats_2.11", "0.6.0") :: Nil

      val result1: EvalResult[Int] = evaluator
        .eval(code, remotes = remotes, dependencies = dependencies1)
        .run
      val result2: EvalResult[Int] = evaluator
        .eval(code, remotes = remotes, dependencies = dependencies2)
        .run

      result1 should matchPattern {
        case EvalSuccess(_, 42, _) =>
      }
      result2 should matchPattern {
        case EvalSuccess(_, 42, _) =>
      }
    }

    describe("can run code from the exercises content") {
      val code = "{import stdlib._; Asserts.scalaTestAsserts(true)}"

      val dependencies = Dependency(
          "org.scala-exercises",
          "exercises-stdlib_2.11",
          "0.2.0") :: Nil

      val result: EvalResult[Unit] = evaluator
        .eval(code, remotes = remotes, dependencies = dependencies)
        .run

      result should matchPattern {
        case EvalSuccess(_, (), _) =>
      }
    }

    describe("captures exceptions when running the exercises content") {
      val code = "{import stdlib._; Asserts.scalaTestAsserts(false)}"

      val dependencies = Dependency(
          "org.scala-exercises",
          "exercises-stdlib_2.11",
          "0.2.0") :: Nil

      val result: EvalResult[Unit] = evaluator
        .eval(code, remotes = remotes, dependencies = dependencies)
        .run

      result should matchPattern {
        case EvalRuntimeError(
            _,
            Some(RuntimeError(err: TestFailedException, _))) =>
      }
    }

    describe("can run code from the exercises content") {
      val code = "{import cats._; Eval.now(42).value}"

      val dependencies = Dependency("org.typelevel", "cats_2.11", "0.6.0") :: Nil

      val compilerFlags = Nil

      val result: EvalResult[Unit] = evaluator
        .eval(
          code,
          remotes = remotes,
          dependencies = dependencies,
          compilerFlags = compilerFlags)
        .run

      result should matchPattern {
        case EvalSuccess(_, 42, _) =>
      }
    }

    describe("can evaluate code without any compiler flags provided") {
      val code = "{import cats._; Eval.now(42).value}"

      val dependencies = Dependency("org.typelevel", "cats_2.11", "0.6.0") :: Nil

      val result: EvalResult[Unit] = evaluator
        .eval(code, remotes = remotes, dependencies = dependencies)
        .run

      result should matchPattern {
        case EvalSuccess(_, 42, _) =>
      }
    }

    describe("can evaluate code with an empty list of compiler flags provided") {
      val code = "{import cats._; Eval.now(42).value}"

      val dependencies = Dependency("org.typelevel", "cats_2.11", "0.6.0") :: Nil

      val compilerFlags = Nil

      val result: EvalResult[Unit] = evaluator
        .eval(
          code,
          remotes = remotes,
          dependencies = dependencies,
          compilerFlags = compilerFlags)
        .run

      result should matchPattern {
        case EvalSuccess(_, 42, _) =>
      }
    }

    describe("can evaluate code with a list of compiler flags provided") {
      val code = "{import cats._; Eval.now(42).value}"

      val dependencies = Dependency("org.typelevel", "cats_2.11", "0.6.0") :: Nil

      val compilerFlags = List("-X", "-help")

      val result: EvalResult[Unit] = evaluator
        .eval(
          code,
          remotes = remotes,
          dependencies = dependencies,
          compilerFlags = compilerFlags)
        .run

      result should matchPattern {
        case EvalSuccess(_, 42, _) =>
      }
    }
  }
}
