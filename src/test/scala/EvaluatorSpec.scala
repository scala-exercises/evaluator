/*
 * scala-exercises-evaluator
 * Copyright (C) 2015-2016 47 Degrees, LLC. <http://www.47deg.com>
 */

package org.scalaexercises.evaluator

import scala.concurrent.duration._
import org.scalatest._

class EvaluatorSpec extends FunSpec with Matchers {
  val evaluator = new Evaluator(10 seconds)

  describe("evaluation") {
    it("can evaluate simple expressions") {
      val result: EvalResult[Int] = evaluator.eval("{ 41 + 1 }")

      result should matchPattern {
        case EvalResult.Success(_, 42, _) ⇒
      }
    }

    it("fails with a timeout when takes longer than the configured timeout") {
      val result: EvalResult[Int] = evaluator.eval("{ while(true) {}; 123 }")

      result should matchPattern {
        case t: EvalResult.Timeout[_] ⇒
      }
    }

    it("can load dependencies for an evaluation") {
      val code = """
import cats._

Eval.now(42).value
      """
      val remotes = List("https://oss.sonatype.org/content/repositories/releases/")
      val dependencies = List(
        ("org.typelevel", "cats_2.11", "0.6.0")
      )

      val result: EvalResult[Int] = evaluator.eval(
        code,
        remotes = remotes,
        dependencies = dependencies
      )

      result should matchPattern {
        case EvalResult.Success(_, 42, _) =>
      }
    }

    it("can load different versions of a dependency across evaluations") {
      val code = """
import cats._
Eval.now(42).value
      """
      val remotes = List("https://oss.sonatype.org/content/repositories/releases/")
      val dependencies1 = List(
        ("org.typelevel", "cats_2.11", "0.4.1")
      )
      val dependencies2 = List(
        ("org.typelevel", "cats_2.11", "0.6.0")
      )

      val result1: EvalResult[Int] = evaluator.eval(
        code,
        remotes = remotes,
        dependencies = dependencies1
      )
      val result2: EvalResult[Int] = evaluator.eval(
        code,
        remotes = remotes,
        dependencies = dependencies2
      )

      result1 should matchPattern {
        case EvalResult.Success(_, 42, _) =>
      }
      result2 should matchPattern {
        case EvalResult.Success(_, 42, _) =>
      }
    }

    it("can run code from the exercises content") {
      val code = """
import stdlib._
Asserts.scalaTestAsserts(true)
"""
      val remotes = List("https://oss.sonatype.org/content/repositories/releases/")
      val dependencies = List(
        ("org.scala-exercises", "exercises-stdlib_2.11", "0.2.0")
      )

      val result: EvalResult[Unit] = evaluator.eval(
        code,
        remotes = remotes,
        dependencies = dependencies
      )

      result should matchPattern {
        case EvalResult.Success(_, (), _) =>
      }
    }

    it("captures exceptions when running the exercises content") {
      val code = """
import stdlib._
Asserts.scalaTestAsserts(false)
"""
      val remotes = List("https://oss.sonatype.org/content/repositories/releases/")
      val dependencies = List(
        ("org.scala-exercises", "exercises-stdlib_2.11", "0.2.0")
      )

      val result: EvalResult[Unit] = evaluator.eval(
        code,
        remotes = remotes,
        dependencies = dependencies
      )

      result should matchPattern {
        case EvalResult.EvalRuntimeError(_, Some(RuntimeError(err: TestFailedException, _))) =>
      }
    }
  }
}
