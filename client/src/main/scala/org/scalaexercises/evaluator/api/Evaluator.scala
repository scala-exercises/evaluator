/*
 * scala-exercises-evaluator-client
 * Copyright (C) 2015-2016 47 Degrees, LLC. <http://www.47deg.com>
 */

package org.scalaexercises.evaluator.api

import org.scalaexercises.evaluator.EvaluatorResponses.EvaluationResponse
import org.scalaexercises.evaluator.{Decoders, Dependency, EvalRequest, EvalResponse}
import org.scalaexercises.evaluator.http.HttpClient
import io.circe.generic.auto._
import io.circe.syntax._

import scala.concurrent.duration.Duration

class Evaluator {

  import Decoders._

  private val httpClient = new HttpClient

  def eval(url: String,
           authKey: String,
           connTimeout: Duration,
           readTimeout: Duration,
           resolvers: List[String] = Nil,
           dependencies: List[Dependency] = Nil,
           code: String): EvaluationResponse[EvalResponse] =
    httpClient.post[EvalResponse](
      url = url,
      secretKey = authKey,
      connTimeout = connTimeout,
      readTimeout = readTimeout,
      data = EvalRequest(resolvers, dependencies, code).asJson.noSpaces)

}