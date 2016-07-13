package org.scalaexercises.evaluator

import org.http4s._, org.http4s.dsl._, org.http4s.server._
import org.http4s.server.blaze._
import org.log4s.getLogger

import monix.execution.Scheduler

import scala.concurrent.duration._

import scalaz.concurrent.Task
import scalaz._

object services {

  import codecs._
  import io.circe.generic.auto._

  private val logger = getLogger

  implicit val scheduler: Scheduler = Scheduler.io("scala-evaluator")

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
              EvalResponse("ok", Option(result.toString), Option(result.asInstanceOf[AnyRef].getClass.getName), cis)
            case Timeout(_) => EvalResponse("Timeout", None, None, Map.empty)
            case UnresolvedDependency(msg) => EvalResponse(s"Unresolved Dependency : $msg", None, None, Map.empty)
            case EvalRuntimeError(cis, _) => EvalResponse("Runtime error", None, None, cis)
            case CompilationError(cis) => EvalResponse("Compilation Error", None, None, cis)
            case GeneralError(err) => EvalResponse("Unforeseen Exception", None, None, Map.empty)
          }
          Ok(response.asJson)
        }
      }
  }

}

object EvaluatorServer extends App {

  import services._

  private[this] val logger = getLogger

  val ip = Option(System.getenv("EVALUATOR_SERVER_IP")).getOrElse("0.0.0.0")

  val port = (Option(System.getenv("EVALUATOR_SERVER_PORT")) orElse
    Option(System.getProperty("http.port")))
    .map(_.toInt)
    .getOrElse(8080)

  logger.info(s"Initializing Evaluator at $ip:$port")

  BlazeBuilder
    .bindHttp(port, ip)
    .mountService(service)
    .start
    .run
    .awaitShutdown()

}
