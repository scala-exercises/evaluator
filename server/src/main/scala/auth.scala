package org.scalaexercises.evaluator

import org.http4s._, org.http4s.dsl._, org.http4s.server._
import com.typesafe.config._
import org.http4s.util._
import scala.util.{Try, Success, Failure}
import pdi.jwt.{Jwt, JwtAlgorithm, JwtHeader, JwtClaim, JwtOptions}

import org.log4s.getLogger

import scalaz.concurrent.Task

object auth {

  private[this] val logger = getLogger

  val config = ConfigFactory.load()

  val SecretKeyPath = "eval.auth.secretKey"

  val secretKey = if (config.hasPath(SecretKeyPath)) {
    config.getString(SecretKeyPath)
  } else {
    throw new IllegalStateException(
      "Missing -Deval.auth.secretKey=[YOUR_KEY_HERE] or env var [EVAL_SECRET_KEY] ")
  }

  def generateToken(value: String = "{}") =
    Jwt.encode(value, secretKey, JwtAlgorithm.HS256)

  object `X-Scala-Eval-Api-Token` extends HeaderKey.Singleton {

    type HeaderT = `X-Scala-Eval-Api-Token`

    def name: CaseInsensitiveString = "x-scala-eval-api-token".ci

    override def parse(s: String): ParseResult[`X-Scala-Eval-Api-Token`] =
      ParseResult.success(`X-Scala-Eval-Api-Token`(s))

    def matchHeader(header: Header): Option[HeaderT] = {
      if (header.name == name) Some(`X-Scala-Eval-Api-Token`(header.value))
      else None
    }

  }

  final case class `X-Scala-Eval-Api-Token`(token: String)
      extends Header.Parsed {
    override def key = `X-Scala-Eval-Api-Token`
    override def renderValue(writer: Writer): writer.type =
      writer.append(token)
  }

  def apply(service: HttpService): HttpService = Service.lift { req =>
    req.headers.get(`X-Scala-Eval-Api-Token`) match {
      case Some(header) =>
        Jwt.decodeRaw(header.value, secretKey, Seq(JwtAlgorithm.HS256)) match {
          case Success(tokenIdentity) =>
            logger.info(s"Auth success with identity : $tokenIdentity")
            service(req)
          case Failure(ex) =>
            logger.warn(s"Auth failed : $ex")
            Task.now(Response(Status.Unauthorized))
        }
      case None => Task.now(Response(Status.Unauthorized))
    }

  }

}
