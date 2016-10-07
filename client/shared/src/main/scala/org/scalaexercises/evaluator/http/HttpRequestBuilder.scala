/*
 * scala-exercises-evaluator-client
 * Copyright (C) 2015-2016 47 Degrees, LLC. <http://www.47deg.com>
 */

package org.scalaexercises.evaluator.http

import org.scalaexercises.evaluator.http.HttpClient._

import scala.concurrent.Future

import fr.hmil.roshttp.{HttpRequest, Method, HttpResponse}
import fr.hmil.roshttp.body.BodyPart

import java.nio.ByteBuffer

case class HttpRequestBuilder(
  url: String,
  httpVerb: String,
  headers: Headers = Map.empty[String, String],
  body: String = ""
) {

  case class CirceJSONBody(value: String) extends BodyPart {
    override def contentType: String = s"application/json; charset=utf-8"

    override def content: ByteBuffer = ByteBuffer.wrap(value.getBytes("utf-8"))
  }

  def withHeaders(headers: Headers) = copy(headers = headers)

  def withBody(body: String) = copy(body = body)

  def run: Future[HttpResponse] = {

    val request = HttpRequest(url)
      .withMethod(Method(httpVerb))
      .withHeader("content-type", "application/json")
      .withHeaders(headers.toList: _*)

    request.send(CirceJSONBody(body))
  }
}
