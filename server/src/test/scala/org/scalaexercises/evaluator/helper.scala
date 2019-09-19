/*
 *
 *  scala-exercises - evaluator-server
 *  Copyright (C) 2015-2019 47 Degrees, LLC. <http://www.47deg.com>
 *
 */

package org.scalaexercises.evaluator

object helper {

  val remotes: List[String]    = "https://oss.sonatype.org/content/repositories/releases/" :: Nil
  val exercisesVersion: String = "0.4.1-SNAPSHOT"

  sealed abstract class ScalaVersion(val version: String)
  case object Scala211 extends ScalaVersion("2.11.8")
  case object Scala212 extends ScalaVersion("2.12.1")

  def toScalaVersion(v: String): ScalaVersion = v match {
    case version if version.startsWith("2.11") => Scala211
    case version if version.startsWith("2.12") => Scala212
    case _                                     => throw new IllegalArgumentException(s"Unknown Scala Version $v")
  }

  val commonResolvers = List(
    "https://oss.sonatype.org/content/repositories/snapshots",
    "https://oss.sonatype.org/content/repositories/public",
    "http://repo1.maven.org/maven2"
  )

  def scalaDependencies(scala: ScalaVersion): List[Dependency] = List(
    Dependency("org.scala-lang", s"scala-library", s"${scala.version}"),
    Dependency("org.scala-lang", s"scala-api", s"${scala.version}"),
    Dependency("org.scala-lang", s"scala-reflect", s"${scala.version}"),
    Dependency("org.scala-lang", s"scala-compiler", s"${scala.version}"),
    Dependency("org.scala-lang", "scala-xml", s"${scala.version}")
  )

  def fetchLibraryDependencies(scala: ScalaVersion): List[Dependency] = {
    val sv = scala.version
    List(
      Dependency("com.fortysevendeg", s"fetch_${sv.substring(0, 4)}", "0.4.0"),
      Dependency("com.fortysevendeg", s"fetch-monix_${sv.substring(0, 4)}", "0.4.0")
    ) ++ scalaDependencies(scala)
  }

  def exerciseContentCode(assertCheck: Boolean) =
    s"""
import org.scalaexercises.content._
import stdlib.Asserts
import stdlib.Asserts._
import stdlib._

Asserts.scalaTestAsserts($assertCheck)
    """

  val fetchCode =
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
}
