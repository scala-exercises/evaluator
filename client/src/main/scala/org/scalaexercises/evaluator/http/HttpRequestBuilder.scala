package org.scalaexercises.evaluator.http

import org.scalaexercises.evaluator.http.HttpClient._

import scalaj.http.Http

case class HttpRequestBuilder(
  url: String,
  httpVerb: String,
  headers: Headers = Map.empty[String, String],
  body: Option[String] = None
) {

  def withHeaders(headers: Headers) = copy(headers = headers)

  def withBody(body: String) = copy(body = Option(body))

  def run = {
    val request = Http(url).method(httpVerb).headers(headers)

    body
      .fold(request)(
        request.postData(_).header("content-type", "application/json"))
      .asString
  }
}
