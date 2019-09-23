/*
 *
 *  scala-exercises - evaluator-client
 *  Copyright (C) 2015-2019 47 Degrees, LLC. <http://www.47deg.com>
 *
 */

package org.scalaexercises.evaluator.service

import org.scalaexercises.evaluator.{EvalRequest, EvalResponse}

trait HttpClientService[F[_]] {

  def evaluates(evalRequest: EvalRequest): F[EvalResponse]

}
