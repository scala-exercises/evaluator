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
import scalaz.concurrent.{ Task => ZTask }

import monix.eval.Task
import monix.execution._

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
  case class EvalRuntimeError(complilationInfos: CI, runtimeError: Option[RuntimeError]) extends EvalResult[Nothing]
  case class CompilationError(complilationInfos: CI) extends EvalResult[Nothing]
  case class GeneralError(stack: Throwable) extends EvalResult[Nothing]
}

class Evaluator(timeout: FiniteDuration = 20.seconds) {
  implicit val scheduler: Scheduler = Scheduler.io("evaluation-scheduler")

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

  def resolveArtifacts(remotes: Seq[Remote], dependencies: Seq[Dependency]): Resolution = {
    val resolution = Resolution(
      dependencies.map(d => {
        Dependency(
          Module(d._1, d._2), d._3
        )
      }).toSet
    )
    val repositories: Seq[Repository] = remotes.map(url => MavenRepository(url))

    val fetch = Fetch.from(repositories, Cache.fetch())
    resolution.process.run(fetch).run
  }

  def fetchArtifacts(resolution: Resolution): List[coursier.FileError \/ File] = {
    if (resolution.isDone)
      ZTask.gatherUnordered(
        resolution.artifacts.map(Cache.file(_).run)
      ).run
    else
      Nil
  }

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

  private[this] def performEval[T](code: String, jars: Seq[File]): EvalResult[T] = {
    val eval = createEval(jars)

    val result = for {
      _ ← Try(eval.check(code))
      result ← Try(eval.applyProcessed[T](code, resetState = true, jars = jars))
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

  def eval[T](code: String, remotes: Seq[Remote] = Nil, dependencies: Seq[Dependency] = Nil): EvalResult[T] = {
    // todo: don't take into account dependency resolution time in timeout
    val resolution = resolveArtifacts(remotes, dependencies)
    val allJars = fetchArtifacts(resolution).sequenceU

    val jars: Seq[File] = allJars match {
      case \/-(jars) => jars
      case -\/(fileError) => Nil // todo: handle errors
    }

    val evaluation = Task({
      performEval(code, jars)
    }) // todo

    Try(
      Await.result(evaluation.runAsync, timeout)
    ).getOrElse(EvalResult.Timeout())
  }
}
