/*
 * scala-exercises-evaluator-client
 * Copyright (C) 2015-2016 47 Degrees, LLC. <http://www.47deg.com>
 */

package org.scalaexercises.evaluator.http

import io.circe.Decoder
import org.scalaexercises.evaluator.EvaluatorResponses
import org.scalaexercises.evaluator.EvaluatorResponses.EvaluationResponse

import scala.concurrent.duration._
import scala.concurrent.duration.Duration

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
    connTimeout: Duration = 1.second,
    readTimeout: Duration = 10.seconds,
    headers: Headers = Map.empty,
    data: String
  )(implicit D: Decoder[A]): EvaluationResponse[A] =
    EvaluatorResponses.toEntity(
      HttpRequestBuilder(
        url = url,
        httpVerb = method,
        connTimeout = connTimeout,
        readTimeout = readTimeout)
        .withHeaders(headers + (authHeaderName -> secretKey))
        .withBody(data)
        .run)
}
