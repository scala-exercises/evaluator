/*
 *
 *  scala-exercises - evaluator-client
 *  Copyright (C) 2015-2019 47 Degrees, LLC. <http://www.47deg.com>
 *
 */

package org.scalaexercises.evaluator

import cats.effect.{ConcurrentEffect, Resource}
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.scalaexercises.evaluator.service.{HttpClientHandler, HttpClientService}

import scala.concurrent.ExecutionContext

case class EvaluatorClient(url: String, authKey: String)

object EvaluatorClient {

  private def clientResource[F[_]: ConcurrentEffect]: Resource[F, Client[F]] =
    BlazeClientBuilder[F](ExecutionContext.global).resource

  def apply[F[_]: ConcurrentEffect](url: String, authKey: String): HttpClientService[F] =
    HttpClientHandler[F](url, authKey, clientResource[F])

}
