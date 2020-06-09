/*
 * Copyright 2016-2020 47 Degrees Open Source <https://www.47deg.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scalaexercises.evaluator

import cats.effect.IO
import io.circe.generic.auto._
import org.http4s.dsl.io._
import org.http4s.headers._
import org.http4s.{Status => HttpStatus, _}
import org.scalaexercises.evaluator.helper._
import org.scalatest.Assertion
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pdi.jwt.{Jwt, JwtAlgorithm}

class EvalEndpointSpec extends AnyFunSpec with Matchers with Implicits {

  import EvalResponse.messages._
  import auth._
  import codecs._
  import services._

  val validToken: String =
    Jwt.encode("""{"user": "scala-exercises"}""", auth.secretKey, JwtAlgorithm.HS256)

  val invalidToken: String = java.util.UUID.randomUUID.toString

  val evaluator = new Evaluator[IO]

  val server = auth[IO](service[IO](evaluator))

  def serve(evalRequest: EvalRequest, authHeader: Header): Response[IO] =
    server
      .run(
        Request[IO](POST, Uri(path = "/eval"))
          .withEntity(evalRequest)
          .putHeaders(authHeader)
      )
      .unsafeRunSync()

  def verifyEvalResponse(
      response: Response[IO],
      expectedStatus: HttpStatus,
      expectedValue: Option[String] = None,
      expectedMessage: String
  ): Assertion = {

    response.status should be(expectedStatus)
    val evalResponse = response.as[EvalResponse].unsafeRunSync()
    evalResponse.value should be(expectedValue)
    evalResponse.msg should be(expectedMessage)
  }

  describe("evaluation") {
    it("can evaluate simple expressions") {
      verifyEvalResponse(
        response = serve(
          EvalRequest(
            code = "{ 41 + 1 }",
            resolvers = commonResolvers,
            dependencies = scalaDependencies(Scala213)
          ),
          `X-Scala-Eval-Api-Token`(validToken)
        ),
        expectedStatus = HttpStatus.Ok,
        expectedValue = Some("42"),
        expectedMessage = `ok`
      )
    }

    it("fails with a timeout when takes longer than the configured timeout") {
      verifyEvalResponse(
        response = serve(
          EvalRequest(
            code = "{ while(true) {}; 123 }",
            resolvers = commonResolvers,
            dependencies = scalaDependencies(Scala213)
          ),
          `X-Scala-Eval-Api-Token`(validToken)
        ),
        expectedStatus = HttpStatus.Ok,
        expectedValue = None,
        expectedMessage = `Timeout Exceded`
      )
    }

    it("can load dependencies for an evaluation") {
      verifyEvalResponse(
        response = serve(
          EvalRequest(
            code = "{import cats._; Eval.now(42).value}",
            resolvers = commonResolvers,
            dependencies =
              List(Dependency("org.typelevel", "cats-core_2.13", "2.0.0")) ++ scalaDependencies(
                Scala213
              )
          ),
          `X-Scala-Eval-Api-Token`(validToken)
        ),
        expectedStatus = HttpStatus.Ok,
        expectedValue = Some("42"),
        expectedMessage = `ok`
      )
    }

    it("can load different versions of a dependency across evaluations") {
      val code      = "{import cats._; Eval.now(42).value}"
      val resolvers = commonResolvers

      List("2.1.0", "2.0.0") foreach { version =>
        verifyEvalResponse(
          response = serve(
            EvalRequest(
              code = code,
              resolvers = resolvers,
              dependencies =
                List(Dependency("org.typelevel", "cats-core_2.13", version)) ++ scalaDependencies(
                  Scala213
                )
            ),
            `X-Scala-Eval-Api-Token`(validToken)
          ),
          expectedStatus = HttpStatus.Ok,
          expectedValue = Some("42"),
          expectedMessage = `ok`
        )
      }

    }

    it("can run code from the exercises content") {

      verifyEvalResponse(
        response = serve(
          EvalRequest(
            code = exerciseContentCode(true),
            resolvers = commonResolvers,
            dependencies = List(
              Dependency("org.scala-exercises", "exercises-stdlib_2.13", exercisesVersion)
            ) ++ scalaDependencies(Scala213)
          ),
          `X-Scala-Eval-Api-Token`(validToken)
        ),
        expectedStatus = HttpStatus.Ok,
        expectedValue = Some("Succeeded"),
        expectedMessage = `ok`
      )
    }

    it("captures exceptions when running the exercises content") {
      verifyEvalResponse(
        response = serve(
          EvalRequest(
            code = exerciseContentCode(false),
            resolvers = commonResolvers,
            dependencies = List(
              Dependency("org.scala-exercises", "exercises-stdlib_2.13", exercisesVersion)
            ) ++ scalaDependencies(Scala213)
          ),
          `X-Scala-Eval-Api-Token`(validToken)
        ),
        expectedStatus = HttpStatus.Ok,
        expectedValue = Some("true was not false"),
        expectedMessage = `Runtime Error`
      )
    }

    it("rejects requests with invalid tokens") {
      serve(
        EvalRequest(
          code = "1",
          resolvers = Nil,
          dependencies = Nil
        ),
        `X-Scala-Eval-Api-Token`(invalidToken)
      ).status should be(HttpStatus.Unauthorized)
    }

    it("rejects requests with missing tokens") {
      serve(
        EvalRequest(
          code = "1",
          resolvers = Nil,
          dependencies = Nil
        ),
        `Accept-Ranges`(Nil)
      ).status should be(HttpStatus.Unauthorized)
    }

  }
}
