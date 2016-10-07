/*
 * scala-exercises-evaluator-client
 * Copyright (C) 2015-2016 47 Degrees, LLC. <http://www.47deg.com>
 */

package org.scalaexercises.evaluator.free.algebra

import cats.free.{Free, Inject}
import org.scalaexercises.evaluator.{Dependency, EvalResponse}
import org.scalaexercises.evaluator.EvaluatorResponses.EvaluationResponse

sealed trait EvaluatorOp[A]
final case class Evaluates(url: String,
                           authKey: String,
                           resolvers: List[String] = Nil,
                           dependencies: List[Dependency] = Nil,
                           code: String)
    extends EvaluatorOp[EvaluationResponse[EvalResponse]]

class EvaluatorOps[F[_]](implicit I: Inject[EvaluatorOp, F]) {

  def evaluates(
    url: String,
    authKey: String,
    resolvers: List[String] = Nil,
    dependencies: List[Dependency] = Nil,
    code: String
  ): Free[F, EvaluationResponse[EvalResponse]] =
    Free.inject[EvaluatorOp, F](
      Evaluates(url, authKey, resolvers, dependencies, code))

}

object EvaluatorOps {

  implicit def instance[F[_]](
    implicit I: Inject[EvaluatorOp, F]): EvaluatorOps[F] = new EvaluatorOps[F]

}
