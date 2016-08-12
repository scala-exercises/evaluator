/*
 * scala-exercises-evaluator-client
 * Copyright (C) 2015-2016 47 Degrees, LLC. <http://www.47deg.com>
 */

package org.scalaexercises.evaluator.http

import org.scalaexercises.evaluator.http.HttpClient._

import scala.concurrent.duration.Duration
import scalaj.http.{Http, HttpOptions}

case class HttpRequestBuilder(
  url: String,
  httpVerb: String,
  connTimeout: Duration,
  readTimeout: Duration,
  followRedirects: Boolean = true,
  headers: Headers = Map.empty[String, String],
  body: Option[String] = None
) {

  def withHeaders(headers: Headers) = copy(headers = headers)

  def withBody(body: String) = copy(body = Option(body))

  def run = {
    val request = Http(url).method(httpVerb).headers(headers)

    body
      .fold(request)(
        request
          .option(HttpOptions.connTimeout(connTimeout.toMillis.toInt))
          .option(HttpOptions.readTimeout(readTimeout.toMillis.toInt))
          .postData(_)
          .header("content-type", "application/json"))
      .asString
  }
}
