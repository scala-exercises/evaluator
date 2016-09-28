/*
 * scala-exercises-evaluator-client
 * Copyright (C) 2015-2016 47 Degrees, LLC. <http://www.47deg.com>
 */

package org.scalaexercises.evaluator

import cats.data.Xor
import cats.free.Free
import cats.syntax.xor._
import io.circe.Decoder
import io.circe.parser._
import io.circe.generic.auto._
import org.scalaexercises.evaluator.free.algebra.EvaluatorOp

import scala.language.higherKinds
import scalaj.http.HttpResponse

object EvaluatorResponses {

  type EvalIO[A] = Free[EvaluatorOp, A]

  type EvaluationResponse[A] = EvaluationException Xor EvaluationResult[A]

  case class EvaluationResult[A](result: A,
                                 statusCode: Int,
                                 headers: Map[String, IndexedSeq[String]])

  sealed abstract class EvaluationException(msg: String,
                                            cause: Option[Throwable] = None)
      extends Throwable(msg) {
    cause foreach initCause
  }

  case class JsonParsingException(msg: String, json: String)
      extends EvaluationException(msg)

  case class UnexpectedException(msg: String) extends EvaluationException(msg)

  def toEntity[A](response: HttpResponse[String])(
    implicit D: Decoder[A]): EvaluationResponse[A] = response match {
    case r if r.isSuccess ⇒
      decode[A](r.body).fold(
        e ⇒
          JsonParsingException(e.getMessage, r.body).left[EvaluationResult[A]],
        result ⇒
          Xor.Right(EvaluationResult(result, r.code, r.headers.toLowerCase))
      )
    case r ⇒
      UnexpectedException(
        s"Failed invoking get with status : ${r.code}, body : \n ${r.body}")
        .left[EvaluationResult[A]]
  }

  implicit class HeadersLowerCase[A](headers: Map[String, A]) {

    def toLowerCase: Map[String, A] = headers.map(e ⇒ (e._1.toLowerCase, e._2))

  }
}
