/*
 *
 *  scala-exercises - evaluator-server
 *  Copyright (C) 2015-2019 47 Degrees, LLC. <http://www.47deg.com>
 *
 */

package org.scalaexercises.evaluator

import cats.effect.IO
import coursier.util.Sync
import coursier.interop.cats._

import scala.concurrent.ExecutionContext

trait Implicits {

  val EC = ExecutionContext.fromExecutor(new java.util.concurrent.ForkJoinPool(10))

  implicit val timer = IO.timer(EC)

  implicit val CS = IO.contextShift(EC)

  implicit val sync = Sync[IO]

}
