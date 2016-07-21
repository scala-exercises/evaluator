/*
 * scala-exercises-evaluator
 * Copyright (C) 2015-2016 47 Degrees, LLC. <http://www.47deg.com>
 */
package org.scalaexercises.evaluator

import scala.util.matching.Regex

import org.scalatest._
import org.http4s._, org.http4s.dsl._, org.http4s.server._

import io.circe.syntax._
import io.circe.generic.auto._
import scalaz.stream.Process.emit
import java.nio.charset.StandardCharsets
import scodec.bits.ByteVector

import org.http4s.{Status => HttpStatus}

class EvalEndpointSpec extends FunSpec with Matchers {
  import services._
  import codecs._
  import EvalResponse.messages._

  val sonatypeReleases = "https://oss.sonatype.org/content/repositories/releases/" :: Nil

  def serve(evalRequest: EvalRequest) =
    service.run(Request(
      POST,
      Uri(path = "/eval"),
      body = emit(
        ByteVector.view(
          evalRequest.asJson.noSpaces.getBytes(StandardCharsets.UTF_8)
        )
      )
    )).run

  def verifyEvalResponse(
    response: Response,
    expectedStatus: HttpStatus,
    expectedValue: Option[String] = None,
    expectedMessage: Regex
  ) = {

    response.status should be(expectedStatus)
    val evalResponse = response.as[EvalResponse].run
    evalResponse.value should be(expectedValue)
    evalResponse.msg should include regex expectedMessage
  }

  describe("evaluation endpoint") {
    it("can evaluate simple expressions") {
      verifyEvalResponse(
        response = serve(EvalRequest(
          code = "{ 41 + 1 }",
          resolvers = sonatypeReleases
        )),
        expectedStatus = HttpStatus.Ok,
        expectedValue = Some("42"),
        expectedMessage = `ok`.r
      )
    }

    it("fails with a timeout when takes longer than the configured timeout") {
      verifyEvalResponse(
        response = serve(EvalRequest(
          code = "{ while(true) {}; 123 }",
          resolvers = sonatypeReleases
        )),
        expectedStatus = HttpStatus.Ok,
        expectedValue = None,
        expectedMessage = `Timeout Exceded`.r
      )
    }

    it("can load dependencies for an evaluation") {
      verifyEvalResponse(
        response = serve(EvalRequest(
          code = "{import cats._; Eval.now(42).value}",
          resolvers = sonatypeReleases,
          dependencies = Dependency("org.typelevel", "cats_2.11", "0.6.0") :: Nil
        )),
        expectedStatus = HttpStatus.Ok,
        expectedValue = Some("42"),
        expectedMessage = `ok`.r
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
          )),
          expectedStatus = HttpStatus.Ok,
          expectedValue = Some("42"),
          expectedMessage = `ok`.r
        )
      }

    }

    it("can run code from the exercises content") {
      verifyEvalResponse(
        response = serve(EvalRequest(
          code = "{import stdlib._; Asserts.scalaTestAsserts(true)}",
          resolvers = sonatypeReleases,
          dependencies = Dependency("org.scala-exercises", "exercises-stdlib_2.11", "0.2.0") :: Nil
        )),
        expectedStatus = HttpStatus.Ok,
        expectedValue = Some("()"),
        expectedMessage = `ok`.r
      )
    }

    it("captures exceptions when running the exercises content") {
      verifyEvalResponse(
        response = serve(EvalRequest(
          code = "{import stdlib._; Asserts.scalaTestAsserts(false)}",
          resolvers = sonatypeReleases,
          dependencies = Dependency("org.scala-exercises", "exercises-stdlib_2.11", "0.2.0") :: Nil
        )),
        expectedStatus = HttpStatus.Ok,
        expectedValue = None,
        expectedMessage = `Runtime Error`.r
      )
    }

    ignore("doesn't allow code to call System.exit") {
      verifyEvalResponse(
        response = serve(EvalRequest(
          code = "System.exit(1)",
          resolvers = sonatypeReleases
        )),
        expectedStatus = HttpStatus.Ok,
        expectedValue = None,
        expectedMessage = `Security Violation`.r
      )
    }

    it("doesn't allow to install a security manager"){
      verifyEvalResponse(
        response = serve(EvalRequest(
          code = """
import java.security._

class MaliciousSecurityManager extends SecurityManager{
  override def checkPermission(perm: Permission): Unit = {
    // allow anything to happen by not throwing a security exception
  }
}

System.setSecurityManager(new MaliciousSecurityManager())
""",
          resolvers = sonatypeReleases
        )),
        expectedStatus = HttpStatus.Ok,
        expectedValue = None,
        expectedMessage = `Security Violation`.r
      )
    }

    it("doesn't allow setting a custom policy"){
      verifyEvalResponse(
        response = serve(EvalRequest(
          code = """
import java.security._

class MaliciousPolicy extends Policy {
  override def getPermissions(domain: ProtectionDomain): PermissionCollection = {
    new AllPermission().newPermissionCollection()
  }
}

Policy.setPolicy(new MaliciousPolicy())
""",
          resolvers = sonatypeReleases
        )),
        expectedStatus = HttpStatus.Ok,
        expectedValue = None,
        expectedMessage = `Security Violation`.r
      )
    }

    it("doesn't allow executing commands"){
      verifyEvalResponse(
        response = serve(EvalRequest(
          code = """
Runtime.getRuntime.exec("ls /")
""",
          resolvers = sonatypeReleases
        )),
        expectedStatus = HttpStatus.Ok,
        expectedValue = None,
        expectedMessage = `Security Violation`.r
      )
    }

    it("doesn't allow the creation of a class loader"){
      verifyEvalResponse(
        response = serve(EvalRequest(
          code = """
val cl = new java.net.URLClassLoader(Array())
cl.loadClass("java.net.URLClassLoader")
""",
          resolvers = sonatypeReleases
        )),
        expectedStatus = HttpStatus.Ok,
        expectedValue = None,
        expectedMessage = `Security Violation`.r
      )
    }

    it("doesn't allow access to the Unsafe instance"){
      verifyEvalResponse(
        response = serve(EvalRequest(
          code = """
import sun.misc.Unsafe

Unsafe.getUnsafe
""",
          resolvers = sonatypeReleases
        )),
        expectedStatus = HttpStatus.Ok,
        expectedValue = None,
        expectedMessage = `Security Violation`.r
      )
    }

    it("doesn't allow access to the sun reflect package"){
      verifyEvalResponse(
        response = serve(EvalRequest(
          code = """
import sun.reflect.Reflection
Reflection.getCallerClass(2)
""",
          resolvers = sonatypeReleases
        )),
        expectedStatus = HttpStatus.Ok,
        expectedValue = None,
        expectedMessage = `Security Violation`.r
      )
    }
  }
}

