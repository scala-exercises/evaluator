/*
 *
 *  scala-exercises - evaluator-client
 *  Copyright (C) 2015-2019 47 Degrees, LLC. <http://www.47deg.com>
 *
 */

package org.scalaexercises.evaluator

import io.circe._, io.circe.jawn._, io.circe.syntax._

object Decoders {

  implicit val decodeRangePosition: Decoder[RangePosition] =
    Decoder.forProduct3("start", "point", "end")(RangePosition.apply)

  implicit val decodeCompilationInfo: Decoder[CompilationInfo] =
    Decoder.forProduct2("message", "pos")(CompilationInfo.apply)

  implicit val decodeEvalResponse: Decoder[EvalResponse] =
    Decoder.forProduct5("msg", "value", "valueType", "consoleOutput", "compilationInfos")(
      EvalResponse.apply)
}
