/*
 * scala-exercises-evaluator-client
 * Copyright (C) 2015-2016 47 Degrees, LLC. <http://www.47deg.com>
 */

package org.scalaexercises.evaluator

import cats.data.XorT
import cats.{MonadError, ~>}
import org.scalaexercises.evaluator.EvaluatorResponses.{EvaluationException, EvaluationResponse, EvaluationResult, EvalIO}
import org.scalaexercises.evaluator.free.algebra.EvaluatorOp

class EvaluatorClient(url: String, authKey: String) {

  lazy val api = new EvaluatorAPI(url, authKey)

}

object EvaluatorClient {

  def apply(url: String, authKey: String) = new EvaluatorClient(url, authKey)

  implicit class EvaluationIOSyntaxXOR[A](
    evalIO: EvalIO[EvaluationResponse[A]]) {

    def exec[M[_]](implicit I: (EvaluatorOp ~> M),
                   A: MonadError[M, Throwable]): M[EvaluationResponse[A]] =
      evalIO foldMap I

    def liftEvaluator: XorT[EvalIO, EvaluationException, EvaluationResult[A]] =
      XorT[EvalIO, EvaluationException, EvaluationResult[A]](evalIO)

  }
}
