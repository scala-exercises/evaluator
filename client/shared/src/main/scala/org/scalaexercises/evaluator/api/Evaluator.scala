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

import scala.concurrent.Future

class Evaluator {

  import Decoders._

  private val httpClient = new HttpClient

  def eval(url: String,
           authKey: String,
           resolvers: List[String] = Nil,
           dependencies: List[Dependency] = Nil,
           code: String,
           compilerFlags: List[String] = Nil): Future[EvaluationResponse[EvalResponse]] =
    httpClient.post[EvalResponse](
      url = url,
      secretKey = authKey,
      data = EvalRequest(resolvers, dependencies, code, compilerFlags).asJson.noSpaces)

}
