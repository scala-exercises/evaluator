/*
 * scala-exercises-evaluator-client
 * Copyright (C) 2015-2016 47 Degrees, LLC. <http://www.47deg.com>
 */

package org.scalaexercises.evaluator.http

import io.circe.Decoder
import org.scalaexercises.evaluator.EvaluatorResponses
import org.scalaexercises.evaluator.EvaluatorResponses.EvaluationResponse
import scala.concurrent.Future

object HttpClient {

  val authHeaderName = "x-scala-eval-api-token"
  type Headers = Map[String, String]

}

class HttpClient {

  import HttpClient._
  def post[A](
    url: String,
    secretKey: String,
    method: String = "post",
    headers: Headers = Map.empty,
    data: String
  )(implicit D: Decoder[A]): Future[EvaluationResponse[A]] =
    EvaluatorResponses.toEntity(
      HttpRequestBuilder(url = url, httpVerb = method)
        .withHeaders(headers + (authHeaderName -> secretKey))
        .withBody(data)
        .run)
}
