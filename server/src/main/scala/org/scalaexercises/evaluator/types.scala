/*
 * Copyright 2016-2020 47 Degrees Open Source <https://www.47deg.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scalaexercises.evaluator

import io.circe.generic.semiauto.deriveDecoder

import scala.concurrent.duration._
import io.circe.Decoder

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

final case class Exclusion(organization: String, moduleName: String)

object Exclusion {
  implicit val decExclusion: Decoder[Exclusion] = deriveDecoder
}

final case class Dependency(
    groupId: String,
    artifactId: String,
    version: String,
    exclusions: Option[List[Exclusion]] = None
)

object Dependency {
  implicit val decDependency: Decoder[Dependency] = deriveDecoder
}

final case class EvalRequest(
    resolvers: List[String] = Nil,
    dependencies: List[Dependency] = Nil,
    code: String
)

object EvalRequest {
  implicit val decEvalRequest: Decoder[EvalRequest] = deriveDecoder
}

final case class EvalResponse(
    msg: String,
    value: Option[String] = None,
    valueType: Option[String] = None,
    consoleOutput: Option[String] = None,
    compilationInfos: CI = Map.empty
)

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
