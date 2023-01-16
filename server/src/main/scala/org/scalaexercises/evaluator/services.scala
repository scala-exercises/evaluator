/*
 * Copyright 2016-2020 47 Degrees Open Source <https://www.47deg.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scalaexercises.evaluator

import cats.effect.{ConcurrentEffect, ContextShift, ExitCode, IO, IOApp, Timer}
import cats.implicits._
import coursier.interop.cats._
import coursier.util.Sync
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s._
import org.http4s.dsl._
import org.http4s.headers.Allow
import org.http4s.server.blaze._
import org.http4s.syntax.kleisli.http4sKleisliResponseSyntaxOptionT
import org.log4s.getLogger
import org.scalaexercises.evaluator.codecs._

import scala.concurrent.duration._

object services {

  import EvalResponse.messages._

  val corsHeaders = Seq(
    Header("Vary", "Origin,Access-Control-Request-Methods"),
    Header("Access-Control-Allow-Methods", "POST"),
    Header("Access-Control-Allow-Origin", "*"),
    Header("Access-Control-Allow-Headers", "x-scala-eval-api-token, Content-Type"),
    Header("Access-Control-Max-Age", 1.day.toSeconds.toString())
  )

  def service[F[_]: ConcurrentEffect: ContextShift: Timer: Sync](evaluator: Evaluator[F]) = {

    object dsl extends Http4sDsl[F]

    import dsl._

    HttpRoutes
      .of[F] {
        // Evaluator service
        case req @ POST -> Root / "eval" =>
          req
            .decode[EvalRequest] { evalRequest =>
              evaluator
                .eval[Any](
                  code = evalRequest.code,
                  remotes = evalRequest.resolvers,
                  dependencies = evalRequest.dependencies
                )
                .flatMap { (result: EvalResult[_]) =>
                  val response = result match {
                    case EvalSuccess(cis, res, out) =>
                      EvalResponse(
                        `ok`,
                        Option(res.toString),
                        Option(res.asInstanceOf[AnyRef].getClass.getName),
                        Option(out),
                        cis
                      )
                    case Timeout(_) =>
                      EvalResponse(`Timeout Exceded`, None, None, None, Map.empty)
                    case UnresolvedDependency(msg) =>
                      EvalResponse(
                        `Unresolved Dependency` + " : " + msg,
                        None,
                        None,
                        None,
                        Map.empty
                      )
                    case EvalRuntimeError(cis, runtimeError) =>
                      EvalResponse(
                        `Runtime Error`,
                        runtimeError map (_.error.getMessage),
                        runtimeError map (_.error.getClass.getName),
                        None,
                        cis
                      )
                    case CompilationError(cis) =>
                      EvalResponse(`Compilation Error`, None, None, None, cis)
                    case GeneralError(err) =>
                      EvalResponse(`Unforeseen Exception`, None, None, None, Map.empty)
                  }
                  Ok(response.asJson)
                }
            }
            .map((r: Response[F]) => r.putHeaders(corsHeaders: _*))
        // LoaderIO service
        case _ -> Root => MethodNotAllowed(Allow(GET, POST, OPTIONS))
        case GET -> Root / "loaderio-1318d1b3e06b7bc96dd5de5716f57496" =>
          Ok("loaderio-1318d1b3e06b7bc96dd5de5716f57496")
        // Options service
        // CORS middleware in http4s can't be combined with our `auth` middleware. We need to handle CORS calls ourselves.
        case OPTIONS -> Root / "eval" =>
          Ok().map(res => res.withHeaders(corsHeaders: _*)) // putHeaders(corsHeaders: _*)
      }
      .orNotFound
  }

}

object EvaluatorServer extends IOApp {

  import services._

  private[this] val logger = getLogger

  lazy val ip = Option(System.getenv("HOST")).getOrElse("0.0.0.0")

  lazy val port = (Option(System.getenv("PORT")) orElse
    Option(System.getProperty("http.port"))).map(_.toInt).getOrElse(8080)

  val httpApp = auth[IO](service(new Evaluator[IO](10.seconds)))

  override def run(args: List[String]): IO[ExitCode] = {
    logger.info(s"Initializing Evaluator at $ip:$port")

    BlazeServerBuilder[IO]
      .bindHttp(port, ip)
      .withHttpApp(httpApp)
      .resource
      .use(_ => IO.never)
      .as(ExitCode.Success)
  }
}
