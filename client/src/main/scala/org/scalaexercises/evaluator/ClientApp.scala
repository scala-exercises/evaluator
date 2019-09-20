/*
 *
 *  scala-exercises - evaluator-client
 *  Copyright (C) 2015-2019 47 Degrees, LLC. <http://www.47deg.com>
 *
 */

package org.scalaexercises.evaluator

import cats.Monad.ops.toAllMonadOps
import cats.effect.{ExitCode, IO, IOApp}
import cats.syntax.applicative.catsSyntaxApplicativeId

object ClientApp extends IOApp {

  val validToken: String =
    "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eW91ciBpZGVudGl0eQ.cfH43Wa7k_w1i0W2pQhV1k21t2JqER9lw5EpJcENRMI"

  val req = EvalRequest(Nil, Nil, "{ 1 + 1 }")

  override def run(args: List[String]): IO[ExitCode] =
    EvaluatorClient[IO]("http://127.0.0.1:8080/eval", validToken)
      .evaluates(req)
      .map(println(_)) *> ExitCode.Success.pure[IO]

}
