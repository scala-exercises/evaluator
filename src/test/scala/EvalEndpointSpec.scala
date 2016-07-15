/*
 * scala-exercises-evaluator
 * Copyright (C) 2015-2016 47 Degrees, LLC. <http://www.47deg.com>
 */
package org.scalaexercises.evaluator

import org.scalatest._
import org.http4s._
import org.http4s.headers._
import org.http4s.dsl._
import org.http4s.server._

import io.circe.syntax._
import io.circe.generic.auto._
import scalaz.stream.Process.emit
import java.nio.charset.StandardCharsets
import scodec.bits.ByteVector
import pdi.jwt.{Jwt, JwtAlgorithm, JwtHeader, JwtClaim, JwtOptions}

import org.http4s.{Status => HttpStatus}

class EvalEndpointSpec extends FunSpec with Matchers {

  import services._
  import codecs._
  import auth._
  import EvalResponse.messages._

  val sonatypeReleases = "https://oss.sonatype.org/content/repositories/releases/" :: Nil

  val validToken = Jwt.encode("""{"user":1}""", auth.secretKey, JwtAlgorithm.HS256)

  val invalidToken = java.util.UUID.randomUUID.toString 

  def serve(evalRequest: EvalRequest, authHeader : Header) =
    evalService.run(Request(
      POST,
      Uri(path = "/eval"),
      body = emit(
        ByteVector.view(
          evalRequest.asJson.noSpaces.getBytes(StandardCharsets.UTF_8)
        )
      )
    ).putHeaders(authHeader)).run

  def verifyEvalResponse(
    response: Response,
    expectedStatus: HttpStatus,
    expectedValue: Option[String] = None,
    expectedMessage: String
  ) = {

    response.status should be(expectedStatus)
    val evalResponse = response.as[EvalResponse].run
    evalResponse.value should be(expectedValue)
    evalResponse.msg should be(expectedMessage)
  }

  describe("evaluation") {
    it("can evaluate simple expressions") {
      verifyEvalResponse(
        response = serve(EvalRequest(code = "{ 41 + 1 }"), `X-Scala-Eval-Api-Token`(validToken)),
        expectedStatus = HttpStatus.Ok,
        expectedValue = Some("42"),
        expectedMessage = `ok`
      )
    }

    it("fails with a timeout when takes longer than the configured timeout") {
      verifyEvalResponse(
        response = serve(EvalRequest(code = "{ while(true) {}; 123 }"), `X-Scala-Eval-Api-Token`(validToken)),
        expectedStatus = HttpStatus.Ok,
        expectedValue = None,
        expectedMessage = `Timeout Exceded`
      )
    }

    it("can load dependencies for an evaluation") {
      verifyEvalResponse(
        response = serve(EvalRequest(
          code = "{import cats._; Eval.now(42).value}",
          resolvers = sonatypeReleases,
          dependencies = Dependency("org.typelevel", "cats_2.11", "0.6.0") :: Nil
        ), `X-Scala-Eval-Api-Token`(validToken)),
        expectedStatus = HttpStatus.Ok,
        expectedValue = Some("42"),
        expectedMessage = `ok`
      )
    }

    it("can load different versions of a dependency across evaluations") {
      val code = "{import cats._; Eval.now(42).value}"
      val resolvers = sonatypeReleases

      List("0.6.0", "0.4.1") foreach { version =>
        verifyEvalResponse(
          response = serve(EvalRequest(
            code = code,
            resolvers = resolvers,
            dependencies = Dependency("org.typelevel", "cats_2.11", version) :: Nil
          ), `X-Scala-Eval-Api-Token`(validToken)),
          expectedStatus = HttpStatus.Ok,
          expectedValue = Some("42"),
          expectedMessage = `ok`
        )
      }

    }

    it("can run code from the exercises content") {
      verifyEvalResponse(
        response = serve(EvalRequest(
          code = "{import stdlib._; Asserts.scalaTestAsserts(true)}",
          resolvers = sonatypeReleases,
          dependencies = Dependency("org.scala-exercises", "exercises-stdlib_2.11", "0.2.0") :: Nil
        ), `X-Scala-Eval-Api-Token`(validToken)),
        expectedStatus = HttpStatus.Ok,
        expectedValue = Some("()"),
        expectedMessage = `ok`
      )
    }

    it("captures exceptions when running the exercises content") {
      verifyEvalResponse(
        response = serve(EvalRequest(
          code = "{import stdlib._; Asserts.scalaTestAsserts(false)}",
          resolvers = sonatypeReleases,
          dependencies = Dependency("org.scala-exercises", "exercises-stdlib_2.11", "0.2.0") :: Nil
        ), `X-Scala-Eval-Api-Token`(validToken)),
        expectedStatus = HttpStatus.Ok,
        expectedValue = None,
        expectedMessage = `Runtime Error`
      )
    }

    it("rejects requests with invalid tokens") {
      serve(EvalRequest(
          code = "1",
          resolvers = Nil,
          dependencies =  Nil
        ), `X-Scala-Eval-Api-Token`(invalidToken)).status should be (HttpStatus.Unauthorized)
    }

    it("rejects requests with missing tokens") {
      serve(EvalRequest(
          code = "1",
          resolvers = Nil,
          dependencies =  Nil
        ), `Accept-Ranges`(Nil)).status should be (HttpStatus.Unauthorized)
    }

  }
}

