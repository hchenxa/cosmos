package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.http.{MediaType, RequestSession}
import com.twitter.util.Future
import io.circe.syntax._
import io.circe.{Encoder, Json}
import io.finch._

import scala.reflect.ClassTag

private[cosmos] abstract class EndpointHandler[Request, Response](
  accepts: MediaType,
  protected[this] val produces: MediaType,
  readerBuilder: RequestReaderBuilder[Request] = RequestReaderBuilder.standard[Request]
)(implicit
  decoder: DecodeRequest[Request],
  requestClassTag: ClassTag[Request],
  encoder: Encoder[Response],
  responseClassTag: ClassTag[Response]
) {

  def apply(request: Request)(implicit session: RequestSession): Future[Response]

  private[cosmos] def route(
    routingFn: RequestReader[(RequestSession, Request)] => Endpoint[(RequestSession, Request)]
  ): Endpoint[Json] = {
    routingFn(reader) { requestInfo: (RequestSession, Request) =>
      implicit val (session, request) = requestInfo
      this(request)
        .map(res => Ok(res.asJson).withContentType(Some(produces.show)))
    }
  }

  private[this] val reader: RequestReader[(RequestSession, Request)] = {
    readerBuilder.build(accepts, produces)
  }

}
