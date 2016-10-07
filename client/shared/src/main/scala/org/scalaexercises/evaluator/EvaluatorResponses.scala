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

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import fr.hmil.roshttp.HttpResponse
import fr.hmil.roshttp.HeaderMap

object EvaluatorResponses {

  type EvalIO[A] = Free[EvaluatorOp, A]

  type EvaluationResponse[A] = EvaluationException Xor EvaluationResult[A]

  case class EvaluationResult[A](result: A,
                                 statusCode: Int,
                                 headers: Map[String, String])

  sealed abstract class EvaluationException(msg: String,
                                            cause: Option[Throwable] = None)
      extends Throwable(msg) {
    cause foreach initCause
  }

  case class JsonParsingException(msg: String, json: String)
      extends EvaluationException(msg)

  case class UnexpectedException(msg: String) extends EvaluationException(msg)

  def toEntity[A](futureResponse: Future[HttpResponse])(
    implicit D: Decoder[A]): Future[EvaluationResponse[A]] =
    futureResponse map {
      case r if isSuccess(r.statusCode) ⇒
        decode[A](r.body).fold(
          e ⇒
            JsonParsingException(e.getMessage, r.body)
              .left[EvaluationResult[A]],
          result ⇒
            Xor.Right(
              EvaluationResult(result, r.statusCode, r.headers.toLowerCase))
        )
      case r ⇒
        UnexpectedException(
          s"Failed invoking get with status : ${r.statusCode}, body : \n ${r.body}")
          .left[EvaluationResult[A]]
    }

  private[this] def isSuccess(statusCode: Int) =
    statusCode >= 200 && statusCode <= 299

  implicit class HeadersLowerCase[A >: String](headers: HeaderMap[A]) {

    def toLowerCase: Map[String, A] =
      headers.iterator.map(t => (t._1.toLowerCase, t._2)).toList.toMap
  }
}
