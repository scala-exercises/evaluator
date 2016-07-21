package org.scalaexercises.evaluator

import java.util.concurrent._

import org.http4s._, org.http4s.dsl._, org.http4s.server._
import org.http4s.server.blaze._
import org.log4s.getLogger

import scala.concurrent.duration._

import scalaz.concurrent.Task
import scalaz._


object services {
  import codecs._
  import io.circe.generic.auto._
  import EvalResponse.messages._

  private val logger = getLogger

  val evaluator = new Evaluator(20 seconds)

  def service = HttpService {
    case req @ POST -> Root / "eval" =>
      import io.circe.syntax._

      req.decode[EvalRequest] { evalRequest =>
        evaluator.eval[Any](
          code = evalRequest.code,
          remotes = evalRequest.resolvers,
          dependencies = evalRequest.dependencies
        ) flatMap { result =>
          val response = result match {
            case EvalSuccess(cis, result, out) =>
              EvalResponse(`ok`, Option(result).map(_.toString), Option(result).map(_.asInstanceOf[AnyRef].getClass.getName), cis)
            case Timeout(_) => EvalResponse(`Timeout Exceded`, None, None, Map.empty)
            case UnresolvedDependency(msg) => EvalResponse(`Unresolved Dependency` + " : " + msg, None, None, Map.empty)
            case EvalRuntimeError(cis, re) => EvalResponse(`Runtime Error` + " : [" + re.error.getClass.getName + "] " + re.error.getMessage, None, None, cis)
            case CompilationError(cis) => EvalResponse(`Compilation Error`, None, None, cis)
            case GeneralError(err) => EvalResponse(`Unforeseen Exception`, None, None, Map.empty)
            case SecurityViolation(explanation) => EvalResponse(`Security Violation` + " : " + explanation, None, None, Map.empty)
          }
          Ok(response.asJson)
        }
      }
  }


  def makeServer(host: String, port: Int) = {
    BlazeBuilder
      .bindHttp(port, host)
      .mountService(service)
      .start
  }
}

object EvaluatorServer extends App {

  import services._

  private[this] val logger = getLogger

  val host = Option(System.getenv("EVALUATOR_SERVER_IP")).getOrElse("0.0.0.0")

  val port = (Option(System.getenv("EVALUATOR_SERVER_PORT")) orElse
    Option(System.getProperty("http.port")))
    .map(_.toInt)
    .getOrElse(8080)

  makeServer(host, port).map(srv => srv.awaitShutdown()).run
}
