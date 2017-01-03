/*
 * scala-exercises-evaluator-server
 * Copyright (C) 2015-2016 47 Degrees, LLC. <http://www.47deg.com>
 */

package org.scalaexercises.evaluator

import scala.concurrent.duration._
import scala.language.postfixOps
import monix.execution.Scheduler
import org.scalatest._
import org.scalatest.exceptions.TestFailedException

class EvaluatorSpec extends FunSpec with Matchers {
  implicit val scheduler: Scheduler = Scheduler.io("exercises-spec")
  val evaluator                     = new Evaluator(20 seconds)

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
        .run

      result should matchPattern {
        case EvalSuccess(_, 42, _) =>
      }
    }

    it("can load binary incompatible dependencies for an evaluation") {
      val code =
        """
type UserId = Int
case class User(id: UserId, username: String)

def latency[A](result: A, msg: String) = {
  val id = Thread.currentThread.getId
  println(s"~~> [$id] $msg")
  Thread.sleep(100)
  println(s"<~~ [$id] $msg")
  result
}

import cats.data.NonEmptyList
import cats.instances.list._

import fetch._

val userDatabase: Map[UserId, User] = Map(
  1 -> User(1, "@one"),
  2 -> User(2, "@two"),
  3 -> User(3, "@three"),
  4 -> User(4, "@four")
)

implicit object UserSource extends DataSource[UserId, User]{
  override def name = "User"

  override def fetchOne(id: UserId): Query[Option[User]] = {
    Query.sync({
	  latency(userDatabase.get(id), s"One User $id")
    })
  }
  override def fetchMany(ids: NonEmptyList[UserId]): Query[Map[UserId, User]] = {
    Query.sync({
	  latency(userDatabase.filterKeys(ids.toList.contains), s"Many Users $ids")
    })
  }
}

def getUser(id: UserId): Fetch[User] = Fetch(id) // or, more explicitly: Fetch(id)(UserSource)

implicit object UnbatchedSource extends DataSource[Int, Int]{
  override def name = "Unbatched"

  override def fetchOne(id: Int): Query[Option[Int]] = {
    Query.sync(Option(id))
  }
  override def fetchMany(ids: NonEmptyList[Int]): Query[Map[Int, Int]] = {
    batchingNotSupported(ids)
  }
}

val fetchUser: Fetch[User] = getUser(1)

import cats.Id
import fetch.unsafe.implicits._
import fetch.syntax._

fetchUser.runA[Id]

val fetchTwoUsers: Fetch[(User, User)] = for {
  aUser <- getUser(1)
  anotherUser <- getUser(aUser.id + 1)
} yield (aUser, anotherUser)

fetchTwoUsers.runA[Id]

import cats.syntax.cartesian._

val fetchProduct: Fetch[(User, User)] = getUser(1).product(getUser(2))

fetchProduct.runA[Id]
      """
      val remotes = List(
        "https://oss.sonatype.org/content/repositories/snapshots",
        "https://oss.sonatype.org/content/repositories/public"
      )
      val dependencies = List(
        Dependency("com.fortysevendeg", "fetch_2.11", "0.4.0"),
        Dependency("com.fortysevendeg", "fetch-monix_2.11", "0.4.0")
      )

      val result: EvalResult[Int] = evaluator
        .eval(
          code,
          remotes = remotes,
          dependencies = dependencies
        )
        .run

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
      )
      val dependencies2 = List(
        Dependency("org.typelevel", "cats_2.11", "0.6.0")
      )

      val result1: EvalResult[Int] = evaluator
        .eval(
          code,
          remotes = remotes,
          dependencies = dependencies1
        )
        .run
      val result2: EvalResult[Int] = evaluator
        .eval(
          code,
          remotes = remotes,
          dependencies = dependencies2
        )
        .run

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
      val remotes =
        List("https://oss.sonatype.org/content/repositories/releases/")
      val dependencies = List(
        Dependency("org.scala-exercises", "exercises-stdlib_2.11", "0.2.0")
      )

      val result: EvalResult[Unit] = evaluator
        .eval(
          code,
          remotes = remotes,
          dependencies = dependencies
        )
        .run

      result should matchPattern {
        case EvalSuccess(_, (), _) =>
      }
    }

    it("captures exceptions when running the exercises content") {
      val code = """
import stdlib._
Asserts.scalaTestAsserts(false)
"""
      val remotes =
        List("https://oss.sonatype.org/content/repositories/releases/")
      val dependencies = List(
        Dependency("org.scala-exercises", "exercises-stdlib_2.11", "0.2.0")
      )

      val result: EvalResult[Unit] = evaluator
        .eval(
          code,
          remotes = remotes,
          dependencies = dependencies
        )
        .run

      result should matchPattern {
        case EvalRuntimeError(
            _,
            Some(RuntimeError(err: TestFailedException, _))) =>
      }
    }
  }
}
