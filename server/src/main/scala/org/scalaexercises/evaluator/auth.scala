/*
 *
 *  scala-exercises - evaluator-server
 *  Copyright (C) 2015-2019 47 Degrees, LLC. <http://www.47deg.com>
 *
 */

package org.scalaexercises.evaluator

import cats.effect.Sync
import com.typesafe.config._
import org.http4s._
import org.http4s.syntax.kleisli._
import org.http4s.util._
import org.log4s.getLogger
import pdi.jwt.{Jwt, JwtAlgorithm}

import scala.util.{Failure, Success}

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

    def name: CaseInsensitiveString = CaseInsensitiveString("x-scala-eval-api-token")

    override def parse(s: String): ParseResult[`X-Scala-Eval-Api-Token`] =
      ParseResult.success(`X-Scala-Eval-Api-Token`(s))

    def matchHeader(header: Header): Option[HeaderT] =
      if (header.name == name) Some(`X-Scala-Eval-Api-Token`(header.value))
      else None

  }

  final case class `X-Scala-Eval-Api-Token`(token: String) extends Header.Parsed {
    override def key = `X-Scala-Eval-Api-Token`
    override def renderValue(writer: Writer): writer.type =
      writer.append(token)
  }

  def apply[F[_]: Sync](service: HttpApp[F]): HttpApp[F] =
    HttpRoutes
      .of[F] {
        case req if req.headers.nonEmpty => {
          req.headers.get(`X-Scala-Eval-Api-Token`) match {
            case Some(header) =>
              Jwt.decodeRaw(header.token, secretKey, Seq(JwtAlgorithm.HS256)) match {
                case Success(tokenIdentity) => {
                  logger.info(s"Auth success with identity : $tokenIdentity")
                  service(req)
                }
                case Failure(ex) => {
                  logger.warn(s"Auth failed : $ex")
                  Sync[F].pure(Response(Status.Unauthorized))
                }
              }
            case None => Sync[F].pure(Response(Status.Unauthorized))
          }
        }
        case _ => Sync[F].pure(Response(Status.Unauthorized))
      }
      .orNotFound

}
