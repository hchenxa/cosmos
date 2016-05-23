package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.http.FinchExtensions._
import com.mesosphere.cosmos.http.{Authorization, MediaType, RequestSession}
import com.twitter.util.Future
import io.circe.syntax._
import io.circe.{Encoder, Json}
import io.finch._

import scala.reflect.ClassTag

private[cosmos] abstract class EndpointHandler[Request, Response](
  accepts: MediaType,
  protected[this] val produces: MediaType
)(implicit
  decoder: DecodeRequest[Request],
  requestClassTag: ClassTag[Request],
  encoder: Encoder[Response],
  responseClassTag: ClassTag[Response]
) {

  def apply(request: Request)(implicit session: RequestSession): Future[Response]

  private[this] type RequestInfo = (RequestSession, Request)

  private[cosmos] def route(
    routingFn: RequestReader[RequestInfo] => Endpoint[RequestInfo]
  ): Endpoint[Json] = {
    routingFn(reader) { requestInfo: RequestInfo =>
      implicit val (session, request) = requestInfo
      this(request)
        .map(res => Ok(res.asJson).withContentType(Some(produces.show)))
    }
  }

  val reader: RequestReader[RequestInfo] = for {
    accept <- header("Accept").as[MediaType].should(beTheExpectedType(produces))
    contentType <- header("Content-Type").as[MediaType].should(beTheExpectedType(accepts))
    auth <- headerOption("Authorization").as[String]
    req <- body.as[Request]
  } yield {
    RequestSession(auth.map(Authorization(_))) -> req
  }

}
