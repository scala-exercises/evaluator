package org.scalaexercises.evaluator

import org.http4s._, org.http4s.dsl._
import io.circe.{Encoder, Decoder, Json, Printer}
import org.http4s.headers.`Content-Type`
import io.circe.jawn.CirceSupportParser.facade

/** Provides Json serialization codecs for the http4s services */
trait Http4sCodecInstances {

  implicit val jsonDecoder: EntityDecoder[Json] = jawn.jawnDecoder(facade)

  implicit def jsonDecoderOf[A](implicit decoder: Decoder[A]): EntityDecoder[A] =
    jsonDecoder.flatMapR { json =>
      decoder.decodeJson(json).fold(
        failure =>
          DecodeResult.failure(InvalidMessageBodyFailure(s"Could not decode JSON: $json", Some(failure))),
        DecodeResult.success(_)
      )
    }

  implicit val jsonEntityEncoder: EntityEncoder[Json] =
    EntityEncoder[String].contramap[Json] { json =>
      Printer.noSpaces.pretty(json)
    }.withContentType(`Content-Type`(MediaType.`application/json`))

  implicit def jsonEncoderOf[A](implicit encoder: Encoder[A]): EntityEncoder[A] =
    jsonEntityEncoder.contramap[A](encoder.apply)

}

object codecs extends Http4sCodecInstances
