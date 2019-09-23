/*
 *
 *  scala-exercises - evaluator-client
 *  Copyright (C) 2015-2019 47 Degrees, LLC. <http://www.47deg.com>
 *
 */

package org.scalaexercises.evaluator.util

import cats.effect.Sync
import io.circe.generic.semiauto
import io.circe.generic.semiauto.deriveDecoder
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Encoder, Json}
import org.http4s.circe._
import org.http4s.{EntityDecoder, EntityEncoder}
import org.scalaexercises.evaluator._

object Codecs {

  implicit val decodeRangePosition: Decoder[RangePosition] = deriveDecoder[RangePosition]

  implicit val decodeCompilationInfo: Decoder[CompilationInfo] = deriveDecoder[CompilationInfo]

  implicit val decodeEvalResponse: Decoder[EvalResponse] = deriveDecoder[EvalResponse]

  implicit val encodeEvalRequest: Encoder[EvalRequest] = Encoder.instance(
    req =>
      Json.obj(
        ("resolvers", Json.arr(req.resolvers.map(Json.fromString): _*)),
        ("dependencies", Json.arr(req.dependencies.map(_.asJson): _*)),
        ("code", Json.fromString(req.code))
    ))

  implicit val encodeDependency: Encoder[Dependency] = Encoder.instance(
    dep =>
      Json.obj(
        ("groupId", Json.fromString(dep.groupId)),
        ("artifactId", Json.fromString(dep.artifactId)),
        ("version", Json.fromString(dep.version)),
        ("exclusions", dep.exclusions.asJson)
    ))

  implicit val encodeExclusion: Encoder[Exclusion] = semiauto.deriveEncoder[Exclusion]

  implicit def decoder[F[_]: Sync, A: Decoder]: EntityDecoder[F, A] = jsonOf[F, A]

  implicit def encoder[F[_]: Sync, A: Encoder]: EntityEncoder[F, A] = jsonEncoderOf[F, A]

}
