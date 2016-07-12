package org.scalaexercises.evaluator

import scala.concurrent.duration._

sealed trait Severity
final case object Informational extends Severity
final case object Warning extends Severity
final case object Error extends Severity

case class RangePosition(start: Int, point: Int, end: Int)
case class CompilationInfo(message: String, pos: Option[RangePosition])
case class RuntimeError(val error: Throwable, position: Option[Int])

sealed trait EvalResult[+T]

object EvalResult {
  type CI = Map[Severity, List[CompilationInfo]]
}
import EvalResult._

case class EvalSuccess[T](complilationInfos: CI, result: T, consoleOutput: String) extends EvalResult[T]
case class Timeout[T](duration: FiniteDuration) extends EvalResult[T]
case class UnresolvedDependency[T](explanation: String) extends EvalResult[T]
case class EvalRuntimeError[T](complilationInfos: CI, runtimeError: Option[RuntimeError]) extends EvalResult[T]
case class CompilationError[T](complilationInfos: CI) extends EvalResult[T]
case class GeneralError[T](stack: Throwable) extends EvalResult[T]
