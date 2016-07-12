package org.scalaexercises.evaluator

/**
 * The incoming request specifying scala version, resolvers and code to eval
 */
case class EvalRequest(
  scalaVersion: String,
  resolvers: List[String],
  dependencies: List[(String, String, String)],
  code: String
)

/** The result from compiling + evaling some code */
sealed trait EvalResult[+A]

object EvalResult {

  type CI = Map[Severity, List[CompilationInfo]]

  /**
   * Compilation error severity levels
   */
  sealed trait Severity
  final case object Info extends Severity
  final case object Warning extends Severity
  final case object Error extends Severity

  /** A source code range and position where a warning, error or info event may be attached */
  case class RangePosition(start: Int, point: Int, end: Int)
  /** A message explaining the cause of a compilation error and warning and it's potential code position */
  case class CompilationInfo(message: String, pos: Option[RangePosition])
  /** A runtime exception while evaluating the code and the potential source position where it may have arised */
  case class RuntimeError(val error: String, position: Option[Int])

  /** If the compilation and evaluation exceed the configured Timeout */
  case object Timeout extends EvalResult[Nothing]
  /** If it all works out and compilation + evaluation proceed normally */
  case class Success[A](complilationInfos: CI, result: A, consoleOutput: String) extends EvalResult[A]
  /** If an exception is thrown while evaluating */
  case class EvalRuntimeError(complilationInfos: CI, runtimeError: Option[RuntimeError]) extends EvalResult[Nothing]
  /** If the code does not compile */
  case class CompilationError(complilationInfos: CI) extends EvalResult[Nothing]
  /** If an unforseen error that is not a compilation error or evaluation error is thrown while attempting to compile and evaluate */
  case class GeneralError(msg: String) extends EvalResult[Nothing]

}

/** The compilation + evaluation response */
case class EvalResponse[+A](
  origin: EvalRequest,
  result: EvalResult[A]
)

