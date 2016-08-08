/*
 * scala-exercises-evaluator-client
 * Copyright (C) 2015-2016 47 Degrees, LLC. <http://www.47deg.com>
 */

package org.scalaexercises.evaluator

import cats.std.FutureInstances
import cats.std.future._
import cats.{Eval, MonadError}
import org.scalaexercises.evaluator.free.interpreters.Interpreter

object implicits
    extends Interpreter
    with EvalInstances
    with FutureInstances

trait EvalInstances {

  implicit val evalMonadError: MonadError[Eval, Throwable] =
    new MonadError[Eval, Throwable] {

      override def pure[A](x: A): Eval[A] = Eval.now(x)

      override def map[A, B](fa: Eval[A])(f: A ⇒ B): Eval[B] = fa.map(f)

      override def flatMap[A, B](fa: Eval[A])(ff: A ⇒ Eval[B]): Eval[B] =
        fa.flatMap(ff)

      override def raiseError[A](e: Throwable): Eval[A] =
        Eval.later({ throw e })

      override def handleErrorWith[A](fa: Eval[A])(
        f: Throwable ⇒ Eval[A]): Eval[A] =
        Eval.later({
          try {
            fa.value
          } catch {
            case e: Throwable ⇒ f(e).value
          }
        })
    }

}
