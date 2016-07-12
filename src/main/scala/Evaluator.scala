/*
 * scala-exercises-evaluator
 * Copyright (C) 2015-2016 47 Degrees, LLC. <http://www.47deg.com>
 */

package org.scalaexercises.evaluator

import scala.language.reflectiveCalls

import java.io.{ File, InputStream }
import java.nio.file.Path
import java.util.jar.JarFile
import java.util.concurrent.TimeoutException

import scala.tools.nsc.{ Global, Settings }
import scala.tools.nsc.reporters._
import scala.tools.nsc.io.{ VirtualDirectory, AbstractFile }
import scala.reflect.internal.util.{ Position, NoPosition, BatchSourceFile, AbstractFileClassLoader }

import scalaz._; import Scalaz._
import scala.util.Try
import scala.util.control.NonFatal
import scala.concurrent._
import scala.concurrent.duration._
import scalaz.concurrent.Task

import monix.execution.Scheduler

import com.twitter.util.Eval; import Eval._

import coursier._

sealed trait Severity
final case object Info extends Severity
final case object Warning extends Severity
final case object Error extends Severity

case class RangePosition(start: Int, point: Int, end: Int)
case class CompilationInfo(message: String, pos: Option[RangePosition])
case class RuntimeError(val error: Throwable, position: Option[Int])

sealed trait EvalResult[+T]

object EvalResult {
  type CI = Map[Severity, List[CompilationInfo]]

  case object Timeout extends EvalResult[Nothing]
  case class Success[T](complilationInfos: CI, result: T, consoleOutput: String) extends EvalResult[T]
  case class Timeout[T]() extends EvalResult[T]
  case class UnresolvedDependency(error: coursier.FileError) extends EvalResult[Nothing]
  case class EvalRuntimeError(complilationInfos: CI, runtimeError: Option[RuntimeError]) extends EvalResult[Nothing]
  case class CompilationError(complilationInfos: CI) extends EvalResult[Nothing]
  case class GeneralError(stack: Throwable) extends EvalResult[Nothing]
}

class Evaluator(timeout: FiniteDuration = 20.seconds)(
  implicit S: Scheduler
) {
  type Dependency = (String, String, String)
  type Remote = String

  private[this] def convert(errors: (Position, String, String)): (Severity, List[CompilationInfo]) = {
    val (pos, msg, severity) = errors
    val sev = severity match {
      case "ERROR"   ⇒ Error
      case "WARNING" ⇒ Warning
      case _         ⇒ Info
    }
    (sev, CompilationInfo(msg, Some(RangePosition(pos.start, pos.point, pos.end))) :: Nil)
  }

  def remoteToRepository(remote: Remote): Repository =
    MavenRepository(remote)

  def dependencyToModule(dependency: Dependency): coursier.Dependency =
    coursier.Dependency(
      Module(dependency._1, dependency._2), dependency._3
    )

  def resolveArtifacts(remotes: Seq[Remote], dependencies: Seq[Dependency]): Task[Resolution] = {
    val resolution = Resolution(dependencies.map(dependencyToModule).toSet)
    val repositories: Seq[Repository] = remotes.map(remoteToRepository)
    val fetch = Fetch.from(repositories, Cache.fetch())
    resolution.process.run(fetch)
  }

  def fetchArtifacts(remotes: Seq[Remote], dependencies: Seq[Dependency]): Task[coursier.FileError \/ List[File]] = for {
    resolution <- resolveArtifacts(remotes, dependencies)
    artifacts <- Task.gatherUnordered(
      resolution.artifacts.map(Cache.file(_).run)
    )
  } yield artifacts.sequenceU

  def createEval(jars: Seq[File]) = {
    new Eval(jars = jars.toList) {
      @volatile var errors: Map[Severity, List[CompilationInfo]] = Map.empty

      override lazy val compilerSettings: Settings = new EvalSettings(None){
        if (!jars.isEmpty) {
          val newJars = jars.mkString(File.pathSeparator)
          classpath.value = newJars + File.pathSeparator + classpath.value
        }
      }

      override lazy val compilerMessageHandler: Option[Reporter] = Some(new AbstractReporter {
        override val settings: Settings = compilerSettings
        override def displayPrompt(): Unit = ()
        override def display(pos: Position, msg: String, severity: this.type#Severity): Unit = {
          errors += convert((pos, msg, severity.toString))
        }
        override def reset() = {
          super.reset()
          errors = Map.empty
        }
      })
    }
  }

  private[this] def evaluate[T](code: String, jars: Seq[File]): EvalResult[T] = {
    val eval = createEval(jars)

    val result = for {
      _ ← Try(eval.check(code))
      result ← Try(eval.execute[T](code, resetState = true, jars = jars))
    } yield result

    val errors: Map[Severity, List[CompilationInfo]] = eval.errors.toMap

    result match {
      case scala.util.Success(r) ⇒ EvalResult.Success[T](errors, r, "")
      case scala.util.Failure(t) ⇒ t match {
        case e: Eval.CompilerException ⇒ EvalResult.CompilationError(errors)
        case NonFatal(e)               ⇒ EvalResult.EvalRuntimeError(errors, Option(RuntimeError(e, None)))
        case e                         ⇒ EvalResult.GeneralError(e)
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
        case \/-(jars) => Task({
          evaluate(code, jars)
        }).timed(timeout).handle({
          case err: TimeoutException => EvalResult.Timeout[T]()
        })
        case -\/(fileError) => Task.now(EvalResult.UnresolvedDependency(fileError))
      }
    } yield result
  }
}
