/*
 * scala-exercises-evaluator-client
 * Copyright (C) 2015-2016 47 Degrees, LLC. <http://www.47deg.com>
 */

package org.scalaexercises.evaluator.api

import org.scalaexercises.evaluator.EvaluatorResponses.EvaluationResponse
import org.scalaexercises.evaluator.{Decoders, EvalRequest, EvalResponse}
import org.scalaexercises.evaluator.http.HttpClient
import io.circe.generic.auto._
import io.circe.syntax._

class Evaluator {

  import Decoders._

  private val httpClient = new HttpClient

  def eval(url: String,
           authKey: String,
           evalRequest: EvalRequest): EvaluationResponse[EvalResponse] =
    httpClient
      .post[EvalResponse](url, authKey, data = evalRequest.asJson.noSpaces)

}
