/*
 * scala-exercises-evaluator-client
 * Copyright (C) 2015-2016 47 Degrees, LLC. <http://www.47deg.com>
 */

package org.scalaexercises.evaluator

import cats.std.FutureInstances
import cats.std.future._
import cats.{Eval, Id, Monad, MonadError}
import org.scalaexercises.evaluator.free.interpreters.Interpreter

object implicits
    extends Interpreter
    with EvalInstances
    with IdInstances
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

trait IdInstances {

  implicit def idMonadError(implicit I: Monad[Id]): MonadError[Id, Throwable] =
    new MonadError[Id, Throwable] {

      override def pure[A](x: A): Id[A] = I.pure(x)

      override def ap[A, B](ff: Id[A ⇒ B])(fa: Id[A]): Id[B] = I.ap(ff)(fa)

      override def map[A, B](fa: Id[A])(f: Id[A ⇒ B]): Id[B] = I.map(fa)(f)

      override def flatMap[A, B](fa: Id[A])(f: A ⇒ Id[B]): Id[B] =
        I.flatMap(fa)(f)

      override def raiseError[A](e: Throwable): Id[A] = throw e

      override def handleErrorWith[A](fa: Id[A])(f: Throwable ⇒ Id[A]): Id[A] = {
        try {
          fa
        } catch {
          case e: Exception ⇒ f(e)
        }
      }
    }

}
