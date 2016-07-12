package org.scalaexercises.evaluator

import org.http4s._, org.http4s.dsl._, org.http4s.server._
import org.http4s.server.blaze._
import org.log4s.getLogger

import scalaz.concurrent.Task

object services {

  private val logger = getLogger

  val service = HttpService {
    case GET -> Root / "eval" =>
      Ok(s"Hello, evaluator!.")
  }

}

object EvaluatorServer extends App {

  import services._

  private[this] val logger = getLogger

  val ip = Option(System.getenv("EVALUATOR_SERVER_IP")).getOrElse("0.0.0.0")

  val port = (Option(System.getenv("EVALUATOR_SERVER_PORT")) orElse
              Option(System.getenv("HTTP_PORT")))
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
