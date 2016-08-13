/*
 * scala-exercises-evaluator-server
 * Copyright (C) 2015-2016 47 Degrees, LLC. <http://www.47deg.com>
 */

package org.scalaexercises.evaluator

import org.http4s._, org.http4s.dsl._, org.http4s.server._
import org.http4s.server.blaze._
import org.log4s.getLogger

import monix.execution.Scheduler

import scala.concurrent.duration._
import scala.language.postfixOps

import scalaz.concurrent.Task
import scalaz._

object services {

  import codecs._
  import io.circe.generic.auto._
  import EvalResponse.messages._

  private val logger = getLogger

  implicit val scheduler: Scheduler = Scheduler.io("scala-evaluator")

  val evaluator = new Evaluator(20 seconds)

  def evalService =
    auth(HttpService {
      case req @ POST -> Root / "eval" =>
        import io.circe.syntax._
        req.decode[EvalRequest] {
          evalRequest =>
            evaluator.eval[Any](
              code = evalRequest.code,
              remotes = evalRequest.resolvers,
              dependencies = evalRequest.dependencies
            ) flatMap {
              result =>
                val response = result match {
                  case EvalSuccess(cis, result, out) =>
                    EvalResponse(
                      `ok`,
                      Option(result.toString),
                      Option(result.asInstanceOf[AnyRef].getClass.getName),
                      cis)
                  case Timeout(_) =>
                    EvalResponse(`Timeout Exceded`, None, None, Map.empty)
                  case UnresolvedDependency(msg) =>
                    EvalResponse(
                      `Unresolved Dependency` + " : " + msg,
                      None,
                      None,
                      Map.empty)
                  case EvalRuntimeError(cis, _) =>
                    EvalResponse(`Runtime Error`, None, None, cis)
                  case CompilationError(cis) =>
                    EvalResponse(`Compilation Error`, None, None, cis)
                  case GeneralError(err) =>
                    EvalResponse(`Unforeseen Exception`, None, None, Map.empty)
                }
                Ok(response.asJson)
            }
        }
    })

  def loaderIOService = HttpService {

    case _ -> Root =>
      MethodNotAllowed()

    case GET -> Root / "loaderio-1318d1b3e06b7bc96dd5de5716f57496" =>
      Ok("loaderio-1318d1b3e06b7bc96dd5de5716f57496")
  }

}

object EvaluatorServer extends App {

  import services._

  private[this] val logger = getLogger

  val ip = Option(System.getenv("HOST")).getOrElse("0.0.0.0")

  val port = (Option(System.getenv("PORT")) orElse
        Option(System.getProperty("http.port"))).map(_.toInt).getOrElse(8080)

  logger.info(s"Initializing Evaluator at $ip:$port")

  BlazeBuilder
    .bindHttp(port, ip)
    .mountService(evalService)
    .mountService(loaderIOService)
    .start
    .run
    .awaitShutdown()

}
