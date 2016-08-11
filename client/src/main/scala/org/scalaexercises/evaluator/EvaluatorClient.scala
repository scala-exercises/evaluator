/*
 * scala-exercises-evaluator-client
 * Copyright (C) 2015-2016 47 Degrees, LLC. <http://www.47deg.com>
 */

package org.scalaexercises.evaluator

import cats.data.XorT
import cats.{MonadError, ~>}
import org.scalaexercises.evaluator.EvaluatorResponses.{EvalIO, EvaluationException, EvaluationResponse, EvaluationResult}
import org.scalaexercises.evaluator.free.algebra.EvaluatorOp

import scala.concurrent.duration._
import scala.concurrent.duration.Duration

class EvaluatorClient(url: String,
                      authKey: String,
                      connTimeout: Duration = 1.second,
                      readTimeout: Duration = 10.seconds) {

  lazy val api = new EvaluatorAPI(url, authKey, connTimeout, readTimeout)

}

object EvaluatorClient {

  def apply(url: String,
            authKey: String,
            connTimeout: Duration = 1.second,
            readTimeout: Duration = 10.seconds) =
    new EvaluatorClient(url, authKey, connTimeout, readTimeout)

  implicit class EvaluationIOSyntaxXOR[A](
    evalIO: EvalIO[EvaluationResponse[A]]) {

    def exec[M[_]](implicit I: (EvaluatorOp ~> M),
                   A: MonadError[M, Throwable]): M[EvaluationResponse[A]] =
      evalIO foldMap I

    def liftEvaluator: XorT[EvalIO, EvaluationException, EvaluationResult[A]] =
      XorT[EvalIO, EvaluationException, EvaluationResult[A]](evalIO)

  }
}
