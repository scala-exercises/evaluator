/*
 * scala-exercises-evaluator-client
 * Copyright (C) 2015-2016 47 Degrees, LLC. <http://www.47deg.com>
 */

package org.scalaexercises.evaluator

import org.scalaexercises.evaluator.free.algebra.{EvaluatorOp, EvaluatorOps}

class EvaluatorAPI(url: String, authKey: String)(
  implicit O: EvaluatorOps[EvaluatorOp]) {

  def evaluates(resolvers: List[String] = Nil,
                dependencies: List[Dependency] = Nil,
                code: String) =
    O.evaluates(url, authKey, resolvers, dependencies, code)
}
