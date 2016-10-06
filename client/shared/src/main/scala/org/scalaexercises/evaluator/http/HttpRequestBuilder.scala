/*
 * scala-exercises-evaluator-client
 * Copyright (C) 2015-2016 47 Degrees, LLC. <http://www.47deg.com>
 */

package org.scalaexercises.evaluator.http

import org.scalaexercises.evaluator.http.HttpClient._

import scala.concurrent.Future

import fr.hmil.roshttp.body.Implicits._
import fr.hmil.roshttp.{HttpRequest, Method, HttpResponse}
import fr.hmil.roshttp.body.JSONBody

case class HttpRequestBuilder(
  url: String,
  httpVerb: String,
  headers: Headers = Map.empty[String, String],
  body: String = ""
) {

  def withHeaders(headers: Headers) = copy(headers = headers)

  def withBody(body: String) = copy(body = body)

  def run: Future[HttpResponse] = {

    val request = HttpRequest(url)
      .withMethod(Method(httpVerb))
      .withHeaders(headers.toList: _*)
      .withHeader("content.type", "application/json")

    request.post(JSONBody(body))
  }
}
