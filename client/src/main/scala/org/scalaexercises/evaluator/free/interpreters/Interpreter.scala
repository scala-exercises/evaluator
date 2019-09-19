/*
 *
 *  scala-exercises - evaluator-client
 *  Copyright (C) 2015-2019 47 Degrees, LLC. <http://www.47deg.com>
 *
 */

package org.scalaexercises.evaluator.free.interpreters

import cats.~>
import org.scalaexercises.evaluator.api.Evaluator
import org.scalaexercises.evaluator.free.algebra.{Evaluates, EvaluatorOp}

import scala.concurrent.Future

trait Interpreter {

  /**
   * Lifts Evaluator Ops to an effect capturing Monad such as Task via natural transformations
   */
  implicit def evaluatorOpsInterpreter: EvaluatorOp ~> Future =
    new (EvaluatorOp ~> Future) {

      val evaluator = new Evaluator()

      def apply[A](fa: EvaluatorOp[A]): Future[A] = fa match {
        case Evaluates(url, authKey, resolvers, dependencies, code) ⇒
          evaluator.eval(url, authKey, resolvers, dependencies, code)
      }

    }
}
