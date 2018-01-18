/*
 * scala-exercises - evaluator-client
 * Copyright (C) 2015-2016 47 Degrees, LLC. <http://www.47deg.com>
 */

package org.scalaexercises.evaluator

import fr.hmil.roshttp.response.SimpleHttpResponse
import fr.hmil.roshttp.util.HeaderMap
import io.circe.Decoder
import org.scalaexercises.evaluator.EvaluatorResponses.{EvaluationException, EvaluationResponse, EvaluationResult, JsonParsingException, UnexpectedException, toEntity}
import org.scalaexercises.evaluator.api.EvaluatorLike
import org.scalaexercises.evaluator.http.HttpClient.Headers
import org.scalaexercises.evaluator.http.HttpClientLike
import org.scalatest.{AsyncFunSpec, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class EvaluatorClientSpec extends AsyncFunSpec with Matchers {
  import EvaluatorClientSpec._

  describe("evaluator client") {
    it("return a correct 200 response if the evaluation returns without errors") {
      val client = httpClientWithValidResult(EvalResponse("ok"), 200, isError = false)

      val evaluator = new EvaluatorLike {
        override val httpClient: HttpClientLike[EvalResponse] = client
      }

      val result = evaluator.eval("", "", List(), List(), "")

      result.map { r =>
        assert(r.isRight)
        r should matchPattern {
          case Right(EvaluationResult(EvalResponse("ok", _, _, _, _), 200, _)) ⇒
        }
      }
    }

    it("return an error response if the evaluation returns without errors") {
      val client = httpClientWithValidResult(EvalResponse("error"), 400, isError = true)

      val evaluator = new EvaluatorLike {
        override val httpClient: HttpClientLike[EvalResponse] = client
      }

      val result = evaluator.eval("", "", List(), List(), "")

      result.map { r =>
        assert(r.isLeft)
        r should matchPattern {
          case Left(TestError(_, _)) ⇒
        }
      }
    }
  }

  describe("evaluator responses") {
    it("return a correct entity when receiving an valid response") {
      import org.scalaexercises.evaluator.Decoders._

      toEntity[EvalResponse](Future(new SimpleHttpResponse(200, HeaderMap(), validJsonResponse))).map { r =>
        r should matchPattern {
          case Right(EvaluationResult(_, 200, _)) ⇒
        }
      }
    }
    it("return a JsonParsingException when receiving a malformed response") {
      toEntity[String](Future(new SimpleHttpResponse(200, HeaderMap(), malformedJsonResponse))).map { r =>
        r should matchPattern {
          case Left(JsonParsingException(_, _)) ⇒
        }
      }
    }
    it("return a UnexpectedException when receiving an error response") {
      toEntity[String](Future(new SimpleHttpResponse(500, HeaderMap(), malformedJsonResponse))).map { r =>
        r should matchPattern {
          case Left(UnexpectedException(_)) ⇒
        }
      }
    }
  }

  private def httpClientWithValidResult[A](result: A, statusCode: Int, isError: Boolean) = new HttpClientLike[A] {
    override def post(url: String, secretKey: String, method: String, headers: Headers, data: String)(implicit D: Decoder[A]): Future[EvaluationResponse[A]] =
      Future {
        if (isError) {
          Left {
            new TestError("Test error")
          }
        } else {
          Right {
            EvaluationResult(result, statusCode, Map())
          }
        }
      }
  }

}

object EvaluatorClientSpec {
  
  case class TestError(msg: String, cause: Option[Throwable] = None) extends EvaluationException(msg, cause)

  val malformedJsonResponse = "error"
  val validJsonResponse =
    s"""
       |{
       |  "msg": "ok",
       |  "compilationInfos": {}
       |}
     """.stripMargin

}