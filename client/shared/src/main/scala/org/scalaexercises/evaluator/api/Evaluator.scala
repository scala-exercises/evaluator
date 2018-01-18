/*
 * scala-exercises - evaluator-client
 * Copyright (C) 2015-2016 47 Degrees, LLC. <http://www.47deg.com>
 */

package org.scalaexercises.evaluator.api

import io.circe.generic.auto._
import io.circe.syntax._
import org.scalaexercises.evaluator.EvaluatorResponses.EvaluationResponse
import org.scalaexercises.evaluator.http.{HttpClient, HttpClientLike}
import org.scalaexercises.evaluator.{Decoders, Dependency, EvalRequest, EvalResponse}

import scala.concurrent.Future

trait EvaluatorLike {

  import Decoders._

  val httpClient: HttpClientLike[EvalResponse] = new HttpClient

  def eval(
      url: String,
      authKey: String,
      resolvers: List[String] = Nil,
      dependencies: List[Dependency] = Nil,
      code: String): Future[EvaluationResponse[EvalResponse]] =
    httpClient.post(
      url = url,
      secretKey = authKey,
      data = EvalRequest(resolvers, dependencies, code).asJson.noSpaces)

}

class Evaluator extends EvaluatorLike