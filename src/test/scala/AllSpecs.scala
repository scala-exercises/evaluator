/*
 * scala-exercises-evaluator
 * Copyright (C) 2015-2016 47 Degrees, LLC. <http://www.47deg.com>
 */

package org.scalaexercises.evaluator

import org.scalatest._

import java.security.Policy

class AllSpecs extends Suites(
  new EvaluatorSpec,
  new EvalEndpointSpec,
  new EvaluatorSecuritySpec
) with BeforeAndAfter {
  before {
    Eval.enableSandbox
  }
}
