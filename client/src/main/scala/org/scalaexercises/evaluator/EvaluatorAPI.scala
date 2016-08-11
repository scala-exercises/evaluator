/*
 * scala-exercises-evaluator-client
 * Copyright (C) 2015-2016 47 Degrees, LLC. <http://www.47deg.com>
 */

package org.scalaexercises.evaluator

import cats.free.Free
import org.scalaexercises.evaluator.EvaluatorResponses.EvaluationResponse
import org.scalaexercises.evaluator.free.algebra.{EvaluatorOp, EvaluatorOps}

import scala.concurrent.duration.Duration

class EvaluatorAPI(
  url: String,
  authKey: String,
  connTimeout: Duration,
  readTimeout: Duration)(implicit O: EvaluatorOps[EvaluatorOp]) {

  def evaluates(
    resolvers: List[String] = Nil,
    dependencies: List[Dependency] = Nil,
    code: String): Free[EvaluatorOp, EvaluationResponse[EvalResponse]] =
    O.evaluates(
      url,
      authKey,
      connTimeout,
      readTimeout,
      resolvers,
      dependencies,
      code)
}
