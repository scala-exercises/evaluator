/*
 *
 *  scala-exercises - evaluator-shared
 *  Copyright (C) 2015-2019 47 Degrees, LLC. <http://www.47deg.com>
 *
 */

package org.scalaexercises.evaluator

import scala.concurrent.duration._

final case class RangePosition(start: Int, point: Int, end: Int)

final case class CompilationInfo(message: String, pos: Option[RangePosition])

final case class RuntimeError(error: Throwable, position: Option[Int])

sealed trait EvalResult[+A]

object EvalResult {
  type CI = Map[String, List[CompilationInfo]]
}

import org.scalaexercises.evaluator.EvalResult._

final case class EvalSuccess[A](compilationInfos: CI, result: A, consoleOutput: String)
    extends EvalResult[A]

final case class Timeout[A](duration: FiniteDuration) extends EvalResult[A]

final case class UnresolvedDependency[A](explanation: String) extends EvalResult[A]

final case class EvalRuntimeError[A](compilationInfos: CI, runtimeError: Option[RuntimeError])
    extends EvalResult[A]

final case class CompilationError[A](compilationInfos: CI) extends EvalResult[A]

final case class GeneralError[A](stack: Throwable) extends EvalResult[A]

final case class Dependency(groupId: String, artifactId: String, version: String)

final case class EvalRequest(
    resolvers: List[String] = Nil,
    dependencies: List[Dependency] = Nil,
    code: String)

final case class EvalResponse(
    msg: String,
    value: Option[String] = None,
    valueType: Option[String] = None,
    consoleOutput: Option[String] = None,
    compilationInfos: CI = Map.empty)

object EvalResponse {

  object messages {

    val `ok`                    = "Ok"
    val `Timeout Exceded`       = "Timeout"
    val `Unresolved Dependency` = "Unresolved Dependency"
    val `Runtime Error`         = "Runtime Error"
    val `Compilation Error`     = "Compilation Error"
    val `Unforeseen Exception`  = "Unforeseen Exception"

  }

}
