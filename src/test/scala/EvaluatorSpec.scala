/*
 * scala-exercises-evaluator
 * Copyright (C) 2015-2016 47 Degrees, LLC. <http://www.47deg.com>
 */

package org.scalaexercises.evaluator

import scala.concurrent.duration._
import org.scalatest._

class EvaluatorSpec extends FunSpec with Matchers {
  val evaluator = new Evaluator(1 second)

  describe("evaluation") {
    it("can evaluate simple expressions") {
      val result: EvalResult[Int] = evaluator.eval("{ 41 + 1 }")
      result should matchPattern {
        case EvalResult.Success(_, 42, _) ⇒
      }
    }

    it("fails with a timeout when takes longer than the configured timeout") {
      val result: EvalResult[Int] = evaluator.eval("{ while(true) {}; 123 }")
      result should matchPattern {
        case t: EvalResult.Timeout[Int] ⇒
      }
    }
  }
}
