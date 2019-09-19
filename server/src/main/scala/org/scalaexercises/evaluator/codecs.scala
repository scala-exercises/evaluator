/*
 *
 *  scala-exercises - evaluator-server
 *  Copyright (C) 2015-2019 47 Degrees, LLC. <http://www.47deg.com>
 *
 */

package org.scalaexercises.evaluator

import cats.effect.Sync
import io.circe.{Decoder, Encoder}
import org.http4s._
import org.http4s.circe._

/** Provides Json serialization codecs for the http4s services */
trait Http4sCodecInstances {

  implicit def entityDecoderOf[F[_]: Sync, A: Decoder]: EntityDecoder[F, A] = jsonOf[F, A]

  implicit def entityEncoderOf[F[_]: Sync, A: Encoder]: EntityEncoder[F, A] = jsonEncoderOf[F, A]

}

object codecs extends Http4sCodecInstances
