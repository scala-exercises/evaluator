package org.scalaexercises.evaluator

import org.scalatest._
import org.http4s._
import org.http4s.client.blaze._
import org.http4s.circe._
import io.circe.generic.auto._
import scalaz.concurrent.Task
import scala.concurrent.duration._

import pdi.jwt.{Jwt, JwtAlgorithm}

class Smoketests extends FunSpec with Matchers with CirceInstances {

  case class EvaluatorResponse(msg: String,
                               value: String,
                               valueType: String,
                               compilationInfos: Map[String, String])

  implicit val decoder: EntityDecoder[EvaluatorResponse] =
    jsonOf[EvaluatorResponse]

  val validToken = Jwt.encode(
    """{"user": "scala-exercises"}""",
    auth.secretKey,
    JwtAlgorithm.HS256)

  def makeRequest(code: String)(
    expectation: EvaluatorResponse => Unit,
    failExpectation: Throwable => Unit = fail(_)): Unit = {

    val request = new Request(
      method = Method.POST,
      uri = Uri.uri("http://scala-evaluator-sandbox.herokuapp.com/eval"),
      headers = Headers(headers)
    ).withBody(
      s"""{"resolvers" : [], "dependencies" : [], "code" : "$code"}""")

    val task = client.expect[EvaluatorResponse](request)

    val response = task.unsafePerformSyncAttemptFor(60.seconds)
    response.fold(failExpectation, expectation)
  }

  val headers = List(
    Header("Content-Type", "application/json").parsed,
    Header("x-scala-eval-api-token", validToken).parsed
  )

  val client = PooledHttp1Client()

  describe("Querying the /eval endpoint") {
    it("should succeed for a simple request") {
      makeRequest("1 + 1") { evaluatorResponse =>
        evaluatorResponse.value shouldBe "2"
      }
    }

    it("should continue to work after calling System.exit") {
      makeRequest("System.exit(1)")(
        expectation = _ => fail("Request should not succeed"),
        failExpectation = _ => ()
      )

      makeRequest("1 + 1") { evaluatorResponse =>
        evaluatorResponse.value shouldBe "2"
      }
    }

    it("should not expose sensitive details by calling sys.env") {
      val keywords = List("password", "key", "api")
      makeRequest("sys.env") { evaluatorResponse =>
        keywords.foreach(kw =>
            evaluatorResponse.value.contains(kw) shouldBe false)
      }

    }
  }
}
