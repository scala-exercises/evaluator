/*
 * scala-exercises - evaluator-compiler
 * Copyright (C) 2015-2016 47 Degrees, LLC. <http://www.47deg.com>
 */

package org.scalaexercises.evaluator

import scala.language.reflectiveCalls
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.TimeoutException

import scalaz._
import Scalaz._
import scala.util.Try
import scala.util.control.NonFatal
import scala.concurrent.duration._
import scalaz.concurrent.Task
import monix.execution.Scheduler
import coursier._

class Evaluator(timeout: FiniteDuration = 20.seconds)(
    implicit S: Scheduler
) {
  type Remote = String

  def remoteToRepository(remote: Remote): Repository =
    MavenRepository(remote)

  def dependencyToModule(dependency: Dependency): coursier.Dependency =
    coursier.Dependency(
      Module(dependency.groupId, dependency.artifactId),
      dependency.version
    )

  def resolveArtifacts(remotes: Seq[Remote], dependencies: Seq[Dependency]): Task[Resolution] = {
    val resolution                    = Resolution(dependencies.map(dependencyToModule).toSet)
    val repositories: Seq[Repository] = Cache.ivy2Local +: remotes.map(remoteToRepository)
    val fetch                         = Fetch.from(repositories, Cache.fetch())
    resolution.process.run(fetch)
  }

  def fetchArtifacts(
      remotes: Seq[Remote],
      dependencies: Seq[Dependency]): Task[coursier.FileError \/ List[File]] =
    for {
      resolution <- resolveArtifacts(remotes, dependencies)
      artifacts <- Task.gatherUnordered(
        resolution.artifacts.map(Cache.file(_).run)
      )
    } yield artifacts.sequenceU

  def createEval(jars: Seq[File]): Eval = {

    def createTempDir(tmpName: String): File = {
      val f = new File(tmpName)
      f.mkdirs()
      f
    }
    val targetDir = Some(createTempDir("evaluator/classes"))

    new Eval(target = targetDir, jars = jars.toList)
  }

  private[this] def evaluate[T](code: String, jars: Seq[File]): EvalResult[T] = {
    val eval = createEval(jars)

    val result = for {
      _      ← Try(eval.check(code))
      result ← Try(eval.execute[T](code, resetState = true, jars = jars))
    } yield result

    val errors = eval.errors

    result match {
      case scala.util.Success(r) ⇒ EvalSuccess[T](errors, r, "")
      case scala.util.Failure(t) ⇒
        t match {
          case _: Eval.CompilerException ⇒ CompilationError(errors)
          case e: InvocationTargetException =>
            EvalRuntimeError(errors, Option(RuntimeError(e.getTargetException, None)))
          case NonFatal(e) ⇒
            EvalRuntimeError(errors, Option(RuntimeError(e, None)))
          case e ⇒ GeneralError(e)
        }
    }
  }

  def eval[T](
      code: String,
      remotes: Seq[Remote] = Nil,
      dependencies: Seq[Dependency] = Nil
  ): Task[EvalResult[T]] = {
    for {
      allJars <- fetchArtifacts(remotes, dependencies)
      result <- allJars match {
        case \/-(jars) =>
          Task({
            evaluate(code, jars)
          }).timed(timeout)
            .handle({
              case _: TimeoutException => Timeout[T](timeout)
            })
        case -\/(fileError) =>
          Task.now(UnresolvedDependency(fileError.describe))
      }
    } yield result
  }
}
