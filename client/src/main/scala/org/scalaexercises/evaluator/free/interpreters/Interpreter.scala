/*
 * scala-exercises-evaluator-client
 * Copyright (C) 2015-2016 47 Degrees, LLC. <http://www.47deg.com>
 */

package org.scalaexercises.evaluator.free.interpreters

import cats.{ApplicativeError, Eval, MonadError, ~>}
import org.scalaexercises.evaluator.api.Evaluator
import org.scalaexercises.evaluator.free.algebra.{Evaluates, EvaluatorOp}

import scala.language.higherKinds

trait Interpreter {

  implicit def interpreter[M[_]](
    implicit A: MonadError[M, Throwable]
  ): EvaluatorOp ~> M = evaluatorOpsInterpreter[M]

  /**
    * Lifts Evaluator Ops to an effect capturing Monad such as Task via natural transformations
    */
  def evaluatorOpsInterpreter[M[_]](
    implicit A: ApplicativeError[M, Throwable]): EvaluatorOp ~> M =
    new (EvaluatorOp ~> M) {

      val evaluator = new Evaluator()

      def apply[A](fa: EvaluatorOp[A]): M[A] = fa match {
        case Evaluates(url, authKey, resolvers, dependencies, code) ⇒
          A.pureEval(
            Eval.later(
              evaluator.eval(url, authKey, resolvers, dependencies, code)))
      }

    }
}
