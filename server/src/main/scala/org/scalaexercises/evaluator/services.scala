/*
 * scala-exercises-evaluator-server
 * Copyright (C) 2015-2016 47 Degrees, LLC. <http://www.47deg.com>
 */

package org.scalaexercises.evaluator

import io.circe.Decoder
import org.http4s._
import org.http4s.dsl._
import org.http4s.server._
import org.http4s.server.blaze._
import org.log4s.getLogger
import monix.execution.Scheduler

import scala.language.postfixOps
import scalaz.concurrent.Task
import scalaz._
import scala.concurrent.duration._

object services {

  import codecs._
  import io.circe.generic.auto._
  import EvalResponse.messages._

  private val logger = getLogger

  implicit val scheduler: Scheduler = Scheduler.io("scala-evaluator")

  val evaluator = new Evaluator(20 seconds)

  val corsHeaders = Seq(
    Header("Vary", "Origin,Access-Control-Request-Methods"),
    Header("Access-Control-Allow-Methods", "POST"),
    Header("Access-Control-Allow-Origin", "*"),
    Header(
      "Access-Control-Allow-Headers",
      "x-scala-eval-api-token, Content-Type"),
    Header("Access-Control-Max-Age", 1.day.toSeconds.toString()))

  def evalService =
    auth(HttpService {
      case req @ POST -> Root / "eval" =>
        import io.circe.syntax._

        val decoder: EntityDecoder[EvalRequest] = {
          implicit val evalRequestDecoder: Decoder[EvalRequest] =
            Decoder.instance {
              cursor =>
                for {
                  resolvers <- cursor.downField("resolvers").as[List[String]]
                  dependencies <- cursor
                                   .downField("dependencies")
                                   .as[List[Dependency]]
                  code <- cursor.downField("code").as[String]
                  compilerFlags <- cursor
                                    .downField("compilerFlags")
                                    .as[Option[List[String]]]
                } yield
                  EvalRequest(
                    resolvers,
                    dependencies,
                    code,
                    compilerFlags.getOrElse(Nil))
            }

          jsonDecoderOf(evalRequestDecoder)
        }

        req
          .decodeWith[EvalRequest](decoder, strict = false) {
            evalRequest =>
              evaluator.eval[Any](
                code = evalRequest.code,
                remotes = evalRequest.resolvers,
                dependencies = evalRequest.dependencies,
                compilerFlags = evalRequest.compilerFlags
              ) flatMap {
                (result: EvalResult[_]) =>
                  val response = result match {
                    case EvalSuccess(cis, res, out) =>
                      EvalResponse(
                        `ok`,
                        Option(res.toString),
                        Option(res.asInstanceOf[AnyRef].getClass.getName),
                        cis)
                    case Timeout(_) =>
                      EvalResponse(`Timeout Exceded`, None, None, Map.empty)
                    case UnresolvedDependency(msg) =>
                      EvalResponse(
                        `Unresolved Dependency` + " : " + msg,
                        None,
                        None,
                        Map.empty)
                    case EvalRuntimeError(cis, runtimeError) =>
                      EvalResponse(
                        `Runtime Error`,
                        runtimeError map (_.error.getMessage),
                        runtimeError map (_.error.getClass.getName),
                        cis)
                    case CompilationError(cis) =>
                      EvalResponse(`Compilation Error`, None, None, cis)
                    case GeneralError(err) =>
                      EvalResponse(
                        `Unforeseen Exception`,
                        None,
                        None,
                        Map.empty)
                  }
                  Ok(response.asJson)
              }
          }
          .map((r: Response) => r.putHeaders(corsHeaders: _*))
    })

  def loaderIOService = HttpService {

    case _ -> Root =>
      MethodNotAllowed()

    case GET -> Root / "loaderio-1318d1b3e06b7bc96dd5de5716f57496" =>
      Ok("loaderio-1318d1b3e06b7bc96dd5de5716f57496")
  }

  // CORS middleware in http4s can't be combined with our `auth` middleware. We need to handle CORS calls ourselves.
  def optionsService = HttpService {
    case OPTIONS -> Root / "eval" =>
      Ok().putHeaders(corsHeaders: _*)
  }

}

object EvaluatorServer extends App {

  import services._

  private[this] val logger = getLogger

  val ip = Option(System.getenv("HOST")).getOrElse("0.0.0.0")

  val port = (Option(System.getenv("PORT")) orElse
        Option(System.getProperty("http.port"))).map(_.toInt).getOrElse(8080)

  logger.info(s"Initializing Evaluator at $ip:$port")

  // The order in which services are mounted is really important. They're executed from bottom to top, and they won't be
  // checked for repeated methods or routes. That's why we set the `optionsService` before the `evalService` one, as if
  // not, execution of a OPTIONS call to /eval would lead to `evalService`, even if that service doesn't recognize the
  // OPTIONS verb.
  BlazeBuilder
    .bindHttp(port, ip)
    .mountService(evalService)
    .mountService(optionsService)
    .mountService(loaderIOService)
    .start
    .run
    .awaitShutdown()

}
