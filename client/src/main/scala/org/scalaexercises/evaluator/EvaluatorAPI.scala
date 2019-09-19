/*
 * scala-exercises - evaluator-client
 * Copyright (C) 2015-2016 47 Degrees, LLC. <http://www.47deg.com>
 */

package org.scalaexercises.evaluator

import cats.free.Free
import org.scalaexercises.evaluator.EvaluatorResponses.EvaluationResponse
import org.scalaexercises.evaluator.free.algebra.EvaluatorOps

class EvaluatorAPI[F[_]](url: String, authKey: String)(implicit O: EvaluatorOps[F]) {

  def evaluates(
      resolvers: List[String] = Nil,
      dependencies: List[Dependency] = Nil,
      code: String): Free[F, EvaluationResponse[EvalResponse]] =
    O.evaluates(url, authKey, resolvers, dependencies, code)
}
