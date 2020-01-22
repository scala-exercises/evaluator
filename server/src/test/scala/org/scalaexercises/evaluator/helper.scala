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

  def fetchLibraryDependencies(scala: ScalaVersion): List[Dependency] = {
    val sv = scala.version
    List(
      Dependency("com.47deg", s"fetch_${sv.substring(0, 4)}", "1.2.1")
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

  val fetchCode =
    """
  import scala.concurrent.ExecutionContext

  import cats.data.NonEmptyList
  import cats.effect._

  import fetch._
  import cats.implicits._

  val executionContext: ExecutionContext = ExecutionContext.fromExecutor(new java.util.concurrent.ForkJoinPool(2))

  implicit val timer: Timer[IO]     = IO.timer(executionContext)
  implicit val cs: ContextShift[IO] = IO.contextShift(executionContext)

  type UserId = Int

  case class User(id: UserId, username: String)

  def latency[F[_]: Concurrent](msg: String): F[Unit] =
    for {
      _ <- Sync[F].delay(println(s"--> [${Thread.currentThread.getId}] $msg"))
      _ <- Sync[F].delay(Thread.sleep(100))
      _ <- Sync[F].delay(println(s"<-- [${Thread.currentThread.getId}] $msg"))
    } yield ()

  val userDatabase: Map[UserId, User] = Map(
    1 -> User(1, "@one"),
    2 -> User(2, "@two"),
    3 -> User(3, "@three"),
    4 -> User(4, "@four")
  )

  object Users extends Data[UserId, User] {
    def name = "Users"

    def source[F[_]: Concurrent]: DataSource[F, UserId, User] = new DataSource[F, UserId, User] {
      override def data = Users

      def CF = Concurrent[F]

      override def fetch(id: UserId): F[Option[User]] =
        latency[F](s"One User $id") >> CF.pure(userDatabase.get(id))

      override def batch(ids: NonEmptyList[UserId]): F[Map[UserId, User]] =
        latency[F](s"Batch Users $ids") >> CF.pure(userDatabase.view.filterKeys(ids.toList.toSet).toMap)
    }
  }

  def getUser[F[_]: Concurrent](id: UserId): Fetch[F, User] = Fetch[F, UserId, User](id, Users.source)

  def fetchUser[F[_] : Concurrent]: Fetch[F, User] = getUser(1)

  Fetch.run[IO](fetchUser).unsafeRunSync()
    """
}
