/*
 *
 *  scala-exercises - evaluator-client
 *  Copyright (C) 2015-2019 47 Degrees, LLC. <http://www.47deg.com>
 *
 */

package org.scalaexercises.evaluator.service
import cats.effect.{Resource, Sync}
import cats.implicits._
import org.http4s.client.Client
import org.http4s.{Header, Method, Request, Uri}
import org.scalaexercises.evaluator._
import org.scalaexercises.evaluator.util.Codecs._

object HttpClientHandler {

  private def headerToken(value: String) = Header("x-scala-eval-api-token", value)

  private val headerContentType = Header("content-type", "application/json")

  def apply[F[_]](uri: String, authString: String, resource: Resource[F, Client[F]])(
      implicit F: Sync[F]): HttpClientService[F] =
    new HttpClientService[F] {
      override def evaluates(evalRequest: EvalRequest): F[EvalResponse] =
        for {
          uri <- F.fromEither(Uri.fromString(uri))
          request = Request[F](Method.POST, uri)
            .withEntity(evalRequest)
            .withHeaders(headerToken(authString), headerContentType)
          result <- resource.use(_.expect[EvalResponse](request))
        } yield result
    }

}
