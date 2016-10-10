/*
 * scala-exercises-evaluator-client
 * Copyright (C) 2015-2016 47 Degrees, LLC. <http://www.47deg.com>
 */

package org.scalaexercises.evaluator

import cats.data.EitherT
import cats.~>
import cats.implicits._
import org.scalaexercises.evaluator.EvaluatorResponses.{EvalIO, EvaluationException, EvaluationResponse, EvaluationResult}
import org.scalaexercises.evaluator.free.algebra.EvaluatorOp

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class EvaluatorClient(url: String, authKey: String) {

  lazy val api: EvaluatorAPI[EvaluatorOp] = new EvaluatorAPI(url, authKey)

}

object EvaluatorClient {

  def apply(url: String, authKey: String) =
    new EvaluatorClient(url, authKey)

  implicit class EvaluationIOSyntaxEither[A](
    evalIO: EvalIO[EvaluationResponse[A]]) {

    def exec(
      implicit I: (EvaluatorOp ~> Future)): Future[EvaluationResponse[A]] =
      evalIO foldMap I

    def liftEvaluator: EitherT[EvalIO,
                               EvaluationException,
                               EvaluationResult[A]] =
      EitherT[EvalIO, EvaluationException, EvaluationResult[A]](evalIO)

  }
}
