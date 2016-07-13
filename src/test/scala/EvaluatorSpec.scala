/*
 * scala-exercises-evaluator
 * Copyright (C) 2015-2016 47 Degrees, LLC. <http://www.47deg.com>
 */

package org.scalaexercises.evaluator

import scalaz._; import Scalaz._
import scala.concurrent.duration._
import org.scalatest._
import java.util.concurrent._

class EvaluatorSpec extends FunSpec with Matchers {
  System.setSecurityManager(new SandboxedSecurityManager())

  val scheduler: ExecutorService = SandboxedExecution.executor
  val evaluator = new Evaluator(30 seconds, scheduler)

  describe("evaluation") {
    it("can evaluate simple expressions") {
      val task = evaluator.eval[Int]("{ 41 + 1 }")

      task.run should matchPattern {
        case EvalSuccess(_, 42, _) =>
      }
    }

    it("fails with a timeout when takes longer than the configured timeout") {
      val task = evaluator.eval[Int]("{ while(true) {}; 123 }")

      task.run should matchPattern {
        case Timeout(_) ⇒
      }
    }

    it("can load dependencies for an evaluation") {
      val code = """
import cats._
Eval.now(42).value
      """
      val remotes = List("https://oss.sonatype.org/content/repositories/releases/")
      val dependencies = List(
        Dependency("org.typelevel", "cats_2.11", "0.6.0")
      )

      val result: EvalResult[Int] = evaluator.eval(
        code,
        remotes = remotes,
        dependencies = dependencies
      ).run

      result should matchPattern {
        case EvalSuccess(_, 42, _) =>
      }
    }

    it("can load different versions of a dependency across evaluations") {
      val code = """
import cats._
Eval.now(42).value
      """
      val remotes = List("https://oss.sonatype.org/content/repositories/releases/")
      val dependencies1 = List(
        Dependency("org.typelevel", "cats_2.11", "0.4.1")
      )
      val dependencies2 = List(
        Dependency("org.typelevel", "cats_2.11", "0.6.0")
      )

      val result1: EvalResult[Int] = evaluator.eval(
        code,
        remotes = remotes,
        dependencies = dependencies1
      ).run
      val result2: EvalResult[Int] = evaluator.eval(
        code,
        remotes = remotes,
        dependencies = dependencies2
      ).run

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
      val remotes = List("https://oss.sonatype.org/content/repositories/releases/")
      val dependencies = List(
        Dependency("org.scala-exercises", "exercises-stdlib_2.11", "0.2.0")
      )

      val result: EvalResult[Unit] = evaluator.eval(
        code,
        remotes = remotes,
        dependencies = dependencies
      ).run

      result should matchPattern {
        case EvalSuccess(_, (), _) =>
      }
    }

    it("captures exceptions when running the exercises content") {
      val code = """
import stdlib._
Asserts.scalaTestAsserts(false)
"""
      val remotes = List("https://oss.sonatype.org/content/repositories/releases/")
      val dependencies = List(
        Dependency("org.scala-exercises", "exercises-stdlib_2.11", "0.2.0")
      )

      val result: EvalResult[Unit] = evaluator.eval(
        code,
        remotes = remotes,
        dependencies = dependencies
      ).run

      result should matchPattern {
        case EvalRuntimeError(_, Some(RuntimeError(err: TestFailedException, _))) =>
      }
    }

    it("doesn't allow code to call System.exit") {
      val result: EvalResult[Unit] = evaluator.eval("sys.exit(1)").run

      result should matchPattern {
        case SecurityViolation(_) ⇒
      }
    }

    it("doesn't allow to install a different security manager") {
      val code = """
import java.security._

class MaliciousSecurityManager extends SecurityManager{
  override def checkPermission(perm: Permission): Unit = {
    // allow anything to happen by not throwing a security exception
  }
}

System.setSecurityManager(new MaliciousSecurityManager())
"""
      val result: EvalResult[Unit] = evaluator.eval(code).run

      result should matchPattern {
        case SecurityViolation(_) ⇒
      }
    }

    // todo: set policy, launched threads sandboxed
  }
}
